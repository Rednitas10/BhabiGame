package com.example.bhabhigameandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.example.bhabhigameandroid.ui.composables.GameScreen
// Assuming your theme is defined elsewhere, e.g., ui.theme.BhabhiGameAndroidTheme
// For now, we'll use a basic MaterialTheme if a custom one isn't readily available.

class MainActivity : ComponentActivity() {
    private val gameEngine: GameEngine by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Using a basic MaterialTheme. If you have a BhabhiGameAndroidTheme, use that.
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    GameScreen(gameEngine = gameEngine)
                }
            }
        }
    }
}
