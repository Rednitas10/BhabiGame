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
import androidx.compose.ui.window.Dialog
import com.example.bhabhigameandroid.FirebaseService
import com.example.bhabhigameandroid.UserFriend

@Composable
fun InviteFriendsDialog(
    friends: List<UserFriend>, // Filtered list of actual friends
    roomId: String,
    roomName: String,
    currentInviterName: String, // Needed for FirebaseService.sendGameInvite
    onInviteSent: (friendId: String) -> Unit,
    onDismiss: () -> Unit,
    firebaseService: FirebaseService // Pass FirebaseService instance
) {
    val (invitedFriendIds, setInvitedFriendIds) = remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFAF8F0), // Light Cream background
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Invite Friends to '$roomName'",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                error?.let {
                    Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }

                if (friends.isEmpty()) {
                    Text("You have no friends to invite yet.", color = Color(0xFF757575))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(friends) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(friend.displayName ?: friend.friendId.take(8), color = Color(0xFF333333))
                                Button(
                                    onClick = {
                                        error = null // Clear previous error
                                        firebaseService.sendGameInvite(
                                            inviteeId = friend.friendId,
                                            inviterName = currentInviterName,
                                            roomId = roomId,
                                            roomName = roomName,
                                            onSuccess = {
                                                setInvitedFriendIds(invitedFriendIds + friend.friendId)
                                                onInviteSent(friend.friendId)
                                            },
                                            onFailure = { e ->
                                                error = "Failed to invite ${friend.displayName ?: friend.friendId}: ${e.message}"
                                            }
                                        )
                                    },
                                    enabled = !invitedFriendIds.contains(friend.friendId),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (invitedFriendIds.contains(friend.friendId)) Color.Gray else Color(0xFF008080),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(if (invitedFriendIds.contains(friend.friendId)) "Invited" else "Invite", fontSize = 12.sp)
                                }
                            }
                            Divider(color = Color(0xFF4682B4).copy(alpha = 0.2f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4682B4))
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
