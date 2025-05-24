package com.example.bhabhigameandroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

// Define GameState enum
enum class GameState {
    INITIALIZING,
    DEALING,
    PLAYER_TURN,
    EVALUATING_ROUND,
    GAME_OVER,
    SHOOT_OUT_SETUP, // Preparing for shoot-out
    SHOOT_OUT_DRAWING, // Player A is drawing
    SHOOT_OUT_RESPONDING // Player B is responding
}

// Data class to hold a card and the ID of the player who played it
data class PlayedCardInfo(val card: Card, val playerId: String)

import android.util.Log

class GameEngine : ViewModel() {

    // Core State - now private MutableStateFlows, exposed as StateFlows
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    // _deck is mostly conceptual for network play, server handles deck and dealing.
    // Kept for potential local-only mode or if client needs to display deck related info.
    private val _deck = MutableStateFlow(Deck())
    val deck: StateFlow<Deck>> = _deck.asStateFlow()

    private val _currentPlayerIndex = MutableStateFlow(0)
    val currentPlayerIndex: StateFlow<Int> = _currentPlayerIndex.asStateFlow()

    private val _currentPlayedCardsInfo = MutableStateFlow<List<PlayedCardInfo>>(emptyList())
    val currentPlayedCardsInfo: StateFlow<List<PlayedCardInfo>> = _currentPlayedCardsInfo.asStateFlow()

    private val _discardPile = MutableStateFlow<List<Card>>(emptyList()) // Server might manage this fully
    val discardPile: StateFlow<List<Card>> = _discardPile.asStateFlow()

    private val _gameState = MutableStateFlow(GameState.INITIALIZING) // Enum GameState
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _gameMessage = MutableStateFlow("Waiting for game to start...")
    val gameMessage: StateFlow<String> = _gameMessage.asStateFlow()

    private val _selectedCard = MutableStateFlow<Card?>(null)
    val selectedCard: StateFlow<Card?> = _selectedCard.asStateFlow()

    // For card collection animation: Pair of (List of cards to collect, collecting player's index)
    // This might need adaptation if server directly tells who collected what.
    private val _cardCollectionAnimationInfo = MutableStateFlow<Pair<List<PlayedCardInfo>, Int>?>(null)
    val cardCollectionAnimationInfo: StateFlow<Pair<List<PlayedCardInfo>, Int>?> = _cardCollectionAnimationInfo.asStateFlow()


    // Public StateFlows for other potentially relevant states (add as needed)
    // Example: If leadSuit, Bhabhi status, or ShootOut details are needed by UI directly from GameEngine
    // For now, these are private or managed internally, updated via initializeOrUpdateFromNetwork.
    // If direct UI observation of these from GameEngine (rather than ViewModel constructing them) is desired, expose them.
    // private val _leadSuit = MutableStateFlow<Suit?>(null)
    // val leadSuit: StateFlow<Suit?> = _leadSuit.asStateFlow()
    // Exposing shoot-out details for UI
    private val _isBhabhiPlayerId = MutableStateFlow<String?>(null)
    val isBhabhiPlayerId: StateFlow<String?> = _isBhabhiPlayerId.asStateFlow()

    private val _shootOutCardToBeat = MutableStateFlow<Card?>(null) // Conceptual: actual card server expects to be beaten
    val shootOutCardToBeat: StateFlow<Card?> = _shootOutCardToBeat.asStateFlow()


    // Network play specific properties
    private var localPlayerId: String? = null
    var firebaseService: FirebaseService? = null // Made public
    private var currentRoomId: String? = null // Added currentRoomId

    // Local game logic variables (may become server-driven or less relevant)
    var leadSuit: Suit? = null // Will be derived from networkState.currentPlayedCards or specific event
    private var isFirstRound: Boolean = true // Server will manage this logic
    private var originalStarterOfFirstRoundIndex: Int = 0 // Server will manage this

    // Shoot-Out specific state (will be derived from networkState)
    private var _shootOutDrawingPlayerId: String? = null
    val shootOutDrawingPlayerId: String? get() = _shootOutDrawingPlayerId // Public getter if UI needs it

    private var _shootOutRespondingPlayerId: String? = null
    val shootOutRespondingPlayerId: String? get() = _shootOutRespondingPlayerId // Public getter

    private var _shootOutDrawnCard: Card? = null // This might be part of GameStateData or a specific event
    val shootOutDrawnCard: Card? get() = _shootOutDrawnCard


    init {
        // _deck.value = Deck() // Initializing deck might be server's job
        Log.d("GameEngine", "GameEngine Initialized for Network Play")
    }

    // New method to update local state from network
    fun initializeOrUpdateFromNetwork(networkState: GameStateData, currentLocalPlayerId: String, roomId: String) {
        localPlayerId = currentLocalPlayerId
        this.currentRoomId = roomId // Set currentRoomId
        Log.d("GameEngine", "Initializing from Network. Local Player ID: $localPlayerId, Room ID: $roomId")


        // Update players
        val newPlayers = networkState.playerTurnOrder.mapNotNull { playerUID ->
            val hand = networkState.playerHands[playerUID]?.map { it.toDomainCard() } ?: emptyList()
            val displayName = networkState.playerDisplayNames[playerUID] ?: "Player $playerUID"
            Player(
                id = playerUID, // Using UID as the primary ID now for Player objects
                uid = playerUID,
                name = displayName,
                hand = hand.toMutableList(),
                isLocal = (playerUID == localPlayerId),
                isBot = false, // Assuming no bots in network state for now, or server flags them
                hasLost = networkState.isBhabhiPlayerId == playerUID || (networkState.playersWhoLost.contains(playerUID)),
                isBhabhi = networkState.isBhabhiPlayerId == playerUID
            )
        }
        _players.value = newPlayers

        // Update current player index
        val currentNetworkPlayerId = networkState.playerTurnOrder.getOrNull(networkState.currentPlayerIndex)
        _currentPlayerIndex.value = newPlayers.indexOfFirst { it.uid == currentNetworkPlayerId }.coerceAtLeast(0)


        // Update current played cards
        _currentPlayedCardsInfo.value = networkState.currentPlayedCards.map { it.toDomainPlayedCardInfo() }

        // Update game message
        _gameMessage.value = networkState.gameMessage

        // Update game state enum
        try {
            _gameState.value = GameState.valueOf(networkState.gameStatus.uppercase())
        } catch (e: IllegalArgumentException) {
            Log.e("GameEngine", "Received unknown game status from network: ${networkState.gameStatus}")
            _gameState.value = GameState.INITIALIZING // Fallback state
        }

        // Update discard pile (if available and relevant for client)
        _discardPile.value = networkState.discardPile.map { it.toDomainCard() }

        _isBhabhiPlayerId.value = networkState.isBhabhiPlayerId
        // Conceptual: If server provides the card to beat in shoot-out directly
        // _shootOutCardToBeat.value = networkState.shootOutCardToBeat?.toDomainCard()

        // Update lead suit based on current played cards (if any)
        leadSuit = _currentPlayedCardsInfo.value.firstOrNull()?.card?.suit

        Log.d("GameEngine", "State updated. Players: ${newPlayers.map { it.name + "("+it.hand.size+")" } }. Current Player: ${newPlayers.getOrNull(_currentPlayerIndex.value)?.name}. Status: ${_gameState.value}")
    }

    fun selectCard(card: Card) {
        // Allow selecting card even if it's not strictly "your turn" yet,
        // as player might be pre-selecting. Actual play validation is in playCard.
        if (_players.value.find { it.isLocal }?.hand?.contains(card) == true) {
            _selectedCard.value = card
        } else {
            Log.w("GameEngine", "selectCard: Card $card not in local player's hand.")
        }
    }

    fun deselectCard() {
        _selectedCard.value = null
    }


    // Old setupGame - comment out or adapt for local-only mode
    /*
    fun setupGame(playerNames: List<String>) {
        if (playerNames.size < 2) {
            _gameMessage.value = "Cannot start game with less than 2 players."
            _gameState.value = GameState.INITIALIZING
            return
        }
        _gameMessage.value = "Setting up new game with ${playerNames.joinToString()}..."
        // Player at index 0 is human, others are bots (e.g., for 3 players: P0=Human, P1=Bot, P2=Bot)
        val newPlayers = playerNames.mapIndexed { index, name ->
            Player(name = name, isBot = index > 0, isLocal = index == 0) // Assuming player 0 is local for local games
        }
        _players.value = newPlayers
        _deck.value = Deck()
        _deck.value.shuffle()
        _currentPlayerIndex.value = 0
        _currentPlayedCardsInfo.value = emptyList()
        _discardPile.value = emptyList()
        leadSuit = null
        isFirstRound = true
        _shootOutDrawingPlayerId = null
        _shootOutRespondingPlayerId = null
        _shootOutDrawnCard = null

        _gameState.value = GameState.DEALING
        dealCards() // This would also need to be local-only

        var startingPlayerFound = false
        for ((index, player) in _players.value.withIndex()) {
            if (player.hand.any { it.rank == Rank.ACE && it.suit == Suit.SPADES }) {
                _currentPlayerIndex.value = index
                originalStarterOfFirstRoundIndex = index
                startingPlayerFound = true
                break
            }
        }

        if (!startingPlayerFound) {
            _currentPlayerIndex.value = 0
            originalStarterOfFirstRoundIndex = 0
            _gameMessage.value = "Ace of Spades not found. ${_players.value.getOrNull(0)?.name ?: "Player 1"} starts."
        } else {
            _gameMessage.value = "${_players.value[_currentPlayerIndex.value].name} has the Ace of Spades and starts the game."
        }
        _gameState.value = GameState.PLAYER_TURN
        // autoPlayBotIfNeeded() // Bot logic might be server-side or different for local
    }
    */

    // Old dealCards - Keep for local mode or remove if server handles all dealing
    /*
    private fun dealCards() {
        val currentPlayers = _players.value.toMutableList()
        if (currentPlayers.isEmpty()) return
        currentPlayers.forEach { it.hand.clear(); it.isBhabhi = false; it.hasLost = false }

        val currentDeck = _deck.value
        if (!currentDeck.deal(currentPlayers)) {
            _gameMessage.value = "Dealing failed."
            return
        }
        _players.value = currentPlayers
        _gameMessage.value = "Cards have been dealt."
    }
    */

    fun playCard(playerIndex: Int, card: Card) {
        val player = _players.value.getOrNull(playerIndex)
        if (player == null) {
            _gameMessage.value = "Invalid player index for playCard."
            Log.w("GameEngine", "playCard: Invalid player index $playerIndex")
            return
        }

        if (player.isLocal) {
            Log.d("GameEngine", "Local player ${player.name} (UID: ${player.uid}) attempting to play card: $card")
            // Basic local validation
            if (_gameState.value != GameState.PLAYER_TURN) {
                _gameMessage.value = "Cannot play card now. Game State: ${_gameState.value}"
                Log.w("GameEngine", "Local playCard: Not player's turn or wrong game state.")
                return
            }
            if (player.uid != _players.value.getOrNull(_currentPlayerIndex.value)?.uid) {
                 _gameMessage.value = "It's not your turn!"
                 Log.w("GameEngine", "Local playCard: Not local player's turn by index/UID.")
                 return
            }
            if (!player.hand.contains(card)) {
                _gameMessage.value = "You do not have that card!"
                Log.w("GameEngine", "Local playCard: Player does not have card $card.")
                return
            }
            // Lead suit validation (optional, server will validate anyway)
            if (_currentPlayedCardsInfo.value.isNotEmpty() && leadSuit != null && card.suit != leadSuit && player.hand.any { it.suit == leadSuit }) {
                _gameMessage.value = "You must play the lead suit ($leadSuit) if you have it."
                Log.w("GameEngine", "Local playCard: Must follow lead suit.")
                return
            }

            if (currentRoomId == null) {
                _gameMessage.value = "Error: Not in a room."
                Log.e("GameEngine", "playCard: currentRoomId is null.")
                return
            }
            if (firebaseService == null) {
                _gameMessage.value = "Error: Network service not available."
                Log.e("GameEngine", "playCard: firebaseService is null.")
                return
            }

            _gameMessage.value = "Playing ${card.rank} of ${card.suit}... Waiting for server..."
            firebaseService?.signalPlayCard(currentRoomId!!, card.toCardNet(),
                onSuccess = { successMessage ->
                    _gameMessage.value = successMessage ?: "Card played successfully. Awaiting update."
                    Log.i("GameEngine", "signalPlayCard success: $successMessage")
                    _selectedCard.value = null // Deselect card after successful signal
                },
                onFailure = { exception ->
                    _gameMessage.value = "Error playing card: ${exception.message}"
                    Log.e("GameEngine", "signalPlayCard failure", exception)
                }
            )

            // Comment out direct state manipulation:
            /*
            if (_currentPlayedCardsInfo.value.isEmpty()) {
                leadSuit = card.suit
            }
            val mutableHand = player.hand.toMutableList()
            mutableHand.remove(card)
            updatePlayerHand(player, mutableHand) // This should not happen locally first
            _currentPlayedCardsInfo.update { it + PlayedCardInfo(card.copy(isPlayed = true), player.id) }
            determineNextPlayerOrEvaluate() // Server will drive this
            */
        } else {
            Log.w("GameEngine", "playCard called for remote player ${player.name} (UID: ${player.uid}). This should not happen directly.")
            // This case should ideally not be triggered if UI is correctly disabled for remote players.
        }
    }

    // This function becomes mostly obsolete as server drives turns.
    // Bot logic will also be server-side.
    /*
    private fun determineNextPlayerOrEvaluate() {
        val activePlayersStillInGame = _players.value.filterNot { it.hasLost }
        // The following logic is now server-driven and reflected via initializeOrUpdateFromNetwork
    }
    */


    // evaluateRound, proceedToNextTurnOrEndGame, checkForBhabhi, updatePlayerBhabhiStatus, resetGame
    // are all heavily dependent on local game logic.
    // They will be significantly simplified or removed as the server dictates game progression and state.
    // For now, commenting out large portions.

    /*
    private fun evaluateRound() {
        // Server will evaluate the round and send updated GameStateData
        Log.d("GameEngine", "evaluateRound() called - this logic is now server-driven.")
    }

    private fun proceedToNextTurnOrEndGame() {
        // Server will determine next player and game end conditions
        Log.d("GameEngine", "proceedToNextTurnOrEndGame() called - this logic is now server-driven.")
    }

    private fun checkForBhabhi() {
        // Server will determine Bhabhi and game over state
        Log.d("GameEngine", "checkForBhabhi() called - this logic is now server-driven.")
    }

    private fun updatePlayerBhabhiStatus(playerId: String, isBhabhi: Boolean) {
        // This state change will come from the server
        Log.d("GameEngine", "updatePlayerBhabhiStatus for $playerId called - this logic is now server-driven.")
    }
    */
    fun resetGame() { // This would likely become a call to Firebase to signal intent to restart or leave.
        Log.i("GameEngine", "resetGame() called. For network play, this would typically involve server interaction.")
        _gameMessage.value = "Resetting game... (Networked game would require server action)"
        // For local testing, one might re-initialize to a default local state
        // _players.value = emptyList()
        // _currentPlayerIndex.value = 0
        // _currentPlayedCardsInfo.value = emptyList()
        // _gameState.value = GameState.INITIALIZING
    }


    // updatePlayerHand is a local helper, might be removed if Player objects are always reconstructed from network state.
    // If used, it should only reflect server-confirmed changes.
    /*
    private fun updatePlayerHand(player: Player, newHand: List<Card>) {
        _players.update { list ->
            list.map {
                if (it.uid == player.uid) it.copy(hand = newHand.sorted().toMutableList()) else it
            }
        }
    }
    */

    // getPlayerById should now use UID
    private fun getPlayerByUid(uid: String): Player? = _players.value.find { it.uid == uid }

    fun attemptTakeHandFromLeft(playerIndex: Int) {
        val player = _players.value.getOrNull(playerIndex)
        if (player == null || !player.isLocal) {
            Log.w("GameEngine", "attemptTakeHandFromLeft: Invalid action for non-local or null player.")
            _gameMessage.value = "Invalid action."
            return
        }

        if (currentRoomId == null) {
            _gameMessage.value = "Error: Not in a room."
            Log.e("GameEngine", "attemptTakeHandFromLeft: currentRoomId is null.")
            return
        }
        if (firebaseService == null) {
            _gameMessage.value = "Error: Network service not available."
            Log.e("GameEngine", "attemptTakeHandFromLeft: firebaseService is null.")
            return
        }

        _gameMessage.value = "Attempting to take hand from left... (Waiting for server)"
        firebaseService?.signalTakeHandFromLeft(currentRoomId!!, player.uid,
            onSuccess = { successMessage ->
                _gameMessage.value = successMessage ?: "Take hand action sent. Awaiting update."
                Log.i("GameEngine", "signalTakeHandFromLeft success: $successMessage")
            },
            onFailure = { exception ->
                _gameMessage.value = "Error taking hand: ${exception.message}"
                Log.e("GameEngine", "signalTakeHandFromLeft failure", exception)
            }
        )
        // Comment out direct state manipulation:
        /*
        if (_gameState.value == GameState.EVALUATING_ROUND || !_currentPlayedCardsInfo.value.isEmpty()) {
            _gameMessage.value = "SR2: Cannot take hand mid-trick. Wait for trick to finish."
            return
        }
        // ... rest of the original validation and logic ...
        _players.update { list -> ... }
        _gameMessage.value = "${takingPlayer.name} took ${leftPlayerOriginalHandSize} cards from ${leftPlayer.name}. ${leftPlayer.name} is out of the game!"
        _currentPlayerIndex.value = playerIndex
        checkForBhabhi()
        if (_gameState.value != GameState.GAME_OVER) {
            _gameState.value = GameState.PLAYER_TURN
            // autoPlayBotIfNeeded() // Bot logic is server-side
        }
        */
    }

    // Bot AI Logic (findPlayForBot, autoPlayBotIfNeeded) will be server-side.
    // Client GameEngine should not contain bot decision-making for network play.
    /*
    private fun findPlayForBot(botPlayer: Player): Card? { ... }
    private fun autoPlayBotIfNeeded() { ... }
    */

    // Shoot-Out (SR4B) Implementation
    // These actions will also be initiated by local player and sent to server.
    // Server will manage shoot-out state and outcomes.

    /*
    private fun initiateShootOut(playerAId: String, playerBId: String) {
         Log.d("GameEngine", "initiateShootOut() called - this logic is now server-driven.")
    }
    */

    fun shootOutDrawCard(playerIndex: Int) { // Changed parameter to playerIndex for consistency
        val player = _players.value.getOrNull(playerIndex)
        if (player == null || !player.isLocal || _gameState.value != GameState.SHOOT_OUT_DRAWING || player.uid != _shootOutDrawingPlayerId) {
            Log.w("GameEngine", "shootOutDrawCard: Invalid action. Player: ${player?.uid}, Local: ${player?.isLocal}, State: ${_gameState.value}, DrawingID: ${_shootOutDrawingPlayerId}")
            _gameMessage.value = "Cannot draw card for shootout now."
            return
        }

        if (currentRoomId == null) {
            _gameMessage.value = "Error: Not in a room."
            Log.e("GameEngine", "shootOutDrawCard: currentRoomId is null.")
            return
        }
        if (firebaseService == null) {
            _gameMessage.value = "Error: Network service not available."
            Log.e("GameEngine", "shootOutDrawCard: firebaseService is null.")
            return
        }

        _gameMessage.value = "Drawing card for Shoot-Out... (Waiting for server)"
        firebaseService?.signalShootOutDraw(currentRoomId!!, player.uid,
            onSuccess = { successMessage ->
                _gameMessage.value = successMessage ?: "Shoot-Out draw action sent. Awaiting update."
                Log.i("GameEngine", "signalShootOutDraw success: $successMessage")
            },
            onFailure = { exception ->
                _gameMessage.value = "Error drawing for Shoot-Out: ${exception.message}"
                Log.e("GameEngine", "signalShootOutDraw failure", exception)
            }
        )
        // Comment out direct state manipulation:
        /*
        // ... original logic ...
        updatePlayerHand(drawingPlayer, (drawingPlayer.hand + shootOutDrawnCard!!).toMutableList())
        _gameMessage.value = "${drawingPlayer.name} drew ${shootOutDrawnCard!!.rank} of ${shootOutDrawnCard!!.suit}. ${getPlayerById(shootOutRespondingPlayerId!!)?.name} to respond."
        _gameState.value = GameState.SHOOT_OUT_RESPONDING
        _currentPlayerIndex.value = _players.value.indexOfFirst { it.id == shootOutRespondingPlayerId }
        // autoPlayBotIfNeeded() // Server side
        */
    }

    fun shootOutRespond(playerIndex: Int, card: Card) { // Changed parameter to playerIndex for consistency
        val player = _players.value.getOrNull(playerIndex)
         if (player == null || !player.isLocal || _gameState.value != GameState.SHOOT_OUT_RESPONDING || player.uid != _shootOutRespondingPlayerId) {
            Log.w("GameEngine", "shootOutRespond: Invalid action. Player: ${player?.uid}, Local: ${player?.isLocal}, State: ${_gameState.value}, RespondingID: ${_shootOutRespondingPlayerId}")
            _gameMessage.value = "Cannot respond to Shoot-Out now."
            return
        }
        if (!player.hand.contains(card)) {
            _gameMessage.value = "You do not have that card for Shoot-Out!"
            return
        }

        if (currentRoomId == null) {
            _gameMessage.value = "Error: Not in a room."
            Log.e("GameEngine", "shootOutRespond: currentRoomId is null.")
            return
        }
        if (firebaseService == null) {
            _gameMessage.value = "Error: Network service not available."
            Log.e("GameEngine", "shootOutRespond: firebaseService is null.")
            return
        }

        _gameMessage.value = "Responding to Shoot-Out with ${card.rank} of ${card.suit}... (Waiting for server)"
        firebaseService?.signalShootOutRespond(currentRoomId!!, player.uid, card.toCardNet(),
            onSuccess = { successMessage ->
                _gameMessage.value = successMessage ?: "Shoot-Out response sent. Awaiting update."
                Log.i("GameEngine", "signalShootOutRespond success: $successMessage")
            },
            onFailure = { exception ->
                _gameMessage.value = "Error responding to Shoot-Out: ${exception.message}"
                Log.e("GameEngine", "signalShootOutRespond failure", exception)
                // Do not deselect card on failure, user might want to retry or select a different card.
            }
        )
        // Comment out direct state manipulation:
        /*
        // ... original logic ...
        updatePlayerHand(respondingPlayer, mutableResponderHand)
        // ... more state changes ...
        checkForBhabhi()
        */
    }
}
