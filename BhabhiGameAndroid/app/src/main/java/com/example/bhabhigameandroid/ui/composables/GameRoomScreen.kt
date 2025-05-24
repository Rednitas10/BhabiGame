package com.example.bhabhigameandroid.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bhabhigameandroid.GameNetStatus // Ensure this is imported
import com.example.bhabhigameandroid.ui.viewmodels.GameRoomViewModel
import com.example.bhabhigameandroid.ui.viewmodels.GameRoomViewModelFactory

@Composable
fun GameRoomScreen(navController: NavController, roomId: String) {
    val gameRoomViewModel: GameRoomViewModel = viewModel(factory = GameRoomViewModelFactory(roomId))

    val roomDetails by gameRoomViewModel.roomDetails.collectAsState()
    val playersInRoom by gameRoomViewModel.playersInRoom.collectAsState()
    val error by gameRoomViewModel.error.collectAsState()
    val isUserHost by gameRoomViewModel.isHost.collectAsState()
    val currentGameState by gameRoomViewModel.gameState.collectAsState()
    val localPlayerId = gameRoomViewModel.localPlayerId
    val friends by gameRoomViewModel.friends.collectAsState()
    val showInviteDialog by gameRoomViewModel.showInviteDialog.collectAsState()
    val isLoading by gameRoomViewModel.isLoading.collectAsState() // Collect isLoading
    val isConnected by gameRoomViewModel.isConnected.collectAsState() // Collect isConnected

    LaunchedEffect(currentGameState) {
        currentGameState?.let {
            val status = try { GameNetStatus.valueOf(it.gameStatus.uppercase()) } catch (e: Exception) { null }
            if (status != null && status != GameNetStatus.WAITING && status != GameNetStatus.INITIALIZING) {
                // Navigate to the actual game play screen for this room
                // The GameScreen itself will need to be refactored to take roomId and use GameEngine with network state
                navController.navigate("game_screen/$roomId") { // Assuming game_screen can take roomId
                    popUpTo("lobby_screen") { inclusive = false } // Example popUpTo logic
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFAF8F0) // Theme background color
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                if (!isConnected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Red.copy(alpha = 0.8f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Disconnected. Attempting to reconnect...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            Text(
                text = "Game Room: ${roomDetails?.roomName ?: roomId}",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF008080), modifier = Modifier.padding(bottom = 16.dp))
            }

            error?.let {
                Text(
                    text = "Error: $it",
                    color = Color.Red,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (!isLoading) { // Only show player list and buttons if not loading initial data
                Text(
                    text = "Players in room:",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Start)
                )

                if (playersInRoom.isEmpty()) {
                    Text("No players yet...", color = Color(0xFF757575), modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // Takes available space
                    ) {
                        items(playersInRoom, key = { player -> player.id }) { player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = player.displayName,
                                    fontSize = 16.sp,
                                    color = Color(0xFF333333)
                                )
                                if (player.id == roomDetails?.hostPlayerId) {
                                    Text(" (Host)", fontSize = 14.sp, color = Color(0xFF008080), fontWeight = FontWeight.SemiBold)
                                }
                                if (player.id == localPlayerId) {
                                    Text(" (You)", fontSize = 14.sp, color = Color(0xFF4682B4), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Divider(color = Color(0xFF4682B4).copy(alpha = 0.2f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }


            // Invite Friends Button
            if (roomDetails != null && playersInRoom.size < (roomDetails?.maxPlayers ?: 4) && (currentGameState?.gameStatus == GameNetStatus.WAITING.name || currentGameState?.gameStatus == GameNetStatus.INITIALIZING.name)) {
                Button(
                    onClick = { gameRoomViewModel.showInviteDialog.value = true },
                    enabled = isConnected && !isLoading, // Disable if not connected or initial loading
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Invite Friends Button
            if (roomDetails != null && playersInRoom.size < (roomDetails?.maxPlayers ?: 4) && (currentGameState?.gameStatus == GameNetStatus.WAITING.name || currentGameState?.gameStatus == GameNetStatus.INITIALIZING.name)) {
                Button(
                    onClick = { gameRoomViewModel.showInviteDialog.value = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00695C)) // Darker Teal
                ) {
                    Text("Invite Friends", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            if (isUserHost) {
                Button(
                    onClick = { gameRoomViewModel.startGame() },
                    enabled = playersInRoom.size >= 2 && (currentGameState?.gameStatus == GameNetStatus.WAITING.name || currentGameState?.gameStatus == GameNetStatus.INITIALIZING.name),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080), // Accent Color 1
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFFD3D3D3),
                        disabledContentColor = Color(0xFFA9A9A9)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Start Game", fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = { navController.popBackStack() }, // Go back to Lobby
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF4682B4), // Accent Color 2
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Leave Room", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
