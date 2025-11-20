package com.jukhg10.hanzimemo.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jukhg10.hanzimemo.data.Character
import com.jukhg10.hanzimemo.data.Word

/**
 * Barra de búsqueda completa con validación pinyin en tiempo real
 * y lista de resultados (caracteres o palabras).
 *
 * Características:
 * - TextField con soporte para mini-lenguaje: p:pinyin d:definition h:hanzi
 * - Icono ✓ / ✗ que valida sílabas pinyin en tiempo real
 * - Lista de resultados filtrando por todos los tokens (AND semantics)
 * - Tarjetas clickeables para agregar a estudio o ver detalles
 */
@Composable
fun DictionarySearchBar(
    viewModel: DictionaryViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery = viewModel.searchQuery.collectAsState()
    val pinyinValid = viewModel.pinyinTokensValid.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // --- SEARCH FIELD WITH VALIDATION ICON ---
        SearchFieldWithIcon(
            query = searchQuery.value,
            onQueryChange = { viewModel.onSearchQueryChange(it) },
            isPinyinValid = pinyinValid.value,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SearchFieldWithIcon(
    query: String,
    onQueryChange: (String) -> Unit,
    isPinyinValid: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        label = { Text("Búsqueda: p:pinyin d:definition h:hanzi") },
        trailingIcon = {
            if (query.isNotBlank()) {
                if (isPinyinValid) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Pinyin válido",
                        tint = Color.Green,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Pinyin inválido",
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(8.dp),
        singleLine = true
    )

    if (query.isNotBlank() && !isPinyinValid) {
        Text(
            text = "⚠ Tonos pinyin inválidos — usa: a1, a2, a3, a4, a5 o a: (ej. shui3, ni3)",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Red,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ResultsList(
    items: List<DictionaryItem>,
    viewModel: DictionaryViewModel,
    modifier: Modifier = Modifier,
    onCharacterClick: (Character) -> Unit = {},
    onWordClick: (Word) -> Unit = {}
) {
    if (items.isEmpty()) {
        Text(
            text = "Sin resultados",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        LazyColumn(modifier = modifier) {
            items(items) { item ->
                when (item) {
                    is DictionaryItem.CharacterItem -> CharacterResultCard(
                        item = item,
                        viewModel = viewModel,
                        onCharacterClick = onCharacterClick
                    )
                    is DictionaryItem.WordItem -> WordResultCard(
                        item = item,
                        viewModel = viewModel,
                        onWordClick = onWordClick
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterResultCard(
    item: DictionaryItem.CharacterItem,
    viewModel: DictionaryViewModel,
    onCharacterClick: (Character) -> Unit = {}
) {
    val isBeingStudied = viewModel.isItemBeingStudied(item).collectAsState(initial = false)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onCharacterClick(item.character) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = item.character.simplified,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 28.sp
                )
                Text(
                    text = item.character.pinyin,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = item.character.definition,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            
            // Botón para agregar/quitar de estudio
            IconButton(
                onClick = { viewModel.addOrRemoveItem(item, isBeingStudied.value) }
            ) {
                Icon(
                    imageVector = if (isBeingStudied.value) 
                        Icons.Outlined.CheckCircle 
                    else 
                        Icons.Outlined.AddCircleOutline,
                    contentDescription = "Add to study list",
                    tint = if (isBeingStudied.value) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Text(
                text = item.character.traditional,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(Color.LightGray, RoundedCornerShape(4.dp))
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun WordResultCard(
    item: DictionaryItem.WordItem,
    viewModel: DictionaryViewModel,
    onWordClick: (Word) -> Unit = {}
) {
    val isBeingStudied = viewModel.isItemBeingStudied(item).collectAsState(initial = false)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onWordClick(item.word) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = item.word.simplified,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 24.sp
                )
                Text(
                    text = item.word.pinyin,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = item.word.definition,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            
            // Botón para agregar/quitar de estudio
            IconButton(
                onClick = { viewModel.addOrRemoveItem(item, isBeingStudied.value) }
            ) {
                Icon(
                    imageVector = if (isBeingStudied.value) 
                        Icons.Outlined.CheckCircle 
                    else 
                        Icons.Outlined.AddCircleOutline,
                    contentDescription = "Add to study list",
                    tint = if (isBeingStudied.value) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Text(
                text = item.word.traditional,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(Color.LightGray, RoundedCornerShape(4.dp))
                    .padding(4.dp)
            )
        }
    }
}
