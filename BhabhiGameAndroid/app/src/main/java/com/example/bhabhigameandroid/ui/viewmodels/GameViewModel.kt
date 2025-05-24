package com.example.bhabhigameandroid.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bhabhigameandroid.Card
import com.example.bhabhigameandroid.FirebaseService
import com.example.bhabhigameandroid.GameEngine
import com.example.bhabhigameandroid.Player
import com.example.bhabhigameandroid.PlayedCardInfo
import com.example.bhabhigameandroid.Suit
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.bhabhigameandroid.GameState as BhabhiGameState // Alias for enum

class GameViewModelFactory(private val roomId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(roomId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class GameViewModel(val roomId: String) : ViewModel() {

    val firebaseService = FirebaseService
    val gameEngine = GameEngine()
    val localPlayerId: String? = firebaseService.getCurrentUserId()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isActionPending = MutableStateFlow(false)
    val isActionPending: StateFlow<Boolean> = _isActionPending.asStateFlow()

    val isConnected: StateFlow<Boolean> = firebaseService.isConnected // Expose connection state

    private var gameStateListener: ListenerRegistration? = null

    // Expose StateFlows from GameEngine
    val players: StateFlow<List<Player>> = gameEngine.players
    val currentPlayerIndex: StateFlow<Int> = gameEngine.currentPlayerIndex
    val currentPlayedCardsInfo: StateFlow<List<PlayedCardInfo>> = gameEngine.currentPlayedCardsInfo
    val gameState: StateFlow<BhabhiGameState> = gameEngine.gameState // Using alias
    val gameMessage: StateFlow<String> = gameEngine.gameMessage
    val discardPile: StateFlow<List<Card>> = gameEngine.discardPile
    val leadSuit: StateFlow<Suit?> = gameEngine.leadSuit // Assuming GameEngine exposes this (it does)
    val selectedCard: StateFlow<Card?> = gameEngine.selectedCard

    // Exposing shoot-out details from GameEngine
    val isBhabhiPlayerId: StateFlow<String?> = gameEngine.isBhabhiPlayerId
    val shootOutDrawingPlayerId: StateFlow<String?> = gameEngine.shootOutDrawingPlayerId
    val shootOutRespondingPlayerId: StateFlow<String?> = gameEngine.shootOutRespondingPlayerId
    val shootOutCardToBeat: StateFlow<Card?> = gameEngine.shootOutCardToBeat


    init {
        gameEngine.firebaseService = this.firebaseService
        listenToCurrentGameState()
    }

    private fun listenToCurrentGameState() {
        if (localPlayerId == null) {
            _error.value = "Local player ID not found, cannot initialize game."
            return
        }
        gameStateListener = firebaseService.listenToGameState(roomId,
            onUpdate = { networkGameState ->
                gameEngine.initializeOrUpdateFromNetwork(networkGameState, localPlayerId, roomId)
            },
            onError = { e ->
                _error.value = "Error fetching game state: ${e.message}"
            }
        )
    }

    fun onCardSelected(card: Card) {
        val localP = players.value.find { it.uid == localPlayerId }
        val currentPIdx = currentPlayerIndex.value

        // Allow selection if the card is in the local player's hand,
        // actual playability is determined when play action is called.
        if (localP != null && localP.hand.contains(card)) {
            if (gameEngine.selectedCard.value == card) {
                gameEngine.deselectCard()
            } else {
                gameEngine.selectCard(card)
            }
        } else {
            // Optionally log or set an error if trying to select a card not in hand
        }
    }

    fun onPlayCardAction() {
        val cardToPlay = gameEngine.selectedCard.value ?: return
        val localP = players.value.find { it.uid == localPlayerId } ?: return
        val playerIndex = players.value.indexOf(localP)
        if (playerIndex != -1) {
            _isActionPending.value = true
            gameEngine.playCard(playerIndex, cardToPlay) // GameEngine will call FirebaseService
            // isActionPending will be reset in GameEngine's callbacks via FirebaseService
            // For this subtask, let's assume GameEngine.playCard itself handles setting isActionPending to false
            // For a more robust solution, GameEngine methods would return a result or use callbacks.
            // Simplified: assume GameEngine's methods that call Firebase will eventually reset action pending.
            // This might be better handled if GameEngine's firebase calls were direct here or used Flows.
            // For now, we can add a temporary reset for UI feedback if GameEngine isn't modified.
            // viewModelScope.launch { delay(2000); _isActionPending.value = false } // Temp reset
        }
    }

    fun onTakeHandFromLeftAction() {
        val localP = players.value.find { it.uid == localPlayerId } ?: return
        val playerIndex = players.value.indexOf(localP)
        if (playerIndex != -1) {
            _isActionPending.value = true
            gameEngine.attemptTakeHandFromLeft(playerIndex)
            // viewModelScope.launch { delay(2000); _isActionPending.value = false } // Temp reset
        }
    }

    fun onShootOutDrawAction() {
        val localP = players.value.find { it.uid == localPlayerId } ?: return
        val playerIndex = players.value.indexOf(localP)
        if (playerIndex != -1) {
            _isActionPending.value = true
            gameEngine.shootOutDrawCard(playerIndex)
            // viewModelScope.launch { delay(2000); _isActionPending.value = false } // Temp reset
        }
    }

    fun onShootOutRespondAction() {
        val cardToPlay = gameEngine.selectedCard.value ?: return
        val localP = players.value.find { it.uid == localPlayerId } ?: return
        val playerIndex = players.value.indexOf(localP)
        if (playerIndex != -1) {
            _isActionPending.value = true
            gameEngine.shootOutRespond(playerIndex, cardToPlay)
            // viewModelScope.launch { delay(2000); _isActionPending.value = false } // Temp reset
        }
    }

    fun onRestartGameAction() {
        _isActionPending.value = true
        firebaseService.signalRestartGame(roomId,
            onSuccess = { msg ->
                _error.value = msg ?: "Restart signaled" // Using error for temp feedback
                _isActionPending.value = false
            },
            onFailure = { e ->
                _error.value = "Error signaling restart: ${e.message}"
                _isActionPending.value = false
            }
        )
    }

    // Called by GameEngine after Firebase operation completes
    fun setActionCompleted() {
        _isActionPending.value = false
    }

    override fun onCleared() {
        super.onCleared()
        gameStateListener?.remove()
        gameEngine.firebaseService = null // Optional cleanup
    }
}
