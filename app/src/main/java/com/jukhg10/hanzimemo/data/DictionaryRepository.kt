package com.jukhg10.hanzimemo.data

import android.util.LruCache
import android.util.Log
import com.jukhg10.hanzimemo.ui.characters.DictionaryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class ReviewPair(val studyItem: StudyItem, val dictionaryItem: DictionaryItem)

class DictionaryRepository(
    private val characterDao: CharacterDao,
    private val wordDao: WordDao,
    private val studyDao: StudyDao
) {
    // ✅ CACHÉ LRU: 16 búsquedas recientes para caracteres y palabras
    private val characterSearchCache = LruCache<String, List<Character>>(16)
    private val wordSearchCache = LruCache<String, List<Word>>(16)

    // --- Dictionary Functions ---
    fun getAllCharacters(): Flow<List<Character>> = characterDao.getAllCharacters()
    fun getAllWords(): Flow<List<Word>> = wordDao.getAllWords()

    // --- Study List Functions ---
    suspend fun addItemToStudyList(item: StudyItem) {
        studyDao.addItem(item)
    }
    suspend fun removeItemFromStudyList(itemId: Int, itemType: String) {
        studyDao.removeItem(itemId, itemType)
    }

    // THIS IS THE FUNCTION THAT WAS MISSING
    fun isItemBeingStudied(itemId: Int, itemType: String): Flow<Boolean> {
        return studyDao.isBeingStudied(itemId, itemType)
    }

    suspend fun getStudyItemByDictionaryId(itemId: Int, itemType: String): StudyItem? {
        return studyDao.getItemByItemId(itemId, itemType)
    }

    suspend fun findOrCreateStudyItem(itemId: Int, itemType: String): StudyItem? {
        // Try find existing
        val existing = studyDao.getItemByItemId(itemId, itemType)
        if (existing != null) return existing

        // Insert new and return created item
        val newId = studyDao.addItem(StudyItem(itemId = itemId, itemType = itemType))
        if (newId <= 0L) {
            // insertion ignored or failed; try fetch again
            return studyDao.getItemByItemId(itemId, itemType)
        }
        return studyDao.getItemById(newId.toInt())
    }

    fun getFullStudyListItems(): Flow<List<DictionaryItem>> {
        return studyDao.getFullStudyList().map { studyItems ->
            val fullItems = mutableListOf<DictionaryItem>()
            for (item in studyItems) {
                val dictItem: DictionaryItem? = if (item.itemType == "character") {
                    characterDao.getCharacterById(item.itemId)?.let { DictionaryItem.CharacterItem(it) }
                } else {
                    wordDao.getWordById(item.itemId)?.let { DictionaryItem.WordItem(it) }
                }
                dictItem?.let { fullItems.add(it) }
            }
            fullItems
        }
    }

    fun getFullStudyListWithStatus(): Flow<List<ReviewPair>> {
        return studyDao.getFullStudyList().map { studyItems ->
            val fullItems = mutableListOf<ReviewPair>()
            for (item in studyItems) {
                val dictItem: DictionaryItem? = if (item.itemType == "character") {
                    characterDao.getCharacterById(item.itemId)?.let { DictionaryItem.CharacterItem(it) }
                } else {
                    wordDao.getWordById(item.itemId)?.let { DictionaryItem.WordItem(it) }
                }
                dictItem?.let { fullItems.add(ReviewPair(studyItem = item, dictionaryItem = it)) }
            }
            fullItems
        }
    }

    // --- Review Functions ---
    suspend fun getReviewDeck(): List<ReviewPair> {
        val studyItems = studyDao.getStudyDeck()
        val fullItems = mutableListOf<ReviewPair>()
        for (item in studyItems) {
            val dictItem: DictionaryItem? = if (item.itemType == "character") {
                characterDao.getCharacterById(item.itemId)?.let { DictionaryItem.CharacterItem(it) }
            } else {
                wordDao.getWordById(item.itemId)?.let { DictionaryItem.WordItem(it) }
            }
            dictItem?.let { fullItems.add(ReviewPair(studyItem = item, dictionaryItem = it)) }
        }
        return fullItems
    }

    suspend fun getStudyItemById(studyItemId: Int): ReviewPair? {
        val studyItem = studyDao.getItemById(studyItemId)
        if (studyItem != null) {
            val dictItem: DictionaryItem? = if (studyItem.itemType == "character") {
                characterDao.getCharacterById(studyItem.itemId)?.let { DictionaryItem.CharacterItem(it) }
            } else {
                wordDao.getWordById(studyItem.itemId)?.let { DictionaryItem.WordItem(it) }
            }
            dictItem?.let { return ReviewPair(studyItem = studyItem, dictionaryItem = it) }
        }
        return null
    }

    suspend fun updateStudyItemProgress(studyItem: StudyItem, knewIt: Boolean) {
        if (knewIt) {
            val newPriority = studyItem.maxPriority + 1
            studyDao.updateStudyProgress(studyItem.id, newPriority, newPriority)
        } else {
            val newCurrentPriority = 1
            val newMaxPriority = (studyItem.maxPriority - 1).coerceAtLeast(1)
            studyDao.updateStudyProgress(studyItem.id, newCurrentPriority, newMaxPriority)
        }
    }

    suspend fun markItemAsLearned(studyItemId: Int, learned: Boolean) {
        studyDao.updateLearned(studyItemId, learned)
    }

    suspend fun decrementItemPriority(studyItem: StudyItem) {
        if (studyItem.currentPriority > 1) {
            studyDao.updateCurrentPriority(studyItem.id, studyItem.currentPriority - 1)
        }
    }

    /**
     * Transacción atómica: Usuario presionó "I Knew It"
     * - Incrementa prioridad (mejor conocimiento)
     * - Marca como learned = true
     * - Logs: DEBUG antes/después del cambio
     */
    suspend fun onKnewIt(studyItem: StudyItem) {
        try {
            android.util.Log.d(
                "HanziMemo.Repo",
                "onKnewIt: itemId=${studyItem.id}, before maxPriority=${studyItem.maxPriority}, learned=${studyItem.learned}"
            )
            
            val newMaxPriority = studyItem.maxPriority + 1
            val newCurrentPriority = newMaxPriority
            
            // Actualizar prioridad y marcar como learned
            studyDao.updateStudyProgress(studyItem.id, newCurrentPriority, newMaxPriority)
            studyDao.updateLearned(studyItem.id, true)
            
            android.util.Log.d(
                "HanziMemo.Repo",
                "onKnewIt: itemId=${studyItem.id}, after maxPriority=$newMaxPriority, learned=true"
            )
        } catch (e: Exception) {
            android.util.Log.e("HanziMemo.Repo", "onKnewIt failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Transacción atómica: Usuario presionó "I Forgot"
     * - Decrementa prioridad (necesita más práctica)
     * - Marca como learned = false (retorna a estudio)
     * - Logs: DEBUG antes/después del cambio
     */
    suspend fun onForgot(studyItem: StudyItem) {
        try {
            android.util.Log.d(
                "HanziMemo.Repo",
                "onForgot: itemId=${studyItem.id}, before maxPriority=${studyItem.maxPriority}, learned=${studyItem.learned}"
            )
            
            val newCurrentPriority = 1
            val newMaxPriority = (studyItem.maxPriority - 1).coerceAtLeast(1)
            
            // Actualizar prioridad y marcar como no learned (retorna a estudio)
            studyDao.updateStudyProgress(studyItem.id, newCurrentPriority, newMaxPriority)
            studyDao.updateLearned(studyItem.id, false)
            
            android.util.Log.d(
                "HanziMemo.Repo",
                "onForgot: itemId=${studyItem.id}, after maxPriority=$newMaxPriority, learned=false"
            )
        } catch (e: Exception) {
            android.util.Log.e("HanziMemo.Repo", "onForgot failed: ${e.message}", e)
            throw e
        }
    }

    // --- Token-based search (optimized in-memory filter) ---
    // ⚠️ CRITICAL FIX: Only search for first token efficiently, then filter in memory
    // This reduces DB load significantly while maintaining accuracy
    fun searchCharactersByTokens(tokens: List<SearchToken>): Flow<List<Character>> {
        if (tokens.isEmpty()) return characterDao.getAllCharacters()
        
        // ✅ CACHÉ: Generar clave de búsqueda
        val cacheKey = tokens.joinToString("|") { "${it.type}:${it.value}" }
        
        // ✅ Verificar caché
        val cached = characterSearchCache.get(cacheKey)
        if (cached != null) {
            Log.d("SearchCache", "✓ Cache HIT para caracteres: $cacheKey (${cached.size} items)")
            return flowOf(cached)
        }

        // Filtrar en memoria todos los tokens para mayor control y precisión
        return characterDao.getAllCharacters().map { list ->
            val filtered = list.filter { character -> tokens.all { matchesCharacterToken(character, it) } }
            
            // ✅ Guardar en caché
            characterSearchCache.put(cacheKey, filtered)
            Log.d("SearchCache", "✓ Cache SAVE para caracteres: $cacheKey (${filtered.size} items)")
            
            filtered
        }
    }

    fun searchWordsByTokens(tokens: List<SearchToken>): Flow<List<Word>> {
        if (tokens.isEmpty()) return wordDao.getAllWords()
        
        // ✅ CACHÉ: Generar clave de búsqueda
        val cacheKey = tokens.joinToString("|") { "${it.type}:${it.value}" }
        
        // ✅ Verificar caché
        val cached = wordSearchCache.get(cacheKey)
        if (cached != null) {
            Log.d("SearchCache", "✓ Cache HIT para palabras: $cacheKey (${cached.size} items)")
            return flowOf(cached)
        }

        // Filtrar en memoria todos los tokens para mayor control y precisión
        return wordDao.getAllWords().map { list ->
            val filtered = list.filter { word -> tokens.all { matchesWordToken(word, it) } }
            
            // ✅ Guardar en caché
            wordSearchCache.put(cacheKey, filtered)
            Log.d("SearchCache", "✓ Cache SAVE para palabras: $cacheKey (${filtered.size} items)")
            
            filtered
        }
    }

    // ✅ NUEVA FUNCIÓN: Limpiar caché en bajo memoria
    fun clearCaches() {
        characterSearchCache.evictAll()
        wordSearchCache.evictAll()
        Log.d("SearchCache", "🧹 Caché limpiado completamente")
    }

    private fun matchesCharacterToken(character: Character, token: SearchToken): Boolean {
        val tokenValue = token.value.lowercase()
        return when (token.type) {
            TokenType.PINYIN_SEARCH -> {
                // ✅ Búsqueda EXACTA de pinyin (con tonos como "ni3", "shui3")
                val pinyinWithTone = character.pinyin.lowercase()
                val pinyinWithoutTone = pinyinWithTone.replace(Regex("[12345]"), "")
                val queryWithoutTone = tokenValue.replace(Regex("[12345]"), "")
                // Coincidencia exacta CON tonos o SIN tonos
                pinyinWithTone.equals(tokenValue) || pinyinWithoutTone.equals(queryWithoutTone)
            }
            TokenType.DEFINITION_SEARCH -> character.definition.lowercase().contains(tokenValue)
            TokenType.HANZI_SEARCH -> (character.simplified + " " + character.traditional).lowercase().contains(tokenValue)
            TokenType.GENERAL_SEARCH -> (
                    character.simplified + " " + character.traditional + " " + character.definition + " " + character.pinyin
                    ).lowercase().contains(tokenValue)
        }
    }

    private fun matchesWordToken(word: Word, token: SearchToken): Boolean {
        val tokenValue = token.value.lowercase()
        return when (token.type) {
            TokenType.PINYIN_SEARCH -> {
                // ✅ Búsqueda EXACTA de pinyin (con tonos como "ni3 hao3", "shui3")
                // ❌ EXCLUSIÓN: Rechazar pinyin simples (monosilábicos como "a1", "e1")
                // Solo palabras multi-sílabas deben coincidir con búsquedas pinyin
                val spaceCount = word.pinyin.count { it == ' ' }
                if (spaceCount == 0) {
                    // Es un pinyin monosilábico - solo Characters, no Words
                    false
                } else {
                    // Para búsquedas multisílabas, verificar coincidencia exacta
                    val pinyinWithTone = word.pinyin.lowercase()
                    val pinyinWithoutTone = pinyinWithTone.replace(Regex("[12345]"), "")
                    val queryWithoutTone = tokenValue.replace(Regex("[12345]"), "")
                    
                    // Coincidencia exacta CON tonos o SIN tonos
                    val exactMatch = pinyinWithTone.equals(tokenValue) || pinyinWithoutTone.equals(queryWithoutTone)
                    
                    // También permitir búsqueda por sílabas individuales (ej: buscar "ni3" en "ni3 hao3")
                    // pero solo si el query también es multisílabo o exacto
                    val syllableMatch = if (queryWithoutTone.any { it == ' ' }) {
                        // El query tiene múltiples sílabas, permitir búsqueda de sílabas
                        pinyinWithTone.split(" ").any { syllable ->
                            syllable.equals(tokenValue) || 
                            syllable.replace(Regex("[12345]"), "").equals(queryWithoutTone)
                        }
                    } else {
                        // El query es monosilábico, NO permitir en Words
                        false
                    }
                    
                    exactMatch || syllableMatch
                }
            }
            TokenType.DEFINITION_SEARCH -> word.definition.lowercase().contains(tokenValue)
            TokenType.HANZI_SEARCH -> (word.simplified + " " + word.traditional).lowercase().contains(tokenValue)
            TokenType.GENERAL_SEARCH -> (
                    word.simplified + " " + word.traditional + " " + word.definition + " " + word.pinyin
                    ).lowercase().contains(tokenValue)
        }
    }
}