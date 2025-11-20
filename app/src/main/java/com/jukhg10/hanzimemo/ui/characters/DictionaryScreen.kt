package com.jukhg10.hanzimemo.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jukhg10.hanzimemo.data.Character
import com.jukhg10.hanzimemo.data.Word
import kotlinx.coroutines.launch

@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onCharacterSelected: (Int) -> Unit = {},
    onWordSelected: (Int) -> Unit = {}
) {
    val items by viewModel.items.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val listState = rememberLazyListState()

    // Effect to scroll to top on new search
    LaunchedEffect(searchQuery) {
        if (items.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Usar el nuevo SearchBar mejorado con validación pinyin ---
        DictionarySearchBar(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )
        
        ModeSelector(
            currentMode = mode,
            onModeChange = { newMode -> viewModel.setMode(newMode) }
        )

        Box {
            if (items.isEmpty()) {
                // Show improved empty message
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) 
                            "No se encontraron resultados para '$searchQuery'"
                        else 
                            "Selecciona una pestaña para ver los elementos",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(items, key = { item ->
                        when (item) {
                            is DictionaryItem.CharacterItem -> "char_${item.character.id}"
                            is DictionaryItem.WordItem -> "word_${item.word.id}"
                        }
                    }) { item ->
                        when (item) {
                            is DictionaryItem.CharacterItem -> CharacterCard(
                                item = item, 
                                viewModel = viewModel,
                                onCardClick = { onCharacterSelected(item.character.id) }
                            )
                            is DictionaryItem.WordItem -> WordCard(
                                item = item, 
                                viewModel = viewModel,
                                onCardClick = { onWordSelected(item.word.id) }
                            )
                        }
                    }
                }
            }

            // The Fast Scroller UI
            if (items.isNotEmpty()) {
                FastScroller(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun ModeSelector(currentMode: DictionaryMode, onModeChange: (DictionaryMode) -> Unit) {
    val tabs = listOf("Characters", "Words")
    val selectedIndex = if (currentMode == DictionaryMode.CHARACTERS) 0 else 1
    
    // ✅ TabRow con altura explícita para evitar que se oculte
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = {
                    val newMode = if (index == 0) DictionaryMode.CHARACTERS else DictionaryMode.WORDS
                    onModeChange(newMode)
                },
                text = { Text(text = title) }
            )
        }
    }
}

@Composable
fun CharacterCard(
    item: DictionaryItem.CharacterItem, 
    viewModel: DictionaryViewModel?,
    onCardClick: () -> Unit = {}
) {
    // Check if item is being studied (when viewModel provided)
    val isBeingStudied by if (viewModel != null) {
        viewModel.isItemBeingStudied(item).collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "${item.character.simplified} (${item.character.traditional})",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.character.pinyin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.character.definition,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            // Add/Remove button
            if (viewModel != null) {
                IconButton(
                    onClick = { viewModel.addOrRemoveItem(item, isBeingStudied) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isBeingStudied) Icons.Outlined.CheckCircle else Icons.Outlined.AddCircleOutline,
                        contentDescription = "Add to study list",
                        tint = if (isBeingStudied) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WordCard(
    item: DictionaryItem.WordItem, 
    viewModel: DictionaryViewModel?,
    onCardClick: () -> Unit = {}
) {
    // Check if item is being studied (when viewModel provided)
    val isBeingStudied by if (viewModel != null) {
        viewModel.isItemBeingStudied(item).collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "${item.word.simplified} (${item.word.traditional})",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.word.pinyin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.word.definition,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            // Add/Remove button
            if (viewModel != null) {
                IconButton(
                    onClick = { viewModel.addOrRemoveItem(item, isBeingStudied) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isBeingStudied) Icons.Outlined.CheckCircle else Icons.Outlined.AddCircleOutline,
                        contentDescription = "Add to study list",
                        tint = if (isBeingStudied) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
@Composable
fun FastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .width(20.dp)
            .padding(horizontal = 4.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        // Calculate the new scroll position based on the drag delta
                        val totalItems = listState.layoutInfo.totalItemsCount
                        if (totalItems > 0) {
                            val dragRatio = delta / listState.layoutInfo.viewportSize.height
                            val newScrollIndex = (listState.firstVisibleItemIndex + (dragRatio * totalItems)).toInt()
                            listState.scrollToItem(newScrollIndex.coerceIn(0, totalItems - 1))
                        }
                    }
                }
            )
    ) {
        // This is the visible scrollbar thumb
        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight(0.5f) // Adjust the height of the thumb
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                .align(Alignment.Center)
        )
    }
}