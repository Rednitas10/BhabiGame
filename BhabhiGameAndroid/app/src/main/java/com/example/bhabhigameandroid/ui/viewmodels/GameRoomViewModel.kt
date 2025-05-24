package com.example.bhabhigameandroid.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bhabhigameandroid.FirebaseService
import com.example.bhabhigameandroid.PlayerInRoom
import com.example.bhabhigameandroid.Room
import com.example.bhabhigameandroid.GameStateData
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*

class GameRoomViewModelFactory(private val roomId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameRoomViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameRoomViewModel(roomId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class GameRoomViewModel(val roomId: String) : ViewModel() {

    val firebaseService = FirebaseService

    private val _playersInRoom = MutableStateFlow<List<PlayerInRoom>>(emptyList())
    val playersInRoom: StateFlow<List<PlayerInRoom>> = _playersInRoom.asStateFlow()

    private val _roomDetails = MutableStateFlow<Room?>(null)
    val roomDetails: StateFlow<Room?> = _roomDetails.asStateFlow()

    private val _gameState = MutableStateFlow<GameStateData?>(null)
    val gameState: StateFlow<GameStateData?> = _gameState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val localPlayerId: String? = firebaseService.getCurrentUserId()
    private var localPlayerDisplayName: String? = null // To store current user's display name

    private val _isLoading = MutableStateFlow(true) // Initialize to true for initial load
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isConnected: StateFlow<Boolean> = firebaseService.isConnected // Expose connection state

    val isHost: StateFlow<Boolean> = roomDetails.map {
        it?.hostPlayerId == localPlayerId && localPlayerId != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _friends = MutableStateFlow<List<UserFriend>>(emptyList())
    val friends: StateFlow<List<UserFriend>> = _friends.asStateFlow() // Actual friends only

    val showInviteDialog = MutableStateFlow(false)


    private var playersListener: ListenerRegistration? = null
    private var roomDetailsListener: ListenerRegistration? = null
    private var gameStateListener: ListenerRegistration? = null
    private var friendsListener: ListenerRegistration? = null

    init {
        listenToRoomDetails()
        listenToPlayers()
        listenToCurrentGameState()
        if (localPlayerId != null) {
            fetchLocalPlayerDisplayName(localPlayerId)
            listenToUserFriends(localPlayerId)
        }
    }

    private fun fetchLocalPlayerDisplayName(userId: String) {
        firebaseService.getUserProfile(userId,
            onSuccess = { profile -> localPlayerDisplayName = profile?.displayName },
            onFailure = { e -> _error.value = "Failed to fetch your display name: ${e.message}" }
        )
    }

    private fun listenToUserFriends(userId: String) {
        friendsListener?.remove()
        friendsListener = firebaseService.listenToFriends(userId,
            onUpdate = { allFriendRelations ->
                _friends.value = allFriendRelations.filter { it.status == com.example.bhabhigameandroid.FriendStatus.FRIENDS.name }
            },
            onError = { e -> _error.value = "Friends list error: ${e.message}" }
        )
    }

    private fun listenToRoomDetails() {
        _isLoading.value = true
        roomDetailsListener = firebaseService.listenToRoomDetails(roomId,
            onUpdate = { room ->
                _roomDetails.value = room
                // Consider loading complete when essential data is present
                if (room != null && _playersInRoom.value.isNotEmpty() && _gameState.value != null) {
                    _isLoading.value = false
                }
            },
            onError = { e ->
                _error.value = "Error fetching room details: ${e.message}"
                _isLoading.value = false
            }
        )
    }

    private fun listenToPlayers() {
        playersListener = firebaseService.listenToPlayersInRoom(roomId,
            onUpdate = { players ->
                _playersInRoom.value = players
                if (_roomDetails.value != null && players.isNotEmpty() && _gameState.value != null) {
                    _isLoading.value = false
                }
            },
            onError = { e ->
                _error.value = "Error fetching players: ${e.message}"
                _isLoading.value = false
            }
        )
    }

    private fun listenToCurrentGameState() {
        gameStateListener = firebaseService.listenToGameState(roomId,
            onUpdate = { state ->
                _gameState.value = state
                if (_roomDetails.value != null && _playersInRoom.value.isNotEmpty() && state != null) {
                    _isLoading.value = false
                }
            },
            onError = { e ->
                _error.value = "Error fetching game state: ${e.message}"
                _isLoading.value = false
            }
        )
    }

    fun startGame() {
        if (isHost.value) {
            _isLoading.value = true // Indicate action pending
            firebaseService.signalStartGame(roomId,
                onSuccess = { msg ->
                    _error.value = "Start game signal sent: ${msg ?: "OK"}" // Using error state for temporary feedback
                    _isLoading.value = false
                },
                onFailure = { e ->
                    _error.value = "Error starting game: ${e.message}"
                    _isLoading.value = false
                }
            )
        } else {
            _error.value = "Only the host can start the game."
        }
    }

    override fun onCleared() {
        super.onCleared()
        playersListener?.remove()
        roomDetailsListener?.remove()
        gameStateListener?.remove()
        friendsListener?.remove()
    }
}
