package com.jukhg10.hanzimemo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.jukhg10.hanzimemo.data.DictionaryRepository
import com.jukhg10.hanzimemo.data.HanziRecognitionService
import com.jukhg10.hanzimemo.ui.about.AboutScreen
import com.jukhg10.hanzimemo.ui.camera.CameraRecognitionScreen
import com.jukhg10.hanzimemo.ui.camera.CameraRecognitionViewModel
import com.jukhg10.hanzimemo.ui.camera.CameraRecognitionViewModelFactory
import com.jukhg10.hanzimemo.ui.characters.*
import com.jukhg10.hanzimemo.ui.navigation.Routes
import com.jukhg10.hanzimemo.ui.review.*

@Composable
fun MainScreen(repository: DictionaryRepository) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Dictionary") },
                    label = { Text("Diccionario") },
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.DICTIONARY_SCREEN } == true,
                    onClick = {
                        navController.navigate(Routes.DICTIONARY_SCREEN) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = "Review") },
                    label = { Text("Revisión") },
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.REVIEW_SCREEN } == true,
                    onClick = {
                        navController.navigate(Routes.REVIEW_SCREEN) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = "Camera") },
                    label = { Text("Cámara") },
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.CAMERA_SCREEN } == true,
                    onClick = {
                        navController.navigate(Routes.CAMERA_SCREEN) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Info, contentDescription = "About") },
                    label = { Text("Acerca") },
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.ABOUT_SCREEN } == true,
                    onClick = {
                        navController.navigate(Routes.ABOUT_SCREEN) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Routes.DICTIONARY_SCREEN,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.DICTIONARY_SCREEN) {
                val viewModel: DictionaryViewModel = viewModel(factory = DictionaryViewModelFactory(repository))
                DictionaryScreen(viewModel = viewModel)
            }
            composable(Routes.REVIEW_SCREEN) {
                val viewModel: ReviewViewModel = viewModel(factory = ReviewViewModelFactory(repository))
                ReviewScreen(viewModel = viewModel)
            }
            composable(Routes.CAMERA_SCREEN) {
                val context = LocalContext.current
                val recognitionService = HanziRecognitionService(context, repository)
                val viewModel: CameraRecognitionViewModel = viewModel(
                    factory = CameraRecognitionViewModelFactory(repository, recognitionService)
                )
                CameraRecognitionScreen(viewModel = viewModel)
            }
            composable(Routes.ABOUT_SCREEN) {
                AboutScreen()
            }
        }
    }
}