package com.jukhg10.hanzimemo.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jukhg10.hanzimemo.data.Character
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.data.PinyinParser
import com.jukhg10.hanzimemo.data.SearchLexer
import com.jukhg10.hanzimemo.data.SearchToken
import com.jukhg10.hanzimemo.data.TokenType
import com.jukhg10.hanzimemo.data.StudyItem
import com.jukhg10.hanzimemo.data.Word
import com.jukhg10.hanzimemo.lexer.tokenizeQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Sealed class and Enum remain the same
sealed class DictionaryItem {
    data class CharacterItem(val character: Character) : DictionaryItem()
    data class WordItem(val word: Word) : DictionaryItem()
}
enum class DictionaryMode { CHARACTERS, WORDS }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DictionaryViewModel(private val repository: DictionaryRepository) : ViewModel() {
    private val _mode = MutableStateFlow(DictionaryMode.CHARACTERS)
    val mode: StateFlow<DictionaryMode> = _mode.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ✅ DEBOUNCE: Esperar 300ms sin cambios antes de procesar búsqueda
    private val debouncedSearchQuery: Flow<String> = _searchQuery
        .debounce(300)  // Esperar 300ms sin cambios
        .distinctUntilChanged()

    // Validación de pinyin en tiempo real
    val pinyinTokensValid: StateFlow<Boolean> = _searchQuery
        .map { query ->
            val tokens = SearchLexer.lex(query)
            val pinyinTokens = tokens.filter { it.type == TokenType.PINYIN_SEARCH }
            if (pinyinTokens.isEmpty()) true
            else pinyinTokens.all { PinyinParser.validateToken(it.value) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ✅ Búsqueda eficiente: guarda tokens del query actual
    private val _currentTokens = MutableStateFlow<List<SearchToken>>(emptyList())
    
    // ✅ Resultados RAW de caracteres y palabras (sin mapear)
    private val charactersFlow: Flow<List<Character>> = _currentTokens.flatMapLatest { tokens ->
        if (tokens.isEmpty() && _searchQuery.value.isBlank()) {
            repository.getAllCharacters()
        } else if (tokens.isNotEmpty()) {
            repository.searchCharactersByTokens(tokens)
        } else {
            flowOf(emptyList())
        }
    }

    private val wordsFlow: Flow<List<Word>> = _currentTokens.flatMapLatest { tokens ->
        if (tokens.isEmpty() && _searchQuery.value.isBlank()) {
            repository.getAllWords()
        } else if (tokens.isNotEmpty()) {
            repository.searchWordsByTokens(tokens)
        } else {
            flowOf(emptyList())
        }
    }

    // ✅ Combinación eficiente: solo mapea cuando hay cambios
    private val allSearchResults: StateFlow<Pair<List<DictionaryItem.CharacterItem>, List<DictionaryItem.WordItem>>> =
        combine(charactersFlow, wordsFlow) { characters, words ->
            characters.map { DictionaryItem.CharacterItem(it) } to 
            words.map { DictionaryItem.WordItem(it) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<DictionaryItem.CharacterItem>() to emptyList<DictionaryItem.WordItem>())

    // ✅ Auto-select tab si solo una tiene resultados
    private val autoMode: StateFlow<DictionaryMode?> = allSearchResults.map { (characters, words) ->
        when {
            // Solo caracteres tienen resultados
            characters.isNotEmpty() && words.isEmpty() -> DictionaryMode.CHARACTERS
            // Solo palabras tienen resultados
            characters.isEmpty() && words.isNotEmpty() -> DictionaryMode.WORDS
            // Ambas o ninguna tienen resultados: no cambiar automáticamente
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ✅ Items filtrados: respeta autoMode si está establecido, sino usa _mode
    val items: StateFlow<List<DictionaryItem>> =
        combine(autoMode, allSearchResults, _mode) { auto, (characters, words), currentMode ->
            // Si hay autoMode (solo uno tiene resultados), usarlo; si no, usar modo actual
            val effectiveMode = auto ?: currentMode
            when (effectiveMode) {
                DictionaryMode.CHARACTERS -> characters
                DictionaryMode.WORDS -> words
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMode(newMode: DictionaryMode) { _mode.value = newMode }

    // ✅ Efecto para auto-seleccionar tab cuando hay resultados en solo uno de los tipos
    init {
        // ✅ DEBOUNCE: Procesar búsqueda debounced
        debouncedSearchQuery
            .onEach { query ->
                _currentTokens.value = if (query.isBlank()) emptyList() else SearchLexer.lex(query)
            }
            .launchIn(viewModelScope)

        // Auto-select tab
        autoMode
            .filterNotNull()  // Solo procesar valores no-null (cuando hay auto-select)
            .distinctUntilChanged()  // Evitar procesamiento de cambios idénticos
            .onEach { auto ->
                // Cambiar el modo para que la UI se actualice
                _mode.value = auto
            }
            .launchIn(viewModelScope)

        // ✅ PRECARGA: Cargar datos en background sin bloquear UI
        viewModelScope.launch {
            android.util.Log.d("DictionaryVM", "🔄 Iniciando precarga de datos...")
            try {
                repository.getAllCharacters()
                    .onStart { android.util.Log.d("DictionaryVM", "✓ Precarga de caracteres iniciada") }
                    .onCompletion { android.util.Log.d("DictionaryVM", "✓ Precarga de caracteres completada") }
                    .collect { characters ->
                        android.util.Log.d("DictionaryVM", "📊 Precargados ${characters.size} caracteres")
                    }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryVM", "❌ Error en precarga: ${e.message}", e)
            }
        }

        // Precarga de palabras también
        viewModelScope.launch {
            try {
                repository.getAllWords()
                    .onStart { android.util.Log.d("DictionaryVM", "✓ Precarga de palabras iniciada") }
                    .onCompletion { android.util.Log.d("DictionaryVM", "✓ Precarga de palabras completada") }
                    .collect { words ->
                        android.util.Log.d("DictionaryVM", "📊 Precargadas ${words.size} palabras")
                    }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryVM", "❌ Error en precarga de palabras: ${e.message}", e)
            }
        }
    }
    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        // ✅ DEBOUNCE se encarga de actualizar tokens automáticamente
        // No actualizar _currentTokens directamente aquí
    }

    // --- NEW FUNCTIONS FOR THE STUDY LIST ---

    fun isItemBeingStudied(item: DictionaryItem): Flow<Boolean> {
        return when (item) {
            is DictionaryItem.CharacterItem -> repository.isItemBeingStudied(item.character.id, "character")
            is DictionaryItem.WordItem -> repository.isItemBeingStudied(item.word.id, "word")
        }
    }

    fun addOrRemoveItem(item: DictionaryItem, isBeingStudied: Boolean) {
        viewModelScope.launch {
            val studyItem: StudyItem
            val itemId: Int
            val itemType: String

            when (item) {
                is DictionaryItem.CharacterItem -> {
                    itemId = item.character.id
                    itemType = "character"
                    studyItem = StudyItem(itemId = itemId, itemType = itemType)
                }
                is DictionaryItem.WordItem -> {
                    itemId = item.word.id
                    itemType = "word"
                    studyItem = StudyItem(itemId = itemId, itemType = itemType)
                }
            }

            if (isBeingStudied) {
                repository.removeItemFromStudyList(itemId, itemType)
            } else {
                repository.addItemToStudyList(studyItem)
            }
        }
    }
}

// The Factory is now simpler, as it only needs the repository
class DictionaryViewModelFactory(private val repository: DictionaryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DictionaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DictionaryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}