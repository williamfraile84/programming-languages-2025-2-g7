package com.jukhg10.hanzimemo.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.data.StudyItem
import com.jukhg10.hanzimemo.ui.characters.CharacterCard
import com.jukhg10.hanzimemo.ui.characters.DictionaryItem
import com.jukhg10.hanzimemo.ui.characters.WordCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class ReviewTab { STUDYING, MASTERED }

data class ReviewStats(
    val totalStudying: Int = 0,
    val totalMastered: Int = 0,
    val progressPercent: Float = 0f
)

@OptIn(ExperimentalCoroutinesApi::class)
class EnhancedReviewViewModel(private val repository: DictionaryRepository) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(ReviewTab.STUDYING)
    val selectedTab: StateFlow<ReviewTab> = _selectedTab.asStateFlow()

    // Todos los items en estudio
    private val allReviewItems: StateFlow<List<DictionaryItem>> =
        repository.getFullStudyListItems()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    // Items filtrados según búsqueda y tab
    val reviewItems: StateFlow<List<DictionaryItem>> =
        combine<String, ReviewTab, List<DictionaryItem>, List<DictionaryItem>>(_searchQuery, _selectedTab, allReviewItems) { query, tab, allItems ->
            var filtered = allItems
            if (query.isNotBlank()) {
                filtered = filtered.filter { item ->
                    when (item) {
                        is DictionaryItem.CharacterItem ->
                            item.character.simplified.contains(query, ignoreCase = true) ||
                            item.character.definition.contains(query, ignoreCase = true)
                        is DictionaryItem.WordItem ->
                            item.word.simplified.contains(query, ignoreCase = true) ||
                            item.word.definition.contains(query, ignoreCase = true)
                    }
                }
            }

            // Filtrar según tab (ahora solo mostramos STUDYING, MASTERED sería futuro)
            when (tab) {
                ReviewTab.STUDYING -> filtered
                ReviewTab.MASTERED -> emptyList() // TODO: implementar cuando se separen learned items
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    // Estadísticas
    val reviewStats: StateFlow<ReviewStats> = allReviewItems
        .map { items ->
            ReviewStats(
                totalStudying = items.size,
                totalMastered = 0, // TODO: implementar conteo de items mastered
                progressPercent = if (items.isEmpty()) 0f else (0 / items.size).toFloat()
            )
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, ReviewStats())

    // Contadores por tipo
    val characterCount: StateFlow<Int> = reviewItems
        .map { items -> items.count { it is DictionaryItem.CharacterItem } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, 0)

    val wordCount: StateFlow<Int> = reviewItems
        .map { items -> items.count { it is DictionaryItem.WordItem } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, 0)

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun selectTab(tab: ReviewTab) {
        _selectedTab.value = tab
    }

    fun removeItem(item: DictionaryItem) {
        viewModelScope.launch {
            when (item) {
                is DictionaryItem.CharacterItem ->
                    repository.removeItemFromStudyList(item.character.id, "character")
                is DictionaryItem.WordItem ->
                    repository.removeItemFromStudyList(item.word.id, "word")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun markAsLearned(_item: DictionaryItem) {
        viewModelScope.launch {
            // TODO: implementar marcado como "learned"
            // Por ahora, solo muestra placeholder
        }
    }
}

class EnhancedReviewViewModelFactory(private val repository: DictionaryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EnhancedReviewViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EnhancedReviewViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun EnhancedReviewScreen(viewModel: EnhancedReviewViewModel) {
    val reviewItems by viewModel.reviewItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val characterCount by viewModel.characterCount.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()
    val reviewStats by viewModel.reviewStats.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra de búsqueda
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("Buscar en lista de estudio...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }
        )

        // Barra de progreso
        ProgressCard(stats = reviewStats)

        // Contador de items
        if (reviewItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("Caracteres", characterCount)
                VerticalDivider(modifier = Modifier.width(1.dp).height(24.dp))
                StatItem("Palabras", wordCount)
            }
        }

        // Tabs de estudio/aprendidas
        TabRow(selectedTabIndex = if (selectedTab == ReviewTab.STUDYING) 0 else 1) {
            Tab(
                selected = selectedTab == ReviewTab.STUDYING,
                onClick = { viewModel.selectTab(ReviewTab.STUDYING) },
                text = { Text("En estudio (${reviewItems.size})") }
            )
            Tab(
                selected = selectedTab == ReviewTab.MASTERED,
                onClick = { viewModel.selectTab(ReviewTab.MASTERED) },
                text = { Text("Aprendidas (0)") }
            )
        }

        // Lista de items
        if (reviewItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank())
                        "No se encontraron resultados"
                    else
                        "Añade items a tu lista de estudio para verlos aquí",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                items(reviewItems, key = { item ->
                    when (item) {
                        is DictionaryItem.CharacterItem -> "char_${item.character.id}"
                        is DictionaryItem.WordItem -> "word_${item.word.id}"
                    }
                }) { item ->
                    ReviewItemCard(
                        item = item,
                        onRemove = { viewModel.removeItem(item) },
                        onMarkLearned = { viewModel.markAsLearned(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressCard(stats: ReviewStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Progreso del aprendizaje",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${(stats.progressPercent * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }

            LinearProgressIndicator(
                progress = { stats.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFFE0E0E0)
            )

            Text(
                text = "${stats.totalMastered} de ${stats.totalStudying + stats.totalMastered} completadas",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun StatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun ReviewItemCard(
    item: DictionaryItem,
    onRemove: () -> Unit,
    onMarkLearned: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when (item) {
                    is DictionaryItem.CharacterItem -> {
                        Text(
                            text = item.character.simplified,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.character.pinyin} • ${item.character.definition}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 2
                        )
                    }
                    is DictionaryItem.WordItem -> {
                        Text(
                            text = item.word.simplified,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.word.pinyin} • ${item.word.definition}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 2
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onMarkLearned, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Marcar como aprendida",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar de estudio",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
