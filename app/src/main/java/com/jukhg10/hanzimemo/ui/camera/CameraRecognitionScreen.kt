package com.jukhg10.hanzimemo.ui.camera

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.jukhg10.hanzimemo.BuildConfig
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.data.HanziMatch
import com.jukhg10.hanzimemo.data.HanziRecognitionService
import com.jukhg10.hanzimemo.ui.Screen
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// --- ESTADO Y VIEWMODEL ---

data class CameraRecognitionUiState(
    val isLoading: Boolean = false,
    val matches: List<HanziMatch> = emptyList(),
    val error: String? = null,
    val lastRecognizedText: String = "",
    val hasProcessedImage: Boolean = false  // Flag para distinguir estado inicial de "sin detección"
)

class CameraRecognitionViewModel(
    private val repository: DictionaryRepository,
    private val recognitionService: HanziRecognitionService
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraRecognitionUiState())
    val uiState: StateFlow<CameraRecognitionUiState> = _uiState.asStateFlow()

    fun recognizeFromImageUri(imageUri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val results = recognitionService.recognizeHanziFromImage(imageUri)
                val recognizedText = results.map { it.recognizedText }.joinToString("")
                val matches = recognitionService.matchRecognizedHanziWithDatabase(
                    results.map { it.recognizedText }
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    matches = matches,
                    lastRecognizedText = recognizedText,
                    hasProcessedImage = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al reconocer: ${e.message}"
                )
            }
        }
    }

    fun recognizeFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val results = recognitionService.recognizeHanziFromUri(uri)
                val recognizedText = results.map { it.recognizedText }.joinToString("")
                val matches = recognitionService.matchRecognizedHanziWithDatabase(
                    results.map { it.recognizedText }
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    matches = matches,
                    lastRecognizedText = recognizedText,
                    hasProcessedImage = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message ?: "Desconocido"}"
                )
            }
        }
    }

    fun addMatchToStudyList(match: HanziMatch) {
        viewModelScope.launch {
            val id = match.matchedCharacter?.id ?: match.matchedWord?.id
            val type = if (match.matchedCharacter != null) "character" else "word"
            
            if (id != null) {
                repository.addItemToStudyList(
                    com.jukhg10.hanzimemo.data.StudyItem(
                        itemId = id,
                        itemType = type
                    )
                )
                // Optimistic UI update
                _uiState.value = _uiState.value.copy(
                    matches = _uiState.value.matches.map {
                        if (it.hanzi == match.hanzi) it.copy(isInStudyList = true) else it
                    }
                )
            }
        }
    }

    fun removeMatchFromStudyList(match: HanziMatch) {
        viewModelScope.launch {
            val id = match.matchedCharacter?.id ?: match.matchedWord?.id
            val type = if (match.matchedCharacter != null) "character" else "word"
            if (id != null) {
                repository.removeItemFromStudyList(id, type)
                _uiState.value = _uiState.value.copy(
                    matches = _uiState.value.matches.map {
                        if (it.hanzi == match.hanzi) it.copy(isInStudyList = false) else it
                    }
                )
            }
        }
    }

    fun clearMatches() {
        _uiState.value = CameraRecognitionUiState()
    }

    fun setError(message: String?) {
        _uiState.value = _uiState.value.copy(error = message)
    }
}

class CameraRecognitionViewModelFactory(
    private val repository: DictionaryRepository,
    private val recognitionService: HanziRecognitionService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraRecognitionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraRecognitionViewModel(repository, recognitionService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- PANTALLA PRINCIPAL ---

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraRecognitionScreen(
    viewModel: CameraRecognitionViewModel,
    navController: NavController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Usar rememberSaveable para que la URI sobreviva si el proceso se mata
    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    
    // ✅ Sincronizar vista cuando se regresa a Camera desde Flashcard
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Permitir que el estado se mantenga para que el usuario vea los resultados
                    // pero limpiar error si lo hay
                    viewModel.setError(null)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Limpiar estado cuando se sale de la pantalla para evitar que
                    // los mensajes se muestren innecesariamente en futuras visitas
                    viewModel.clearMatches()
                    viewModel.setError(null)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 1. Launcher para el CROP (Recorte)
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
            if (resultUri != null) {
                viewModel.recognizeFromUri(resultUri)
            } else {
                viewModel.setError("Error al obtener imagen recortada.")
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(result.data ?: return@rememberLauncherForActivityResult)
            viewModel.setError("Error uCrop: ${error?.message}")
        }
    }

    // Función auxiliar para iniciar uCrop
    fun launchCrop(sourceUri: Uri) {
        try {
            val destFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            val destUri = Uri.fromFile(destFile)

            // Configuración de uCrop
            val uCropIntent = UCrop.of(sourceUri, destUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1024, 1024)
                .getIntent(context)
            
            cropLauncher.launch(uCropIntent)
        } catch (e: Exception) {
            viewModel.setError("No se pudo iniciar el recorte: ${e.message}")
        }
    }

    // 2. Launcher para la CÁMARA
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            // Si la foto se tomó correctamente, lanzamos el recorte
            launchCrop(tempPhotoUri!!)
        } else if (!success) {
            // Usuario canceló
        } else {
            viewModel.setError("Error al capturar foto.")
        }
    }

    // 3. Launcher para la GALERÍA
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setError(null)
            // IMPORTANTE: Mover operación de archivo a IO thread para evitar ANR/Crash
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "gallery_temp_${System.currentTimeMillis()}.jpg")
                        
                        tempFile.outputStream().use { out ->
                            inputStream.copyTo(out)
                        }
                        inputStream.close()
                        
                        // Volver al Main thread para lanzar la UI de recorte
                        val tempUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            tempFile
                        )
                        
                        withContext(Dispatchers.Main) {
                            launchCrop(tempUri)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            viewModel.setError("No se pudo leer la imagen.")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        viewModel.setError("Error procesando galería: ${e.message}")
                    }
                }
            }
        }
    }

    // Mostrar Snackbar cuando hay error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Long,
                actionLabel = "Cerrar"
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Show permission denied message
            if (cameraPermissionState.status != PermissionStatus.Granted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Se necesitan permisos de cámara",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Por favor, permite el acceso a la cámara en la configuración de la aplicación para usar la función de reconocimiento de caracteres.",
                            fontSize = 14.sp,
                            color = Color(0xFFD32F2F),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Permitir acceso")
                        }
                    }
                }
            }
            
            Text(
                text = "Reconocer Hanzi",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Botones de captura
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (cameraPermissionState.status == PermissionStatus.Granted) {
                            try {
                                val photoFile = File.createTempFile("photo_hanzi_", ".jpg", context.cacheDir)
                                // Usar BuildConfig.APPLICATION_ID para mayor seguridad
                                val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
                                
                                val photoUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    authority,
                                    photoFile
                                )
                                tempPhotoUri = photoUri // Guardar para usar tras el callback
                                cameraLauncher.launch(photoUri)
                            } catch (e: Exception) {
                                viewModel.clearMatches()
                                viewModel.setError("No se pudo crear archivo para la cámara: ${e.message}")
                            }
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Cámara")
                }

                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Galería")
                }
            }

            // Estado de carga
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 32.dp))
                Text("Reconociendo Hanzi...", fontSize = 14.sp, color = Color.Gray)
            }

            // Mostrar texto reconocido
            if (uiState.lastRecognizedText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Texto reconocido:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Text(
                            text = uiState.lastRecognizedText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Lista de coincidencias
            if (uiState.matches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Coincidencias (${uiState.matches.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { viewModel.clearMatches() },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Limpiar", fontSize = 12.sp)
                    }
                }
                
                LazyColumn(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.matches) { match ->
                        HanziMatchCard(
                            match = match,
                            onAddToStudy = {
                                if (match.isInStudyList) viewModel.removeMatchFromStudyList(match) else viewModel.addMatchToStudyList(match)
                            },
                            onOpenFlashcard = {
                                val id = match.matchedCharacter?.id ?: match.matchedWord?.id
                                val type = if (match.matchedCharacter != null) "character" else "word"
                                if (id != null) {
                                    navController?.navigate(Screen.FlashcardWithId.createRoute(type, id))
                                }
                            }
                        )
                    }
                }
            } else if (!uiState.isLoading && uiState.lastRecognizedText.isNotEmpty() && uiState.error == null) {
                // ✅ Mostrar mensaje cuando se detectó texto pero no hay coincidencias en BD
                // Esto significa que el OCR encontró caracteres, pero no están en la base de datos
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "⚠️ Carácter no encontrado en BD",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC62828),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Se reconoció el carácter '${uiState.lastRecognizedText}' pero no existe en la base de datos.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearMatches() }) {
                            Text("Intentar de nuevo")
                        }
                    }
                }
            } else if (!uiState.isLoading && uiState.lastRecognizedText.isEmpty() && uiState.error == null && uiState.hasProcessedImage) {
                // ✅ Mostrar mensaje cuando NO se detectó NINGÚN texto (OCR falló silenciosamente)
                // Esto sucede cuando la imagen es de muy mala calidad o no contiene caracteres chinos
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "📷 No se detectaron caracteres",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No se pudo reconocer ningún carácter en la imagen. Intenta con:\n\n• Mejor iluminación\n• Una imagen más clara\n• Acerca más el carácter\n• Enfoca directamente al carácter",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearMatches() }) {
                            Text("Intentar de nuevo")
                        }
                    }
                }
            } else if (!uiState.isLoading && uiState.lastRecognizedText.isEmpty() && uiState.error == null && !uiState.hasProcessedImage) {
                // Mostrar mensaje inicial solo si NO se ha procesado ninguna imagen aún
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Selecciona una foto para comenzar",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HanziMatchCard(
    match: HanziMatch,
    onAddToStudy: () -> Unit,
    onOpenFlashcard: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { 
                // Solo navega si el match tiene un ID válido
                if (match.matchedCharacter?.id != null || match.matchedWord?.id != null) {
                    onOpenFlashcard()
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.hanzi,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                when {
                    match.matchedCharacter != null -> {
                        val char = match.matchedCharacter
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Pinyin: ${char.pinyin}", fontSize = 11.sp)
                        Text(text = "${char.definition}", fontSize = 10.sp, maxLines = 1)
                    }
                    match.matchedWord != null -> {
                        val word = match.matchedWord
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Pinyin: ${word.pinyin}", fontSize = 11.sp)
                        Text(text = "${word.definition}", fontSize = 10.sp, maxLines = 1)
                    }
                    else -> {
                        // ✅ Mensaje cuando el carácter no está en BD
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ No encontrado en la base de datos",
                            fontSize = 11.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Este carácter no existe en nuestro diccionario.",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                Text(
                    text = "Confianza: ${(match.confidence * 100).toInt()}%",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ✅ Deshabilitar botón si no hay coincidencia en BD
            val hasMatch = match.matchedCharacter != null || match.matchedWord != null
            IconButton(
                onClick = onAddToStudy,
                modifier = Modifier.size(40.dp),
                enabled = hasMatch
            ) {
                Icon(
                    imageVector = if (match.isInStudyList) 
                        Icons.Default.CheckCircle else 
                        Icons.Default.AddCircleOutline,
                    contentDescription = if (hasMatch) "Agregar a estudio" else "No disponible",
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        match.isInStudyList -> MaterialTheme.colorScheme.primary
                        hasMatch -> Color.Gray
                        else -> Color.LightGray
                    }
                )
            }
        }
    }
}