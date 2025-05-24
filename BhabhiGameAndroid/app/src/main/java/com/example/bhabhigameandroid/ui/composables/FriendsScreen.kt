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
import com.example.bhabhigameandroid.FriendStatus
import com.example.bhabhigameandroid.UserFriend
import com.example.bhabhigameandroid.UserProfile
import com.example.bhabhigameandroid.ui.viewmodels.FriendsViewModel
import com.example.bhabhigameandroid.ui.theme.BhabhiGameAndroidTheme

@Composable
fun FriendsScreen(navController: NavController, friendsViewModel: FriendsViewModel = viewModel()) {
    val friendsList by friendsViewModel.friendsList.collectAsState()
    val searchResults by friendsViewModel.searchResults.collectAsState()
    val error by friendsViewModel.error.collectAsState()
    val isConnected by friendsViewModel.isConnected.collectAsState() // Collect connection state
    var searchQuery by remember { mutableStateOf("") }

    val localPlayerId = friendsViewModel.localPlayerId

    BhabhiGameAndroidTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAF8F0) // Theme background
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
                    "Friends & Social",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Search Section
                Text("Find Friends", style = MaterialTheme.typography.h6, color = Color(0xFF333333), modifier = Modifier.align(Alignment.Start))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search by exact display name") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFF008080),
                            unfocusedBorderColor = Color(0xFF4682B4),
                            textColor = Color(0xFF333333)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { friendsViewModel.searchUsers(searchQuery) },
                           colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF008080))) {
                        Text("Search", color = Color.White)
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) { // Limit height of search results
                    items(searchResults) { userProfile ->
                        val existingRelation = friendsList.find { it.friendId == userProfile.uid }
                        FriendSearchResultItem(
                            userProfile = userProfile,
                            existingRelation = existingRelation,
                            onSendRequest = { friendsViewModel.sendFriendRequest(userProfile) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Display
                error?.let {
                    Text(
                        text = "Status: $it", // Changed "Error" to "Status" as it's used for feedback too
                        color = if (it.startsWith("Failed") || it.startsWith("Error")) Color.Red else Color(0xFF333333),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Sections for different friend statuses
                FriendListSection(
                    title = "Friend Requests Received",
                    list = friendsList.filter { it.status == FriendStatus.PENDING_RECEIVED.name },
                    localPlayerId = localPlayerId,
                    onAccept = { friendId -> friendsViewModel.acceptFriendRequest(friendId) },
                    onDecline = { friendId -> friendsViewModel.declineOrCancelRequest(friendId) },
                    actionLabelAccept = "Accept",
                    actionLabelDecline = "Decline"
                )

                FriendListSection(
                    title = "Your Friends",
                    list = friendsList.filter { it.status == FriendStatus.FRIENDS.name },
                    localPlayerId = localPlayerId,
                    onRemove = { friendId -> friendsViewModel.declineOrCancelRequest(friendId) },
                    actionLabelRemove = "Remove"
                )

                FriendListSection(
                    title = "Friend Requests Sent",
                    list = friendsList.filter { it.status == FriendStatus.PENDING_SENT.name },
                    localPlayerId = localPlayerId,
                    onCancel = { friendId -> friendsViewModel.declineOrCancelRequest(friendId) },
                    actionLabelCancel = "Cancel"
                )

                Spacer(modifier = Modifier.weight(1f)) // Push button to bottom

                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4682B4))
                ) {
                    Text("Back to Lobby", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun FriendSearchResultItem(
    userProfile: UserProfile,
    existingRelation: UserFriend?,
    onSendRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(userProfile.displayName, color = Color(0xFF333333))
        if (existingRelation == null) {
            Button(onClick = onSendRequest,
                   colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF008080))) {
                Text("Send Request", color = Color.White, fontSize = 12.sp)
            }
        } else {
            Text(
                text = when (existingRelation.status) {
                    FriendStatus.FRIENDS.name -> "Friends"
                    FriendStatus.PENDING_SENT.name -> "Request Sent"
                    FriendStatus.PENDING_RECEIVED.name -> "Request Received"
                    else -> existingRelation.status
                },
                fontSize = 12.sp,
                color = Color(0xFF757575)
            )
        }
    }
    Divider()
}

@Composable
fun FriendListSection(
    title: String,
    list: List<UserFriend>,
    localPlayerId: String?,
    onAccept: ((String) -> Unit)? = null,
    onDecline: ((String) -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null,
    onCancel: ((String) -> Unit)? = null,
    actionLabelAccept: String = "",
    actionLabelDecline: String = "",
    actionLabelRemove: String = "",
    actionLabelCancel: String = ""
) {
    if (list.isNotEmpty()) {
        Text(title, style = MaterialTheme.typography.h6, color = Color(0xFF333333), modifier = Modifier.align(Alignment.Start).padding(top = 16.dp, bottom = 8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) { // Limit height
            items(list) { friend ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(friend.displayName ?: friend.friendId.take(8) + "...", color = Color(0xFF333333)) // Show part of ID if name is null
                    Row {
                        onAccept?.let {
                            Button(onClick = { it(friend.friendId) },
                                   colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                                   modifier = Modifier.padding(end = 4.dp)) {
                                Text(actionLabelAccept, color = Color.White, fontSize = 12.sp)
                            }
                        }
                        onDecline?.let {
                            Button(onClick = { it(friend.friendId) },
                                   colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                                   modifier = Modifier.padding(end = 4.dp)) {
                                Text(actionLabelDecline, color = Color.White, fontSize = 12.sp)
                            }
                        }
                        onRemove?.let {
                            Button(onClick = { it(friend.friendId) },
                                   colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))) {
                                Text(actionLabelRemove, color = Color.White, fontSize = 12.sp)
                            }
                        }
                        onCancel?.let {
                            Button(onClick = { it(friend.friendId) },
                                   colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF757575))) {
                                Text(actionLabelCancel, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Divider()
            }
        }
    }
}
