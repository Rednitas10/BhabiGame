package com.example.bhabhigameandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bhabhigameandroid.ui.composables.FriendsScreen // Added FriendsScreen import
import com.example.bhabhigameandroid.ui.composables.GameRoomScreen
import com.example.bhabhigameandroid.ui.composables.GameScreen
import com.example.bhabhigameandroid.ui.composables.LobbyScreen
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

    LaunchedEffect(Unit) {
        if (FirebaseService.getCurrentUserId() == null) {
            FirebaseService.signInAnonymously(
                onSuccess = { uid ->
                    Log.d("MainActivity", "Anonymous sign-in successful. UID: $uid")
                },
                onFailure = { exception ->
                    Log.e("MainActivity", "Anonymous sign-in failed.", exception)
                    // Optionally, show an error to the user or retry
                }
            )
        } else {
            Log.d("MainActivity", "User already signed in. UID: ${FirebaseService.getCurrentUserId()}")
        }
    }

    NavHost(navController = navController, startDestination = "lobby_screen") { // Changed startDestination
        composable("main_menu") { // Kept for potential future use
            MainMenuScreen(navController = navController)
        }
        composable("lobby_screen") {
            LobbyScreen(navController = navController)
        }
        composable("game_screen") { // This might be deprecated or used for local play
            GameScreen(navController = navController)
        }
        composable("rules_screen") {
            com.example.bhabhigameandroid.ui.composables.RulesScreen(navController = navController)
        }
        composable(
            "game_room_screen/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            if (roomId != null) {
                GameRoomScreen(navController = navController, roomId = roomId)
            } else {
                Log.e("BhabhiApp", "Error: RoomId is null for game_room_screen.")
                navController.popBackStack()
            }
        }
        composable("friends_screen") {
            FriendsScreen(navController = navController)
        }
    }
}
