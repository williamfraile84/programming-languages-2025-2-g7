package com.jukhg10.hanzimemo.ui.flashcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.data.ReviewPair
import com.jukhg10.hanzimemo.data.StudyItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.jukhg10.hanzimemo.ui.characters.DictionaryItem

enum class StudyStatus { STUDYING, LEARNED }

data class FlashcardUiState(
    val deck: List<ReviewPair> = emptyList(),
    val currentItem: ReviewPair? = null,
    val isRevealed: Boolean = false,
    val sessionActive: Boolean = false,
    val currentStudyStatus: StudyStatus = StudyStatus.STUDYING
)

class FlashcardViewModel(private val repository: DictionaryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FlashcardUiState())
    val uiState: StateFlow<FlashcardUiState> = _uiState.asStateFlow()

    private val _sessionDeck = MutableStateFlow<List<ReviewPair>>(emptyList())
    private var studyingDeck: MutableList<ReviewPair> = mutableListOf()  // Items not yet learned
    private var learnedDeck: MutableList<ReviewPair> = mutableListOf()   // Items marked as learned
    private var currentIndex = -1
    private var singleItemMode = false

    // ✅ StateFlows globales para contadores (basados en sessionDeck como Review)
    val characterCount: StateFlow<Int> = _sessionDeck.map { deck ->
        deck.count { it.dictionaryItem is DictionaryItem.CharacterItem }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val wordCount: StateFlow<Int> = _sessionDeck.map { deck ->
        deck.count { it.dictionaryItem is DictionaryItem.WordItem }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val studyingCount: StateFlow<Int> = _sessionDeck.map { deck ->
        deck.count { !it.studyItem.learned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val learnedCount: StateFlow<Int> = _sessionDeck.map { deck ->
        deck.count { it.studyItem.learned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> = _sessionDeck.map { deck ->
        deck.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val progressPercentage: StateFlow<Float> = combine(learnedCount, totalCount) { learned, total ->
        if (total == 0) 0f else (learned.toFloat() / total) * 100
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun startSession() {
        viewModelScope.launch {
            val sessionList = repository.getReviewDeck().toMutableList()
            _sessionDeck.emit(sessionList)
            
            // Separar en dos decks: estudiando y aprendido
            studyingDeck = sessionList.filter { !it.studyItem.learned }.toMutableList()
            learnedDeck = sessionList.filter { it.studyItem.learned }.toMutableList()
            
            _uiState.update {
                it.copy(
                    deck = studyingDeck,  // Por defecto mostrar estudiando
                    sessionActive = sessionList.isNotEmpty(),
                    currentItem = null,
                    currentStudyStatus = StudyStatus.STUDYING
                )
            }
            if (studyingDeck.isNotEmpty()) {
                currentIndex = -1
                findNextCard()
            }
        }
    }

    fun loadItemById(studyItemId: Int) {
        viewModelScope.launch {
            try {
                val item = repository.getStudyItemById(studyItemId)
                if (item != null) {
                    singleItemMode = true
                    val deck = mutableListOf(item)
                    _sessionDeck.emit(deck)
                    _uiState.update {
                        it.copy(
                            deck = deck,
                            sessionActive = true,
                            currentItem = item,
                            isRevealed = false
                        )
                    }
                    currentIndex = 0
                } else {
                    _uiState.update {
                        it.copy(
                            sessionActive = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        sessionActive = false
                    )
                }
            }
        }
    }

    /**
     * Cargar o crear un StudyItem dado un dictionary id y tipo (character/word)
     */
    fun loadByDictionaryId(itemType: String, dictionaryId: Int) {
        viewModelScope.launch {
            try {
                val studyItem = repository.findOrCreateStudyItem(dictionaryId, itemType)
                if (studyItem != null) {
                    singleItemMode = true
                    val pair = repository.getStudyItemById(studyItem.id)
                    if (pair != null) {
                        val deck = mutableListOf(pair)
                        _sessionDeck.emit(deck)
                        _uiState.update {
                            it.copy(
                                deck = deck,
                                sessionActive = true,
                                currentItem = pair,
                                isRevealed = false
                            )
                        }
                        currentIndex = 0
                    } else {
                        _uiState.update { it.copy(sessionActive = false) }
                    }
                } else {
                    _uiState.update { it.copy(sessionActive = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(sessionActive = false) }
            }
        }
    }

    private fun findNextCard() {
        viewModelScope.launch {
            // Usar el deck correcto según el tab activo
            val currentDeck = if (_uiState.value.currentStudyStatus == StudyStatus.STUDYING) studyingDeck else learnedDeck
            
            if (currentDeck.isEmpty()) {
                // ✅ NO cerrar la sesión, solo limpiar currentItem (la tab está vacía)
                _uiState.update { it.copy(currentItem = null, isRevealed = false) }
                return@launch
            }

            var searchIndex = currentIndex
            // This loop ensures we check every card once to find the next one to show.
            for (i in 0 until currentDeck.size) {
                searchIndex = (searchIndex + 1) % currentDeck.size
                val currentPair = currentDeck[searchIndex]

                if (currentPair.studyItem.currentPriority > 1) {
                    val newPriority = currentPair.studyItem.currentPriority - 1
                    repository.decrementItemPriority(currentPair.studyItem)
                    currentDeck[searchIndex] = currentPair.copy(
                        studyItem = currentPair.studyItem.copy(currentPriority = newPriority)
                    )
                } else {
                    currentIndex = searchIndex
                    _uiState.update {
                        it.copy(
                            deck = currentDeck,
                            currentItem = currentPair,
                            isRevealed = false
                        )
                    }
                    return@launch
                }
            }
            // If we finish a full loop without finding a card, start the search again.
            findNextCard()
        }
    }

    fun handleReviewAction(knewIt: Boolean) {
        val currentPair = _uiState.value.currentItem ?: return

        viewModelScope.launch {
            try {
                // Usar transacciones atómicas del repository
                if (knewIt) {
                    repository.onKnewIt(currentPair.studyItem)
                    
                    // Actualizar el item en memoria
                    val newMaxPriority = currentPair.studyItem.maxPriority + 1
                    val updatedStudyItem = currentPair.studyItem.copy(
                        currentPriority = newMaxPriority,
                        maxPriority = newMaxPriority,
                        learned = true
                    )
                    val updatedPair = currentPair.copy(studyItem = updatedStudyItem)
                    
                    // Mover de studyingDeck a learnedDeck
                    val currentDeck = if (_uiState.value.currentStudyStatus == StudyStatus.STUDYING) studyingDeck else learnedDeck
                    val indexInCurrentDeck = currentDeck.indexOfFirst { it.studyItem.id == currentPair.studyItem.id }
                    if (indexInCurrentDeck >= 0) {
                        currentDeck.removeAt(indexInCurrentDeck)
                    }
                    learnedDeck.add(updatedPair)
                    
                    // ✅ Actualizar sessionDeck para mantener counters consistentes
                    val indexInSession = _sessionDeck.value.indexOfFirst { it.studyItem.id == currentPair.studyItem.id }
                    if (indexInSession >= 0) {
                        val updatedSession = _sessionDeck.value.toMutableList()
                        updatedSession[indexInSession] = updatedPair
                        _sessionDeck.emit(updatedSession)
                    }
                    
                    // ✅ FIX #2: Si estamos en STUDYING tab y movemos a LEARNED,
                    // automáticamente cambiar el deck mostrado al STUDYING (el que ahora tiene el siguiente item)
                    // O mantener en STUDYING pero mostrar el siguiente del deck actual
                    val targetDeck = if (_uiState.value.currentStudyStatus == StudyStatus.STUDYING) 
                        studyingDeck 
                    else 
                        learnedDeck
                    
                    val nextItem = if (targetDeck.isNotEmpty()) targetDeck.first() else null
                    _uiState.update {
                        it.copy(
                            deck = targetDeck,
                            currentItem = nextItem,
                            isRevealed = false
                        )
                    }
                    
                    currentIndex = -1
                    if (targetDeck.isNotEmpty()) {
                        findNextCard()
                    }
                } else {
                    repository.onForgot(currentPair.studyItem)
                    
                    // Actualizar el item en memoria
                    val newCurrentPriority = 1
                    val newMaxPriority = (currentPair.studyItem.maxPriority - 1).coerceAtLeast(1)
                    val updatedStudyItem = currentPair.studyItem.copy(
                        currentPriority = newCurrentPriority,
                        maxPriority = newMaxPriority,
                        learned = false
                    )
                    val updatedPair = currentPair.copy(studyItem = updatedStudyItem)
                    
                    // Asegurar que esté en studyingDeck (podría estar en learnedDeck)
                    val indexInLearned = learnedDeck.indexOfFirst { it.studyItem.id == currentPair.studyItem.id }
                    if (indexInLearned >= 0) {
                        learnedDeck.removeAt(indexInLearned)
                    }
                    if (!studyingDeck.contains(updatedPair)) {
                        studyingDeck.add(updatedPair)
                    }
                    
                    // ✅ Actualizar sessionDeck para mantener counters consistentes
                    val indexInSession = _sessionDeck.value.indexOfFirst { it.studyItem.id == currentPair.studyItem.id }
                    if (indexInSession >= 0) {
                        val updatedSession = _sessionDeck.value.toMutableList()
                        updatedSession[indexInSession] = updatedPair
                        _sessionDeck.emit(updatedSession)
                    }
                    
                    // ✅ FIX #2: Si estamos en LEARNED tab y movemos a STUDYING,
                    // mostrar el siguiente del LEARNED deck (que ahora tiene un item menos)
                    val targetDeck = if (_uiState.value.currentStudyStatus == StudyStatus.STUDYING) 
                        studyingDeck 
                    else 
                        learnedDeck
                    
                    val nextItem = if (targetDeck.isNotEmpty()) targetDeck.first() else null
                    _uiState.update {
                        it.copy(
                            deck = targetDeck,
                            currentItem = nextItem,
                            isRevealed = false
                        )
                    }
                    
                    currentIndex = -1
                    if (targetDeck.isNotEmpty()) {
                        findNextCard()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HanziMemo.VM", "handleReviewAction failed: ${e.message}", e)
            }
        }
    }

    // Public function for the "Next" button after forgetting a card
    fun proceedToNextItem() {
        findNextCard()
    }
    
    fun showNextItem() {
        if (singleItemMode) return
        findNextCard()
    }
    
    fun revealCard() {
        _uiState.update { it.copy(isRevealed = true) }
    }
    
    fun toggleRevealCard() {
        _uiState.update { it.copy(isRevealed = !it.isRevealed) }
    }
    
    fun showPreviousItem() {
        if (singleItemMode) return
        
        viewModelScope.launch {
            val currentDeck = if (_uiState.value.currentStudyStatus == StudyStatus.STUDYING) 
                studyingDeck 
            else 
                learnedDeck
            
            if (currentDeck.isEmpty()) return@launch
            
            // ✅ FIX #4: No permitir wrapping al final sin feedback visual
            val newIndex = currentIndex - 1
            if (newIndex < 0) {
                // Ya estamos en el primer item, mostrar error o no hacer nada
                android.util.Log.i("FlashcardViewModel", "Already at first item")
                return@launch
            }
            
            if (newIndex in currentDeck.indices) {
                currentIndex = newIndex
                val previousPair = currentDeck[newIndex]
                _uiState.update {
                    it.copy(
                        deck = currentDeck,
                        currentItem = previousPair,
                        isRevealed = false
                    )
                }
            }
        }
    }
    
    fun syncDeckOnResume() {
        if (!uiState.value.sessionActive) return // Don't do anything if a session isn't running

        viewModelScope.launch {
            val currentDbDeck = repository.getReviewDeck()
            // Compare the IDs of the deck in memory with the deck in the database
            val deckInMemoryIds = _sessionDeck.value.map { it.studyItem.id }.toSet()
            val dbDeckIds = currentDbDeck.map { it.studyItem.id }.toSet()

            if (deckInMemoryIds != dbDeckIds) {
                // The lists are different, so restart the session
                startSession()
            }
        }
    }

    /**
     * Cambiar entre tabs Estudiando / Aprendido
     * Filtra y muestra el deck correcto según el status seleccionado
     */
    fun switchStudyStatus(newStatus: StudyStatus) {
        viewModelScope.launch {
            val targetDeck = if (newStatus == StudyStatus.STUDYING) studyingDeck else learnedDeck
            
            // ✅ FIX #3: Garantizar que currentItem esté sincronizado con el nuevo deck
            val nextItem = if (targetDeck.isNotEmpty()) targetDeck[0] else null
            
            _uiState.update {
                it.copy(
                    deck = targetDeck,
                    currentStudyStatus = newStatus,
                    currentItem = nextItem,  // ✅ Sincronizar siempre
                    isRevealed = false
                )
            }
            
            // Resetear índice para empezar desde el primero del nuevo deck
            currentIndex = -1
            if (targetDeck.isNotEmpty()) {
                findNextCard()
            }
        }
    }
}


class FlashcardViewModelFactory(private val repository: DictionaryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlashcardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FlashcardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}