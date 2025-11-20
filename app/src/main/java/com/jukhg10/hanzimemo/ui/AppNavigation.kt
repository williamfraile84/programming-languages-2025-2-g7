package com.jukhg10.hanzimemo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.data.HanziRecognitionService
import com.jukhg10.hanzimemo.ui.about.AboutScreen
import com.jukhg10.hanzimemo.ui.camera.CameraRecognitionScreen
import com.jukhg10.hanzimemo.ui.camera.CameraRecognitionViewModel
import com.jukhg10.hanzimemo.ui.camera.CameraRecognitionViewModelFactory
import com.jukhg10.hanzimemo.ui.characters.*
import com.jukhg10.hanzimemo.ui.flashcard.FlashcardScreen
import com.jukhg10.hanzimemo.ui.flashcard.FlashcardViewModel
import com.jukhg10.hanzimemo.ui.flashcard.FlashcardViewModelFactory
import com.jukhg10.hanzimemo.ui.review.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dictionary : Screen("dictionary", "Dictionary", Icons.AutoMirrored.Filled.List)
    object Review : Screen("review", "Review", Icons.Default.Refresh)
    object Flashcard : Screen("flashcard", "Flashcards", Icons.Default.Style)
    object FlashcardWithId : Screen("flashcard/{itemType}/{itemId}", "Flashcards", Icons.Default.Style) {
        fun createRoute(itemType: String, itemId: Int) = "flashcard/$itemType/$itemId"
    }
    object Camera : Screen("camera", "Camera", Icons.Default.PhotoCamera)
    object About : Screen("about", "Acerca", Icons.Default.Info)
}

val bottomNavItems = listOf(Screen.Dictionary, Screen.Review, Screen.Flashcard, Screen.Camera, Screen.About)

// Nested graph routes para independent backstacks
const val DICTIONARY_GRAPH = "dictionary_graph"
const val REVIEW_GRAPH = "review_graph"
const val FLASHCARD_GRAPH = "flashcard_graph"
const val CAMERA_GRAPH = "camera_graph"

@Composable
fun AppNavigation(repository: DictionaryRepository) {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            // Navega al gráfico raíz para cada pantalla, manteniendo sus backstacks independientes
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dictionary.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ==================== DICTIONARY ROUTE ====================
            composable(Screen.Dictionary.route) {
                val dictionaryViewModel: DictionaryViewModel = 
                    viewModel(factory = DictionaryViewModelFactory(repository))
                DictionaryScreen(
                    viewModel = dictionaryViewModel,
                    onCharacterSelected = { characterId ->
                        // Navega a detalle de flashcard por tipo/id
                        navController.navigate(Screen.FlashcardWithId.createRoute("character", characterId)) {
                            launchSingleTop = true
                        }
                    },
                    onWordSelected = { wordId ->
                        // Navega a detalle de flashcard por tipo/id
                        navController.navigate(Screen.FlashcardWithId.createRoute("word", wordId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            
            // ==================== REVIEW ROUTE ====================
            composable(Screen.Review.route) {
                val reviewViewModel: ReviewViewModel = 
                    viewModel(factory = ReviewViewModelFactory(repository))
                ReviewScreen(viewModel = reviewViewModel, navController = navController)
            }
            
            // ==================== FLASHCARD ROUTE (sin argumento - sesión completa) ====================
            composable(Screen.Flashcard.route) {
                val flashcardViewModel: FlashcardViewModel = 
                    viewModel(factory = FlashcardViewModelFactory(repository))
                FlashcardScreen(
                    viewModel = flashcardViewModel,
                    navController = navController,
                    studyItemId = null
                )
            }
            
            // ==================== FLASHCARD WITH ID ROUTE (detalle de item específico) ====================
            composable(
                route = Screen.FlashcardWithId.route,
                arguments = listOf(
                    navArgument("itemType") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val itemType = backStackEntry.arguments?.getString("itemType") ?: ""
                val itemId = backStackEntry.arguments?.getInt("itemId") ?: 0
                val flashcardViewModel: FlashcardViewModel = 
                    viewModel(factory = FlashcardViewModelFactory(repository))
                
                FlashcardScreen(
                    viewModel = flashcardViewModel,
                    navController = navController,
                    studyItemId = itemId,
                    itemType = itemType
                )
            }
            
            // ==================== CAMERA ROUTE ====================
            composable(Screen.Camera.route) {
                val context = LocalContext.current
                val recognitionService = HanziRecognitionService(context, repository)
                val viewModel: CameraRecognitionViewModel = viewModel(
                    factory = CameraRecognitionViewModelFactory(repository, recognitionService)
                )
                CameraRecognitionScreen(viewModel = viewModel, navController = navController)
            }
            
            // ==================== ABOUT ROUTE ====================
            composable(Screen.About.route) {
                AboutScreen()
            }
        }
    }
}