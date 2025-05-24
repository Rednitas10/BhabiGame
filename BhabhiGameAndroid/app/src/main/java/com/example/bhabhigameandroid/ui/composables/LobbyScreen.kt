package com.example.bhabhigameandroid.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bhabhigameandroid.RoomAccess
import com.example.bhabhigameandroid.ui.viewmodels.LobbyViewModel

@Composable
fun LobbyScreen(navController: NavController, lobbyViewModel: LobbyViewModel = viewModel()) {
    val rooms by lobbyViewModel.rooms.collectAsState()
    val isLoading by lobbyViewModel.isLoading.collectAsState()
    val error by lobbyViewModel.error.collectAsState()
    val isConnected by lobbyViewModel.isConnected.collectAsState() // Collect connection state
    val userDisplayName = lobbyViewModel.userDisplayName // This is a MutableState<String>

    var newRoomName by remember { mutableStateOf("") }

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
                text = "Game Lobby",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = userDisplayName.value,
                onValueChange = { userDisplayName.value = it },
                label = { Text("Your Display Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFF008080),
                    unfocusedBorderColor = Color(0xFF4682B4),
                    textColor = Color(0xFF333333)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newRoomName,
                    onValueChange = { newRoomName = it },
                    label = { Text("New Room Name") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF008080),
                        unfocusedBorderColor = Color(0xFF4682B4),
                        textColor = Color(0xFF333333)
                    )
                )
                Button(
                    onClick = {
                        if (newRoomName.isNotBlank() && userDisplayName.value.isNotBlank()) {
                            lobbyViewModel.createRoom(
                                roomName = newRoomName,
                                maxPlayers = 4, // Default or make configurable
                                access = RoomAccess.PUBLIC.name,
                                onSuccess = { roomId ->
                                    navController.navigate("game_room_screen/$roomId")
                                }
                            )
                            newRoomName = "" // Clear after attempting create
                        }
                    },
                    enabled = newRoomName.isNotBlank() && userDisplayName.value.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080), // Accent Color 1
                        contentColor = Color.White
                    )
                ) {
                    Text("Create", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Reduced spacer

            Button( // Add Friends Button
                onClick = { navController.navigate("friends_screen") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF4682B4) // Accent Color 2
                )
            ) {
                Text("Manage Friends", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game Invites Section
            val gameInvites by lobbyViewModel.gameInvites.collectAsState()
            if (gameInvites.isNotEmpty()) {
                Text(
                    "Game Invites",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333),
                    modifier = Modifier.align(Alignment.Start).padding(top = 16.dp, bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) { // Limit height
                    items(gameInvites) { invite ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = 2.dp,
                            shape = RoundedCornerShape(6.dp),
                            backgroundColor = Color(0xFFE8F5E9) // A slightly different background for invites
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "From: ${invite.inviterName}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF333333)
                                    )
                                    Text(
                                        "Room: ${invite.roomName}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF555555)
                                    )
                                }
                                Row {
                                    Button(
                                        onClick = {
                                            lobbyViewModel.acceptGameInvite(invite.id, invite.roomId) {
                                                navController.navigate("game_room_screen/${invite.roomId}")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                                        modifier = Modifier.padding(end = 8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Accept", color = Color.White, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { lobbyViewModel.declineGameInvite(invite.id) },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Decline", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp)) // Spacer after invites list
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Available Rooms",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333)
                )
                IconButton(onClick = { lobbyViewModel.listenToRooms() }, enabled = !isLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Rooms", tint = Color(0xFF008080))
                }
            }


            if (isLoading && rooms.isEmpty() && error == null) { // Show loading indicator only if rooms are empty initially and no error
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF008080))
                }
            } else { // Show room list or empty message or error
                error?.let {
                    Text(
                        text = it,
                        color = Color.Red,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                if (rooms.isEmpty() && !isLoading && error == null) { // Condition to show "No rooms"
                    Text(
                        "No public rooms available. Create one!",
                        modifier = Modifier.padding(16.dp).weight(1f), // Use weight to center if list is empty
                        color = Color(0xFF757575)
                    )
                } else if (rooms.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f) // Use weight to fill available space
                    ) {
                        items(rooms, key = { room -> room.id }) { room ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                elevation = 4.dp,
                                shape = RoundedCornerShape(8.dp),
                                backgroundColor = Color(0xFFFAF8F0), // Card background
                                border = BorderStroke(1.dp, Color(0xFF4682B4).copy(alpha = 0.5f)) // Calm Blue border
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = room.roomName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF333333)
                                        )
                                        Text(
                                            text = "Host: ${room.hostPlayerId.take(6)}...", // Show part of host ID or ideally host name
                                            fontSize = 12.sp,
                                            color = Color(0xFF757575)
                                        )
                                    }
                                    Text(
                                        text = "${room.currentPlayerCount}/${room.maxPlayers}",
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = Color(0xFF333333)
                                    )
                                    Button(
                                        onClick = {
                                            if (userDisplayName.value.isNotBlank()) {
                                                lobbyViewModel.joinRoom(
                                                    roomId = room.id,
                                                    onSuccess = {
                                                        navController.navigate("game_room_screen/${room.id}")
                                                    }
                                                )
                                            }
                                        },
                                        enabled = userDisplayName.value.isNotBlank() && !isLoading && room.currentPlayerCount < room.maxPlayers && isConnected, // Disable if not connected
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(0xFF4682B4), // Accent Color 2 (Calm Blue)
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Join", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
                ) {
                    items(rooms, key = { room -> room.id }) { room ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            elevation = 4.dp,
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = Color(0xFFFAF8F0), // Card background
                            border = BorderStroke(1.dp, Color(0xFF4682B4).copy(alpha = 0.5f)) // Calm Blue border
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = room.roomName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color(0xFF333333)
                                    )
                                    Text(
                                        text = "Host: ${room.hostPlayerId.take(6)}...", // Show part of host ID or ideally host name
                                        fontSize = 12.sp,
                                        color = Color(0xFF757575)
                                    )
                                }
                                Text(
                                    text = "${room.currentPlayerCount}/${room.maxPlayers}",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color(0xFF333333)
                                )
                                Button(
                                    onClick = {
                                        if (userDisplayName.value.isNotBlank()) {
                                            lobbyViewModel.joinRoom(
                                                roomId = room.id,
                                                onSuccess = {
                                                    navController.navigate("game_room_screen/${room.id}")
                                                }
                                            )
                                        }
                                    },
                                    enabled = userDisplayName.value.isNotBlank() && !isLoading && room.currentPlayerCount < room.maxPlayers,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF4682B4), // Accent Color 2 (Calm Blue)
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Join", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
