package com.jukhg10.hanzimemo.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jukhg10.hanzimemo.ui.characters.CharacterCard
import com.jukhg10.hanzimemo.ui.characters.DictionaryItem
import com.jukhg10.hanzimemo.ui.characters.WordCard
import com.jukhg10.hanzimemo.ui.Screen

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    navController: NavController? = null
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val characterCount by viewModel.characterCount.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()
    val progressPercentage by viewModel.progressPercentage.collectAsState()
    val learnedCount by viewModel.learnedCount.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    
    val studyingItems by viewModel.studyingItems.collectAsState()
    val learnedItems by viewModel.learnedItems.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- Barra de búsqueda ---
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("Search your study list...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") }
        )

        // --- Progress Bar ---
        if (totalCount > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress: $learnedCount / $totalCount",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = String.format("%.1f%%", progressPercentage),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                LinearProgressIndicator(
                    progress = { progressPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // --- Contadores ---
        if (totalCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(
                    text = "Characters: $characterCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Words: $wordCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Subtabs ---
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Studying (${studyingItems.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Learned (${learnedItems.size})") }
            )
        }

        // --- Content based on selected tab ---
        val displayItems = if (selectedTab == 0) studyingItems else learnedItems
        
        if (displayItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No results found." else (
                        if (selectedTab == 0) "No items to study yet." else "No learned items yet."
                    ),
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
                items(displayItems, key = { item ->
                    when (item) {
                        is DictionaryItem.CharacterItem -> "char_${item.character.id}"
                        is DictionaryItem.WordItem -> "word_${item.word.id}"
                    }
                }) { item ->
                    Box {
                        // Hacer el card clickable para navegar a Flashcard
                        val itemId = when (item) {
                            is DictionaryItem.CharacterItem -> item.character.id
                            is DictionaryItem.WordItem -> item.word.id
                        }
                        
                            when (item) {
                            is DictionaryItem.CharacterItem -> CharacterCard(
                                item = item, 
                                viewModel = null,
                                onCardClick = {
                                    navController?.navigate(Screen.FlashcardWithId.createRoute("character", itemId)) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                            is DictionaryItem.WordItem -> WordCard(
                                item = item, 
                                viewModel = null,
                                onCardClick = {
                                    navController?.navigate(Screen.FlashcardWithId.createRoute("word", itemId)) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            // Botón para cambiar estado learned
                            IconButton(
                                onClick = { 
                                    val newStatus = selectedTab == 0 // Si está en "Studying", marcarlo como learned
                                    viewModel.markAsLearned(itemId, newStatus)
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.Search else Icons.Default.Refresh,
                                    contentDescription = if (selectedTab == 0) "Mark as learned" else "Mark as studying"
                                )
                            }
                            // Botón para eliminar
                            IconButton(
                                onClick = { viewModel.removeItem(item) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove from study list")
                            }
                        }
                    }
                }
            }
        }
    }
}