package com.jukhg10.hanzimemo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume

/**
 * Gestor de reconocimiento offline de Hanzi usando ML Kit
 * 
 * Pipeline:
 * 1. Captura imagen de cámara o galería (width x height <= 2048px)
 * 2. Carga en bitmap normalizado
 * 3. Realiza OCR offline con ML Kit (ChineseTextRecognizerOptions)
 * 4. Busca caracteres reconocidos en base de datos SQLite
 * 5. Retorna coincidencias potenciales enriquecidas con pinyin, definiciones, etc.
 * 
 * Rendimiento objetivo:
 * - Carga de imagen: <500ms
 * - OCR ML Kit: 1-2s
 * - Búsqueda DB: <200ms
 * - Total: ~2-3s
 * 
 * Notas:
 * - Ejecutar en Dispatcher.IO para no bloquear UI
 * - Timeouts recomendados: 5s para operación completa
 * - Manejo de errores y fallback: mostrar mensaje claro, permitir reintentar
 */

const val TAG = "HanziMemo.OCR"
const val RECOGNITION_TIMEOUT_MS = 5000L
const val MAX_IMAGE_SIZE = 2048

data class RecognitionResult(
    val recognizedText: String,
    val confidence: Float,
    val boundingBox: List<Pair<Int, Int>> = emptyList()
)

data class HanziMatch(
    val hanzi: String,
    val matchedCharacter: Character?,
    val matchedWord: Word?,
    val confidence: Float,
    val isInStudyList: Boolean
)

/**
 * Servicio de reconocimiento de Hanzi offline
 * Utiliza ML Kit con modelos locales (no requiere conexión a internet)
 */
class HanziRecognitionService(private val context: Context, private val repository: DictionaryRepository) {
    
    private var recognizerInstance: com.google.mlkit.vision.text.TextRecognizer? = null
    
    init {
        // Pre-inicializar reconocedor para evitar latencia en primera llamada
        initializeRecognizer()
    }
    
    private fun initializeRecognizer() {
        try {
            if (recognizerInstance == null) {
                recognizerInstance = TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build()
                )
                Log.d(TAG, "Reconocedor chino inicializado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando reconocedor: ${e.message}", e)
        }
    }
    
    /**
     * Reconoce Hanzi de una imagen (archivo local)
     * Con timeout y cancellación para evitar bloqueos indefinidos.
     * 
     * @param imagePath Ruta al archivo de imagen
     * @return Lista de resultados de reconocimiento ordenados por confianza
     * @throws TimeoutCancellationException si OCR excede RECOGNITION_TIMEOUT_MS
     */
    suspend fun recognizeHanziFromImage(imagePath: String): List<RecognitionResult> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "recognizeHanziFromImage: imagePath=$imagePath")
            val startTime = System.currentTimeMillis()
            try {
                // Cargar y normalizar bitmap
                val bitmap = loadBitmap(imagePath) ?: run {
                    Log.w(TAG, "No se pudo cargar bitmap desde $imagePath")
                    return@withContext emptyList()
                }
                Log.d(TAG, "Bitmap cargado: ${bitmap.width}x${bitmap.height}")
                
                // OCR con ML Kit + timeout
                val results = try {
                    withTimeout(RECOGNITION_TIMEOUT_MS) {
                        recognizeTextInBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OCR timeout o error en recognizeHanziFromImage: ${e.message}")
                    emptyList()
                }
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "recognizeHanziFromImage completado en ${totalTime}ms, encontrados ${results.size} resultados")
                results
            } catch (e: Exception) {
                Log.e(TAG, "Error en recognizeHanziFromImage: ${e.message}", e)
                emptyList()
            }
        }
    
    /**
     * Reconoce Hanzi de un Uri (cámara, galería)
     * Con timeout y cancellación para evitar bloqueos indefinidos.
     */
    suspend fun recognizeHanziFromUri(imageUri: Uri): List<RecognitionResult> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "recognizeHanziFromUri: imageUri=$imageUri")
            val startTime = System.currentTimeMillis()
            try {
                val bitmap = loadBitmapFromUri(imageUri) ?: run {
                    Log.w(TAG, "No se pudo cargar bitmap desde $imageUri")
                    return@withContext emptyList()
                }
                Log.d(TAG, "Bitmap cargado: ${bitmap.width}x${bitmap.height}")
                
                val results = try {
                    withTimeout(RECOGNITION_TIMEOUT_MS) {
                        recognizeTextInBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OCR timeout o error en recognizeHanziFromUri: ${e.message}")
                    emptyList()
                }
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "recognizeHanziFromUri completado en ${totalTime}ms, encontrados ${results.size} resultados")
                results
            } catch (e: Exception) {
                Log.e(TAG, "Error en recognizeHanziFromUri: ${e.message}", e)
                emptyList()
            }
        }
    
    /**
     * Pipeline de OCR con ML Kit
     * - Entrada: bitmap de imagen
     * - Salida: lista de RecognitionResult ordenados por confianza descendente
     * - Logs: DEBUG con progreso y tiempos
     * - Manejo: suspendCancellableCoroutine para permitir cancellación
     */
    private suspend fun recognizeTextInBitmap(bitmap: Bitmap): List<RecognitionResult> {
        return withContext(Dispatchers.Default) {
            try {
                val recognizer = recognizerInstance ?: TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build()
                )
                
                Log.d(TAG, "Iniciando OCR ML Kit en bitmap ${bitmap.width}x${bitmap.height}")
                val startTime = System.currentTimeMillis()
                
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                // Usar suspendCancellableCoroutine para manejo de cancellación
                val visionText = suspendCancellableCoroutine { continuation ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { result ->
                            Log.d(TAG, "ML Kit process completó exitosamente")
                            continuation.resume(result)
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "ML Kit process falló: ${exception.message}", exception)
                            continuation.resumeWithException(exception)
                        }
                        .addOnCanceledListener {
                            Log.d(TAG, "ML Kit process fue cancelado")
                            continuation.cancel()
                        }
                }
                
                val results = mutableListOf<RecognitionResult>()
                
                // Procesar bloques de texto reconocidos
                var blockCount = 0
                var lineCount = 0
                var elementCount = 0
                
                for (block in visionText.textBlocks) {
                    blockCount++
                    for (line in block.lines) {
                        lineCount++
                        for (element in line.elements) {
                            elementCount++
                            val text = element.text.trim()
                            if (text.isNotEmpty()) {
                                val confidence = (line.confidence * 100).toInt() / 100f
                                results.add(
                                    RecognitionResult(
                                        recognizedText = text,
                                        confidence = confidence.coerceIn(0f, 1f),
                                        boundingBox = emptyList()
                                    )
                                )
                            }
                        }
                    }
                }
                
                val elapsedTime = System.currentTimeMillis() - startTime
                Log.d(
                    TAG,
                    "OCR completado: $blockCount bloques, $lineCount líneas, $elementCount elementos, " +
                            "${results.size} reconocidos, tiempo=${elapsedTime}ms"
                )
                
                // Ordenar por confianza descendente
                results.sortByDescending { it.confidence }
                
                results
            } catch (e: Exception) {
                Log.e(TAG, "Error en recognizeTextInBitmap: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    /**
     * Compara los Hanzi reconocidos con la base de datos de forma eficiente
     * 
     * Optimizaciones:
     * - Fetch todas las entradas de DB una sola vez (con timeout)
     * - Búsqueda en memoria con Map para O(1) lookup
     * - Retorna lista enriquecida con pinyin, definición, estado de estudio
     * - SIN .collect() bloqueante - usa firstOrNull() para no congelar UI
     */
    suspend fun matchRecognizedHanziWithDatabase(recognizedTexts: List<String>): List<HanziMatch> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "matchRecognizedHanziWithDatabase: reconocidos=${recognizedTexts.size} textos")
            val startTime = System.currentTimeMillis()
            
            try {
                val matches = mutableListOf<HanziMatch>()
                val characterMap = mutableMapOf<String, Character>()
                val wordMap = mutableMapOf<String, Word>()
                
                // Fetch DB data usando firstOrNull() para NO bloquear
                val dbFetchTime = measureTimeMillis {
                    try {
                        val characters = repository.getAllCharacters().firstOrNull() ?: emptyList()
                        Log.d(TAG, "Cargados ${characters.size} caracteres")
                        characters.forEach { char ->
                            characterMap[char.simplified] = char
                            characterMap[char.traditional] = char
                        }
                        
                        val words = repository.getAllWords().firstOrNull() ?: emptyList()
                        Log.d(TAG, "Cargadas ${words.size} palabras")
                        words.forEach { word ->
                            wordMap[word.simplified] = word
                            wordMap[word.traditional] = word
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching DB: ${e.message}", e)
                    }
                }
                Log.d(TAG, "DB fetch completado en ${dbFetchTime}ms: ${characterMap.size} chars, ${wordMap.size} words")
                
                // Procesar textos reconocidos - SIN loops anidados innecesarios
                val matchTime = measureTimeMillis {
                    val seenHanzi = mutableSetOf<String>()
                    
                    for (text in recognizedTexts) {
                        // Procesar texto completo primero
                        if (text.isNotEmpty() && !seenHanzi.contains(text)) {
                            seenHanzi.add(text)
                            
                            val character = characterMap[text]
                            val word = wordMap[text]
                            
                            if (character != null || word != null) {
                                val isStudying = character?.let { 
                                    checkIsBeingStudied(it.id, "character")
                                } ?: false
                                
                                matches.add(
                                    HanziMatch(
                                        hanzi = text,
                                        matchedCharacter = character,
                                        matchedWord = word,
                                        confidence = 0.90f,
                                        isInStudyList = isStudying
                                    )
                                )
                                Log.d(TAG, "Match encontrado: $text")
                            }
                        }
                    }
                }
                Log.d(TAG, "Matching completado en ${matchTime}ms: ${matches.size} matches")
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "matchRecognizedHanziWithDatabase completado: ${matches.size} en ${totalTime}ms")
                
                matches
            } catch (e: Exception) {
                Log.e(TAG, "Error en matchRecognizedHanziWithDatabase: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    /**
     * Verifica si un carácter está siendo estudiado (optimizado: one-shot, no collector bloqueante)
     */
    private suspend fun checkIsBeingStudied(characterId: Int, itemType: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Usar firstOrNull() para obtener el primer resultado sin bloquear
                val isBeingStudied = repository.isItemBeingStudied(characterId, itemType).firstOrNull() ?: false
                Log.d(TAG, "checkIsBeingStudied: characterId=$characterId, itemType=$itemType, result=$isBeingStudied")
                isBeingStudied
            } catch (e: Exception) {
                Log.d(TAG, "Error verificando estudio: ${e.message}")
                false
            }
        }
    }
    
    private fun loadBitmap(imagePath: String): Bitmap? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "Archivo de imagen no existe: $imagePath")
                return null
            }
            
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, Uri.fromFile(file))
            }
            
            // Normalizar tamaño si es muy grande
            if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
                val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Log.d(TAG, "Redimensionando bitmap: ${bitmap.width}x${bitmap.height} → ${newWidth}x${newHeight}")
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando bitmap: ${e.message}", e)
            null
        }
    }
    
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            
            // Normalizar tamaño si es muy grande
            if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
                val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Log.d(TAG, "Redimensionando bitmap: ${bitmap.width}x${bitmap.height} → ${newWidth}x${newHeight}")
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando bitmap desde URI: ${e.message}", e)
            null
        }
    }
    
    private fun isHanzi(char: Char): Boolean {
        val code = char.code
        return code in 0x4E00..0x9FFF  // Rango Unicode para CJK Unified Ideographs
    }
}
