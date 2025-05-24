package com.example.bhabhigameandroid.ui.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bhabhigameandroid.FirebaseService
import com.example.bhabhigameandroid.Room
import com.example.bhabhigameandroid.RoomAccess
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LobbyViewModel : ViewModel() {

    val firebaseService = FirebaseService

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val isConnected: StateFlow<Boolean> = firebaseService.isConnected // Expose connection state

    val userDisplayName = mutableStateOf("Player" + (100..999).random()) // Simple default

    private val _gameInvites = MutableStateFlow<List<GameInvite>>(emptyList())
    val gameInvites: StateFlow<List<GameInvite>> = _gameInvites.asStateFlow()

    private var roomsListener: ListenerRegistration? = null
    private var gameInvitesListener: ListenerRegistration? = null
    private val localUserId = firebaseService.getCurrentUserId()


    init {
        listenToRooms()
        if (localUserId != null) {
            listenForGameInvites(localUserId)
        }
    }

    private fun listenForGameInvites(userId: String) {
        gameInvitesListener?.remove()
        gameInvitesListener = firebaseService.listenToGameInvites(userId,
            onUpdate = { invites -> _gameInvites.value = invites },
            onError = { e -> _error.value = "Invites Error: ${e.message}" }
        )
    }

    fun listenToRooms() {
        _isLoading.value = true
        _error.value = null // Clear previous errors
        roomsListener?.remove() // Remove previous listener if any
        roomsListener = firebaseService.listenToPublicRooms(
            onUpdate = { roomList ->
                _rooms.value = roomList
                _isLoading.value = false
            },
            onError = { e ->
                _error.value = e.message ?: "An unknown error occurred while fetching rooms."
                _isLoading.value = false
            }
        )
    }

    fun createRoom(roomName: String, maxPlayers: Int, access: String, onSuccess: (String) -> Unit) {
        val currentUserId = firebaseService.getCurrentUserId()
        if (currentUserId == null) {
            _error.value = "User not authenticated. Cannot create room."
            return
        }
        if (userDisplayName.value.isBlank()) {
            _error.value = "Display name cannot be empty."
            return
        }

        _isLoading.value = true
        _error.value = null

        // Ensure user profile exists before creating a room
        ensureUserProfileExists(currentUserId, userDisplayName.value,
            onProfileReady = {
                firebaseService.createRoom(
                    roomName = roomName,
                    hostDisplayName = userDisplayName.value, // This is already the ensured display name
                    maxPlayers = maxPlayers,
                    access = access,
                    onSuccess = { newRoomId ->
                        _isLoading.value = false
                        onSuccess(newRoomId)
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Failed to create room."
                        _isLoading.value = false
                    }
                )
            },
            onProfileFailure = { e ->
                _error.value = "Failed to ensure user profile: ${e.message}"
                _isLoading.value = false
            }
        )
    }

    fun joinRoom(roomId: String, onSuccess: () -> Unit) {
        val currentUserId = firebaseService.getCurrentUserId()
        if (currentUserId == null) {
            _error.value = "User not authenticated. Cannot join room."
            return
        }
        if (userDisplayName.value.isBlank()) {
            _error.value = "Display name cannot be empty."
            return
        }

        _isLoading.value = true
        _error.value = null

        // Ensure user profile exists before joining a room
        ensureUserProfileExists(currentUserId, userDisplayName.value,
            onProfileReady = {
                firebaseService.joinRoom(
                    roomId = roomId,
                    playerDisplayName = userDisplayName.value, // This is the ensured display name
                    onSuccess = {
                        _isLoading.value = false
                        onSuccess()
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Failed to join room."
                        _isLoading.value = false
                    }
                )
            },
            onProfileFailure = { e ->
                _error.value = "Failed to ensure user profile: ${e.message}"
                _isLoading.value = false
            }
        )
    }

    private fun ensureUserProfileExists(
        userId: String,
        displayName: String,
        onProfileReady: () -> Unit,
        onProfileFailure: (Exception) -> Unit
    ) {
        firebaseService.getUserProfile(userId,
            onSuccess = { profile ->
                if (profile == null || profile.displayName != displayName) {
                    // Profile doesn't exist or display name is different, create/update it
                    firebaseService.createUserProfileDocument(userId, displayName, null, // Assuming no email for anonymous
                        onSuccess = { onProfileReady() },
                        onFailure = { e -> onProfileFailure(e) }
                    )
                } else {
                    // Profile exists and display name is current
                    onProfileReady()
                }
            },
            onFailure = { e -> onProfileFailure(e) }
        )
    }

    fun acceptGameInvite(inviteId: String, roomId: String, onSuccessNavToRoom: () -> Unit) {
        _isLoading.value = true
        _error.value = null
        firebaseService.acceptGameInvite(inviteId,
            onSuccess = {
                // After accepting, attempt to join the room
                joinRoom(roomId, onSuccess = {
                    _isLoading.value = false
                    onSuccessNavToRoom()
                })
                // joinRoom handles its own isLoading and error states,
                // so we don't need to set them again here unless joinRoom fails immediately.
            },
            onFailure = { e ->
                _error.value = "Failed to accept invite: ${e.message}"
                _isLoading.value = false
            }
        )
    }

    fun declineGameInvite(inviteId: String) {
        _isLoading.value = true // Optional: show loading for this action too
        _error.value = null
        firebaseService.declineGameInvite(inviteId,
            onSuccess = {
                _isLoading.value = false
                _error.value = "Invite declined." // Feedback via error state
                // Invites list will update automatically via listener
            },
            onFailure = { e ->
                _error.value = "Failed to decline invite: ${e.message}"
                _isLoading.value = false
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        roomsListener?.remove()
        gameInvitesListener?.remove()
    }
}
