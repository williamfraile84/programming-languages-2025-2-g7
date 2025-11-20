package com.jukhg10.hanzimemo.ui.flashcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavController
import com.jukhg10.hanzimemo.data.ReviewPair
import com.jukhg10.hanzimemo.ui.characters.DictionaryItem

enum class StudyTab { STUDYING, LEARNED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    viewModel: FlashcardViewModel,
    navController: NavController? = null,
    studyItemId: Int? = null,
    itemType: String = ""
) {
    val uiState by viewModel.uiState.collectAsState()
    var studyTab by remember { mutableStateOf(StudyTab.STUDYING) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // ✅ Controlar si ya iniciamos la sesión
    val hasInitialized = remember { mutableStateOf(false) }
    
    // Cargar item específico si se proporciona un ID
    LaunchedEffect(studyItemId, itemType) {
        if (studyItemId != null && studyItemId > 0 && itemType.isNotEmpty()) {
            viewModel.loadByDictionaryId(itemType, studyItemId)
            hasInitialized.value = true
        }
    }
    
    // ✅ Iniciar sesión automáticamente si no tenemos item específico y no hemos inicializado
    LaunchedEffect(Unit) {
        if (studyItemId == null && !hasInitialized.value && !uiState.sessionActive) {
            viewModel.startSession()
            hasInitialized.value = true
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncDeckOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcard") },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(
                            onClick = { 
                                navController.popBackStack()
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver atrás",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ✅ SIEMPRE obtener contadores del ViewModel (incluso si currentItem es null)
            val characterCount by viewModel.characterCount.collectAsState()
            val wordCount by viewModel.wordCount.collectAsState()
            val progressPercentage by viewModel.progressPercentage.collectAsState()
            val learnedCountValue by viewModel.learnedCount.collectAsState()
            val totalCountValue by viewModel.totalCount.collectAsState()
            val studyingCountValue by viewModel.studyingCount.collectAsState()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // --- Progress Bar (siempre visible si hay items) ---
                    if (totalCountValue > 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Progress: $learnedCountValue / $totalCountValue",
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
                    
                    // --- Contadores de Characters / Words (siempre visible si hay items) ---
                    if (totalCountValue > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 0.dp, vertical = 8.dp),
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
                    
                    // --- Subtabs con contador (SIEMPRE VISIBLE si hay items) ---
                    if (totalCountValue > 0) {
                        TabRow(
                            selectedTabIndex = if (studyTab == StudyTab.STUDYING) 0 else 1,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Tab(
                                selected = studyTab == StudyTab.STUDYING,
                                onClick = {
                                    studyTab = StudyTab.STUDYING
                                    viewModel.switchStudyStatus(com.jukhg10.hanzimemo.ui.flashcard.StudyStatus.STUDYING)
                                },
                                text = { Text("Studying ($studyingCountValue)") }
                            )
                            Tab(
                                selected = studyTab == StudyTab.LEARNED,
                                onClick = {
                                    studyTab = StudyTab.LEARNED
                                    viewModel.switchStudyStatus(com.jukhg10.hanzimemo.ui.flashcard.StudyStatus.LEARNED)
                                },
                                text = { Text("Learned ($learnedCountValue)") }
                            )
                        }
                    }
                }
                
                // --- Área Principal: Flashcard o Mensaje según contexto ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        totalCountValue == 0 -> {
                            // No hay items en absoluto
                            Text(
                                text = "Select a character or word",
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        uiState.currentItem != null -> {
                            // Tenemos item: mostrar flashcard
                            Flashcard(
                                item = uiState.currentItem,
                                isRevealed = uiState.isRevealed,
                                onCardClick = {
                                    // ✅ Toggle reveal: TAP para revelar, TAP para ocultar
                                    viewModel.toggleRevealCard()
                                }
                            )
                        }
                        studyTab == StudyTab.STUDYING && studyingCountValue == 0 -> {
                            // Tab STUDYING vacía (todas aprendidas)
                            Text(
                                text = "Congratulations you have learned all the selected words",
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        studyTab == StudyTab.LEARNED && learnedCountValue == 0 -> {
                            // Tab LEARNED vacía (nada aprendido aún)
                            Text(
                                text = "Keep studying, you haven't learned any words yet",
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        else -> {
                            // Estado indeterminado (cargando)
                            CircularProgressIndicator()
                        }
                    }
                }
                
                // --- Botones de control (solo si hay item visible) ---
                if (uiState.currentItem != null) {
                    ReviewControls(
                        isRevealed = uiState.isRevealed,
                        currentTab = studyTab,
                        onForgotClick = { viewModel.handleReviewAction(knewIt = false) },
                        onKnewItClick = { viewModel.handleReviewAction(knewIt = true) },
                        onPrevClick = { viewModel.showPreviousItem() },
                        onNextClick = { viewModel.showNextItem() }
                    )
                }
            }
        }
    }
}

@Composable
fun Flashcard(
    item: ReviewPair?,
    isRevealed: Boolean,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clickable(onClick = onCardClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item == null) {
                CircularProgressIndicator()
            } else {
                val dictionaryItem = item.dictionaryItem
                val frontText: String
                val backContent: @Composable () -> Unit

                when (dictionaryItem) {
                    is DictionaryItem.CharacterItem -> {
                        val character = dictionaryItem.character
                        // Always show in RECALL mode (simplified character visible)
                        frontText = character.simplified
                        backContent = {
                            FlashcardBackContent(
                                pinyin = character.pinyin,
                                definition = character.definition,
                                traditional = character.traditional
                            )
                        }
                    }
                    is DictionaryItem.WordItem -> {
                        val word = dictionaryItem.word
                        // Always show in RECALL mode (simplified character visible)
                        frontText = word.simplified
                        backContent = {
                            FlashcardBackContent(
                                pinyin = word.pinyin,
                                definition = word.definition,
                                traditional = word.traditional
                            )
                        }
                    }
                }
                
                FlashcardContent(
                    front = frontText,
                    back = backContent,
                    isRevealed = isRevealed
                )
            }
        }
    }
}

@Composable
fun FlashcardBackContent(
    pinyin: String,
    definition: String,
    traditional: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tonos del pinyin (segmentados)
        Text(
            text = pinyin,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        HorizontalDivider(modifier = Modifier
            .fillMaxWidth(0.5f)
            .padding(vertical = 4.dp))
        
        // Caracteres tradicionales
        Text(
            text = "Traditional: $traditional",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
        
        // Definición con scroll si es necesario
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = definition,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun FlashcardContent(
    front: String,
    back: @Composable () -> Unit,
    isRevealed: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (!isRevealed) {
            Text(
                text = front,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "(tap card to reveal)",
                fontSize = 12.sp,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            back()
        }
    }
}

@Composable
fun ReviewControls(
    isRevealed: Boolean,
    currentTab: StudyTab,
    onForgotClick: () -> Unit,
    onKnewItClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ✅ Fila 1: Previous - Acción (contextual) - Next (siempre los 3)
        if (!isRevealed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Previous
                Button(
                    onClick = onPrevClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text("← Prev", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                // Botón de acción contextual según el tab
                if (currentTab == StudyTab.STUDYING) {
                    // Tab STUDYING: "I Knew It" en color primary (azul)
                    Button(
                        onClick = onKnewItClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("I Knew It", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Tab LEARNED: "I Forgot" en color error (rojo)
                    Button(
                        onClick = onForgotClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) {
                        Text("I Forgot", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Botón Next
                Button(
                    onClick = onNextClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text("Next →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // ✅ Cuando la tarjeta está revelada, mostrar Previous - Next con espaciado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrevClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text("← Prev", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onNextClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Next →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}