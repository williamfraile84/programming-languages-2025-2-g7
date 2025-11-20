package com.jukhg10.hanzimemo.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.data.ReviewPair
import com.jukhg10.hanzimemo.ui.characters.DictionaryItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReviewViewModel(private val repository: DictionaryRepository) : ViewModel() {

    // State for the search text
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Flow con los items y su estado learned
    private val allReviewItems: StateFlow<List<ReviewPair>> = 
        repository.getFullStudyListWithStatus()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Flow para items siendo estudiados (learned = false)
    val studyingItems: StateFlow<List<DictionaryItem>> =
        allReviewItems
            .combine(_searchQuery) { list, query ->
                list
                    .filter { it.studyItem.learned == false }
                    .map { it.dictionaryItem }
                    .filter { item ->
                        if (query.isBlank()) true else matchesQuery(item, query)
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Flow para items aprendidos (learned = true)
    val learnedItems: StateFlow<List<DictionaryItem>> =
        allReviewItems
            .combine(_searchQuery) { list, query ->
                list
                    .filter { it.studyItem.learned == true }
                    .map { it.dictionaryItem }
                    .filter { item ->
                        if (query.isBlank()) true else matchesQuery(item, query)
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Flow para TODOS los items (sin filtrar por learned)
    val reviewItems: StateFlow<List<DictionaryItem>> =
        allReviewItems
            .combine(_searchQuery) { list, query ->
                list
                    .map { it.dictionaryItem }
                    .filter { item ->
                        if (query.isBlank()) true else matchesQuery(item, query)
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // --- CONTADORES ---
    val characterCount: StateFlow<Int> = reviewItems.map { list ->
        list.count { it is DictionaryItem.CharacterItem }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val wordCount: StateFlow<Int> = reviewItems.map { list ->
        list.count { it is DictionaryItem.WordItem }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Progress: (learned / total) * 100
    val learnedCount: StateFlow<Int> = learnedItems.map { it.size }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val totalCount: StateFlow<Int> = reviewItems.map { it.size }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val progressPercentage: StateFlow<Float> = 
        combine(learnedCount, totalCount) { learned, total ->
            if (total == 0) 0f else (learned.toFloat() / total) * 100
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0f
        )

    private fun matchesQuery(item: DictionaryItem, query: String): Boolean {
        return when (item) {
            is DictionaryItem.CharacterItem -> {
                val char = item.character
                char.simplified.contains(query, ignoreCase = true) ||
                        char.traditional.contains(query, ignoreCase = true) ||
                        char.pinyin.replace("[0-9]".toRegex(), "").contains(query, ignoreCase = true) ||
                        char.definition.contains(query, ignoreCase = true)
            }
            is DictionaryItem.WordItem -> {
                val word = item.word
                word.simplified.contains(query, ignoreCase = true) ||
                        word.traditional.contains(query, ignoreCase = true) ||
                        word.pinyin.replace("[0-9]".toRegex(), "").contains(query, ignoreCase = true) ||
                        word.definition.contains(query, ignoreCase = true)
            }
        }
    }


    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun removeItem(item: DictionaryItem) {
        viewModelScope.launch {
            val itemId: Int
            val itemType: String
            when (item) {
                is DictionaryItem.CharacterItem -> {
                    itemId = item.character.id
                    itemType = "character"
                }
                is DictionaryItem.WordItem -> {
                    itemId = item.word.id
                    itemType = "word"
                }
            }
            repository.removeItemFromStudyList(itemId, itemType)
        }
    }

    fun markAsLearned(itemId: Int, learned: Boolean) {
        viewModelScope.launch {
            try {
                // itemId here is the dictionary id; fetch the study item if exists
                val studyItem = repository.getStudyItemByDictionaryId(itemId, if (learned) "character" else "character")
                // The above line attempts best-effort; try character then word if null
                var actualStudyItem = studyItem
                if (actualStudyItem == null) {
                    // try as word
                    actualStudyItem = repository.getStudyItemByDictionaryId(itemId, "word")
                }
                if (actualStudyItem != null) {
                    repository.markItemAsLearned(actualStudyItem.id, learned)
                } else {
                    // No study item found: create one and mark as learned if requested
                    val created = repository.findOrCreateStudyItem(itemId, "character") ?: repository.findOrCreateStudyItem(itemId, "word")
                    if (created != null) repository.markItemAsLearned(created.id, learned)
                }
            } catch (e: Exception) {
                // swallow but log
                android.util.Log.e("HanziMemo.ReviewVM", "markAsLearned failed: ${e.message}", e)
            }
        }
    }
}

class ReviewViewModelFactory(private val repository: DictionaryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReviewViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReviewViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}