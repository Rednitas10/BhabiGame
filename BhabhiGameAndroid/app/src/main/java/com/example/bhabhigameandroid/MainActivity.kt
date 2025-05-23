package com.example.bhabhigameandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bhabhigameandroid.ui.composables.GameScreen
import com.example.bhabhigameandroid.ui.composables.MainMenuScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Using a basic MaterialTheme. If you have a BhabhiGameAndroidTheme, use that.
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    BhabhiApp()
                }
            }
        }
    }
}

@Composable
fun BhabhiApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main_menu") {
        composable("main_menu") {
            MainMenuScreen(navController = navController)
        }
        composable("game_screen") {
            // GameScreen now gets its own GameEngine instance via viewModel()
            // and takes navController for "Back to Main Menu"
            GameScreen(navController = navController)
        }
        composable("rules_screen") {
            com.example.bhabhigameandroid.ui.composables.RulesScreen(navController = navController)
        }
    }
}
