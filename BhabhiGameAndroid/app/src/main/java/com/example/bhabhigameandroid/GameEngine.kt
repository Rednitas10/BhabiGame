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

class GameEngine : ViewModel() {

    // Properties using StateFlow for UI updates
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private val _deck = MutableStateFlow(Deck())
    val deck: StateFlow<Deck> = _deck.asStateFlow()

    private val _currentPlayerIndex = MutableStateFlow(0) // General current player
    val currentPlayerIndex: StateFlow<Int> = _currentPlayerIndex.asStateFlow()

    private val _currentPlayedCardsInfo = MutableStateFlow<List<PlayedCardInfo>>(emptyList())
    val currentPlayedCardsInfo: StateFlow<List<PlayedCardInfo>> = _currentPlayedCardsInfo.asStateFlow()

    private val _discardPile = MutableStateFlow<List<Card>>(emptyList())
    val discardPile: StateFlow<List<Card>> = _discardPile.asStateFlow()

    private val _gameState = MutableStateFlow(GameState.INITIALIZING)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _gameMessage = MutableStateFlow("Welcome to Bhabhi!")
    val gameMessage: StateFlow<String> = _gameMessage.asStateFlow()

    var leadSuit: Suit? = null
    private var isFirstRound: Boolean = true
    private var originalStarterOfFirstRoundIndex: Int = 0

    // Shoot-Out specific state
    var shootOutDrawingPlayerId: String? = null // Made public for UI check
    var shootOutRespondingPlayerId: String? = null // Made public for UI check
    private var shootOutDrawnCard: Card? = null


    init {
        _deck.value = Deck()
    }

    fun setupGame(playerNames: List<String>) {
        if (playerNames.size < 2) {
            _gameMessage.value = "Cannot start game with less than 2 players."
            _gameState.value = GameState.INITIALIZING
            return
        }
        _gameMessage.value = "Setting up new game with ${playerNames.joinToString()}..."
        // Player at index 0 is human, others are bots (e.g., for 3 players: P0=Human, P1=Bot, P2=Bot)
        val newPlayers = playerNames.mapIndexed { index, name ->
            Player(name = name, isBot = index > 0)
        }
        _players.value = newPlayers
        _deck.value = Deck()
        _deck.value.shuffle()
        _currentPlayerIndex.value = 0
        _currentPlayedCardsInfo.value = emptyList()
        _discardPile.value = emptyList()
        leadSuit = null
        isFirstRound = true
        shootOutDrawingPlayerId = null
        shootOutRespondingPlayerId = null
        shootOutDrawnCard = null


        _gameState.value = GameState.DEALING
        dealCards()

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
        autoPlayBotIfNeeded() // Check if bot starts
    }

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

    fun playCard(playerIndex: Int, card: Card) {
        if (_gameState.value != GameState.PLAYER_TURN) {
            _gameMessage.value = "Cannot play card now. State: ${_gameState.value}"
            return
        }
        if (playerIndex != _currentPlayerIndex.value) {
            _gameMessage.value = "It's not ${_players.value.getOrNull(playerIndex)?.name ?: "that player"}'s turn!"
            return
        }

        val player = _players.value[playerIndex]
        if (!player.hand.contains(card)) {
            _gameMessage.value = "${player.name} does not have ${card.rank} of ${card.suit}!"
            return
        }

        if (_currentPlayedCardsInfo.value.isEmpty()) {
            leadSuit = card.suit
            _gameMessage.value = "${player.name} played ${card.rank} of ${card.suit}. Lead suit is $leadSuit."
        } else {
            if (card.suit != leadSuit) {
                if (player.hand.any { it.suit == leadSuit }) {
                    _gameMessage.value = "${player.name}, you must play $leadSuit if you have it."
                    return
                }
                _gameMessage.value = "${player.name} played ${card.rank} of ${card.suit} (out of suit)."
            } else {
                _gameMessage.value = "${player.name} played ${card.rank} of ${card.suit}."
            }
        }

        val mutableHand = player.hand.toMutableList()
        mutableHand.remove(card)
        updatePlayerHand(player, mutableHand)

        _currentPlayedCardsInfo.update { it + PlayedCardInfo(card.copy(isPlayed = true), player.id) }

        if (mutableHand.isEmpty()) {
            _gameMessage.value = "${player.name} has played all their cards!"
        }
        determineNextPlayerOrEvaluate()
    }

    private fun determineNextPlayerOrEvaluate() {
        val activePlayersStillInGame = _players.value.filterNot { it.hasLost }
        val playersWithCards = activePlayersStillInGame.filter { it.hand.isNotEmpty() }

        val playerWhoJustPlayedId = _currentPlayedCardsInfo.value.lastOrNull()?.playerId
        val playerWhoJustPlayed = getPlayerById(playerWhoJustPlayedId ?: "")
        if (playerWhoJustPlayed != null && playerWhoJustPlayed.hand.isEmpty() && playersWithCards.size == 1 && activePlayersStillInGame.size == 2) {
            val otherPlayer = playersWithCards.first()
            initiateShootOut(playerWhoJustPlayed.id, otherPlayer.id)
            return
        }

        if (playersWithCards.size <= 1 && activePlayersStillInGame.size > 1 && _gameState.value != GameState.SHOOT_OUT_SETUP) {
             evaluateRound()
            return
        }

        val outOfSuitPlayed = _currentPlayedCardsInfo.value.any { it.card.suit != leadSuit && leadSuit != null }
        val trickSize = _currentPlayedCardsInfo.value.size
        val expectedPlayersInTrick = activePlayersStillInGame.count { p -> p.hand.isNotEmpty() || _currentPlayedCardsInfo.value.any { pci -> pci.playerId == p.id } }

        if (outOfSuitPlayed || trickSize == expectedPlayersInTrick || playersWithCards.isEmpty()) {
             evaluateRound()
        } else {
            var nextPlayerIndex = (_currentPlayerIndex.value + 1) % _players.value.size
            var attempts = 0
            while ((_players.value[nextPlayerIndex].hand.isEmpty() || _players.value[nextPlayerIndex].hasLost) && attempts < _players.value.size) {
                nextPlayerIndex = (nextPlayerIndex + 1) % _players.value.size
                attempts++
            }

            if ((_players.value[nextPlayerIndex].hand.isNotEmpty() && !_players.value[nextPlayerIndex].hasLost) || (attempts == _players.value.size && playersWithCards.isNotEmpty()) ) {
                 if (_players.value[nextPlayerIndex].hand.isEmpty() && playersWithCards.isNotEmpty() && !_players.value[nextPlayerIndex].hasLost) { // Found a player but they have no cards (shouldn't happen if logic is right)
                    evaluateRound() // Re-evaluate if we couldn't find a suitable next player with cards
                    return
                }
                _currentPlayerIndex.value = nextPlayerIndex
                _gameState.value = GameState.PLAYER_TURN
                _gameMessage.value = "It's ${_players.value[_currentPlayerIndex.value].name}'s turn. Lead suit: ${leadSuit ?: "None"}."
                autoPlayBotIfNeeded()
            } else {
                 evaluateRound()
            }
        }
    }

    private fun evaluateRound() {
        if (_gameState.value == GameState.SHOOT_OUT_SETUP || _gameState.value == GameState.SHOOT_OUT_DRAWING || _gameState.value == GameState.SHOOT_OUT_RESPONDING) {
            return
        }
        _gameState.value = GameState.EVALUATING_ROUND
        if (_currentPlayedCardsInfo.value.isEmpty()) {
            checkForBhabhi()
            if (_gameState.value == GameState.PLAYER_TURN) {
                val playerWhoShouldLead = _players.value[_currentPlayerIndex.value]
                if (playerWhoShouldLead.hand.isEmpty() && !playerWhoShouldLead.hasLost) {
                    _gameMessage.value = "${playerWhoShouldLead.name} has no cards to lead (SR4)."
                    var nextLeadPlayerIndex = (_currentPlayerIndex.value + 1) % _players.value.size
                    var attempts = 0
                    while ((_players.value[nextLeadPlayerIndex].hand.isEmpty() || _players.value[nextLeadPlayerIndex].hasLost) && attempts < _players.value.size) {
                        nextLeadPlayerIndex = (nextLeadPlayerIndex + 1) % _players.value.size
                        attempts++
                    }
                     if (_players.value[nextLeadPlayerIndex].hand.isNotEmpty() && !_players.value[nextLeadPlayerIndex].hasLost) {
                        _currentPlayerIndex.value = nextLeadPlayerIndex
                        _gameMessage.value += " ${_players.value[_currentPlayerIndex.value].name} (to the left) starts."
                        autoPlayBotIfNeeded()
                    }
                } else {
                    autoPlayBotIfNeeded() // Current leader might be a bot
                }
            }
            return
        }

        val playedCardsInfo = _currentPlayedCardsInfo.value.toList()
        val playedCards = playedCardsInfo.map { it.card }
        val outOfSuitCardPlayedInfo = playedCardsInfo.firstOrNull { it.card.suit != leadSuit && leadSuit != null }

        if (outOfSuitCardPlayedInfo != null) {
            if (isFirstRound) {
                _gameMessage.value = "SR1: Out of suit in first round (${outOfSuitCardPlayedInfo.card.suit} by ${getPlayerById(outOfSuitCardPlayedInfo.playerId)?.name}). Cards go to discard pile."
                _discardPile.update { it + playedCards }
                _currentPlayerIndex.value = originalStarterOfFirstRoundIndex
                _gameMessage.value += " ${_players.value[_currentPlayerIndex.value].name} leads again."
                isFirstRound = false
            } else {
                val highestLeadSuitPlayedInfo = playedCardsInfo.filter { it.card.suit == leadSuit }
                                                             .maxByOrNull { it.card.rank.value }
                if (highestLeadSuitPlayedInfo != null) {
                    val pickerPlayerId = highestLeadSuitPlayedInfo.playerId
                    val picker = getPlayerById(pickerPlayerId)!!
                    _gameMessage.value = "${picker.name} played highest of lead suit (${highestLeadSuitPlayedInfo.card.rank} of ${highestLeadSuitPlayedInfo.card.suit}) and picks up the pile due to out-of-suit play by ${getPlayerById(outOfSuitCardPlayedInfo.playerId)?.name}."
                    val mutablePickerHand = picker.hand.toMutableList()
                    mutablePickerHand.addAll(playedCards.map { it.copy(isPlayed = false) })
                    updatePlayerHand(picker, mutablePickerHand)
                    _currentPlayerIndex.value = _players.value.indexOfFirst { it.id == pickerPlayerId }
                } else { // Should only happen if no lead suit cards were played before breaking suit (e.g. first player breaks)
                    _gameMessage.value = "No lead suit card played before ${getPlayerById(outOfSuitCardPlayedInfo.playerId)?.name} broke suit. ${getPlayerById(outOfSuitCardPlayedInfo.playerId)?.name} picks up."
                    val pickerPlayerId = outOfSuitCardPlayedInfo.playerId
                    val picker = getPlayerById(pickerPlayerId)!!
                    val mutablePickerHand = picker.hand.toMutableList()
                    mutablePickerHand.addAll(playedCards.map { it.copy(isPlayed = false) })
                    updatePlayerHand(picker, mutablePickerHand)
                    _currentPlayerIndex.value = _players.value.indexOfFirst { it.id == pickerPlayerId }
                }
            }
        } else {
            val highestCardInfo = playedCardsInfo.filter { it.card.suit == leadSuit }
                                                 .maxByOrNull { it.card.rank.value }
            if (highestCardInfo != null) {
                val roundWinnerPlayerId = highestCardInfo.playerId
                _gameMessage.value = "${getPlayerById(roundWinnerPlayerId)?.name} wins the trick with ${highestCardInfo.card.rank} of ${highestCardInfo.card.suit}."
                _discardPile.update { it + playedCards }
                _currentPlayerIndex.value = _players.value.indexOfFirst { it.id == roundWinnerPlayerId }
            } else {
                _gameMessage.value = "Error: No highest card. Discarding pile."
                 _discardPile.update { it + playedCards }
                 // Current player leads again as fallback
            }
            isFirstRound = false
        }

        _currentPlayedCardsInfo.value = emptyList()
        leadSuit = null
        checkForBhabhi()

        if (_gameState.value == GameState.PLAYER_TURN) {
            val leadingPlayer = _players.value[_currentPlayerIndex.value]
            if (leadingPlayer.hand.isEmpty() && !leadingPlayer.hasLost) {
                _gameMessage.value += " ${leadingPlayer.name} won/leads but has no cards (SR4)."
                var nextLeadPlayerIndex = (_currentPlayerIndex.value + 1) % _players.value.size
                var attempts = 0
                while ((_players.value[nextLeadPlayerIndex].hand.isEmpty() || _players.value[nextLeadPlayerIndex].hasLost) && attempts < _players.value.size) {
                    nextLeadPlayerIndex = (nextLeadPlayerIndex + 1) % _players.value.size
                    attempts++
                }
                if (_players.value[nextLeadPlayerIndex].hand.isNotEmpty() && !_players.value[nextLeadPlayerIndex].hasLost) {
                    _currentPlayerIndex.value = nextLeadPlayerIndex
                    _gameMessage.value += " ${_players.value[_currentPlayerIndex.value].name} (to the left) starts."
                }
            }
            _gameMessage.value += " It's now ${_players.value.getOrNull(_currentPlayerIndex.value)?.name ?: "N/A"}'s turn."
            autoPlayBotIfNeeded()
        }
    }

    private fun checkForBhabhi() {
        val activePlayers = _players.value.filterNot { it.hasLost }
        val playersWithCards = activePlayers.filter { it.hand.isNotEmpty() }

        if (activePlayers.size <= 1 && _players.value.size > 1) {
            if (playersWithCards.size == 1) {
                val bhabhiPlayer = playersWithCards.first()
                updatePlayerBhabhiStatus(bhabhiPlayer.id, true)
                _gameState.value = GameState.GAME_OVER
                _gameMessage.value = "${bhabhiPlayer.name} is the Bhabhi! Game Over."
            } else if (playersWithCards.isEmpty()) {
                _gameMessage.value = "All players have finished their cards! Game Over."
                _gameState.value = GameState.GAME_OVER
            }
        } else if (_gameState.value != GameState.SHOOT_OUT_SETUP && _gameState.value != GameState.SHOOT_OUT_DRAWING && _gameState.value != GameState.SHOOT_OUT_RESPONDING && _gameState.value != GameState.GAME_OVER) {
            _gameState.value = GameState.PLAYER_TURN
        }
    }

    private fun updatePlayerBhabhiStatus(playerId: String, isBhabhi: Boolean) {
        _players.update { list ->
            list.map {
                if (it.id == playerId) it.copy(isBhabhi = isBhabhi, hasLost = isBhabhi)
                else it
            }
        }
    }

    fun resetGame() {
        _gameMessage.value = "Resetting game..."
        val currentPlayersNames = _players.value.map { it.name } // Preserve names
        setupGame(currentPlayersNames.ifEmpty { listOf("Player 1", "Player 2", "Player 3") }) // Restart with same or default names
    }

    private fun updatePlayerHand(player: Player, newHand: List<Card>) {
        _players.update { list ->
            list.map {
                if (it.id == player.id) it.copy(hand = newHand.sorted().toMutableList()) else it
            }
        }
    }

    private fun getPlayerById(id: String): Player? = _players.value.find { it.id == id }

    fun attemptTakeHandFromLeft(playerIndex: Int) {
        if (_gameState.value == GameState.EVALUATING_ROUND || !_currentPlayedCardsInfo.value.isEmpty()) {
            _gameMessage.value = "SR2: Cannot take hand mid-trick. Wait for trick to finish."
            return
        }
        if (playerIndex < 0 || playerIndex >= _players.value.size) {
            _gameMessage.value = "SR2: Invalid player index."
            return
        }
        val takingPlayer = _players.value[playerIndex]
        if (takingPlayer.hasLost) {
            _gameMessage.value = "SR2: ${takingPlayer.name} is already out of the game."
            return
        }
        var leftPlayerIndex = if (playerIndex == 0) _players.value.size - 1 else playerIndex - 1
        var attempts = 0
        while(_players.value[leftPlayerIndex].hasLost && attempts < _players.value.size) {
            leftPlayerIndex = if (leftPlayerIndex == 0) _players.value.size - 1 else leftPlayerIndex - 1
            attempts++
        }
        val leftPlayer = _players.value[leftPlayerIndex]
        if (leftPlayer.hasLost || leftPlayer.id == takingPlayer.id || leftPlayer.hand.isEmpty()) { // Cannot take empty hand
            _gameMessage.value = "SR2: No valid player to the left with cards to take hand from."
            return
        }
        _gameMessage.value = "SR2: ${takingPlayer.name} attempts to take hand from ${leftPlayer.name}."
        val combinedHand = (takingPlayer.hand + leftPlayer.hand).sorted().toMutableList()
        val leftPlayerOriginalHandSize = leftPlayer.hand.size
        _players.update { list ->
            list.map {
                when (it.id) {
                    takingPlayer.id -> it.copy(hand = combinedHand)
                    leftPlayer.id -> it.copy(hand = mutableListOf(), hasLost = true) 
                    else -> it
                }
            }
        }
        _gameMessage.value = "${takingPlayer.name} took ${leftPlayerOriginalHandSize} cards from ${leftPlayer.name}. ${leftPlayer.name} is out of the game!"
        _currentPlayerIndex.value = playerIndex // Taker is current player
        checkForBhabhi()
        if (_gameState.value != GameState.GAME_OVER) {
            _gameState.value = GameState.PLAYER_TURN
            autoPlayBotIfNeeded()
        }
    }

    // --- Bot AI Logic ---
    private fun findPlayForBot(botPlayer: Player): Card? {
        if (botPlayer.hand.isEmpty()) return null

        // Rule: Must play Ace of Spades if it's the first card of the game, bot has it, and is the designated starter.
        if (isFirstRound && _currentPlayedCardsInfo.value.isEmpty() &&
            _players.value.getOrNull(originalStarterOfFirstRoundIndex)?.id == botPlayer.id ) {
            val aceOfSpades = botPlayer.hand.find { it.rank == Rank.ACE && it.suit == Suit.SPADES }
            if (aceOfSpades != null) return aceOfSpades
        }

        if (leadSuit == null) { // Bot is leading the trick
            // Play the lowest rank card. If multiple, prefer lower suit ordinal (less "valuable" suit).
            return botPlayer.hand.minWithOrNull(compareBy({ it.rank.value }, { it.suit.ordinal }))
        } else {
            // Try to follow suit
            val cardsInSuit = botPlayer.hand.filter { it.suit == leadSuit }
            if (cardsInSuit.isNotEmpty()) {
                // Play the lowest card of the lead suit.
                return cardsInSuit.minByOrNull { it.rank.value }
            } else {
                // Cannot follow suit, play a high-value card of a different suit (to get rid of it)
                // Prefer highest rank, then suit with most cards in hand (to break it if possible), then highest suit ordinal.
                return botPlayer.hand.maxWithOrNull(compareBy({ it.rank.value }, { botPlayer.hand.count { c -> c.suit == it.suit} }, {it.suit.ordinal}))
            }
        }
    }

    private fun autoPlayBotIfNeeded() {
        if (!(_gameState.value == GameState.PLAYER_TURN ||
              _gameState.value == GameState.SHOOT_OUT_DRAWING ||
              _gameState.value == GameState.SHOOT_OUT_RESPONDING)) {
            return
        }

        val currentPlayer = _players.value.getOrNull(_currentPlayerIndex.value)
        if (currentPlayer != null && currentPlayer.isBot && !currentPlayer.hasLost) {
            viewModelScope.launch {
                delay(1000) // UX delay

                val stillCurrentPlayer = _players.value.getOrNull(_currentPlayerIndex.value)
                if (stillCurrentPlayer?.id != currentPlayer.id || stillCurrentPlayer.hasLost || _gameState.value == GameState.GAME_OVER) {
                    return@launch // State changed, or player changed, or game ended during delay
                }
                
                _gameMessage.value = "${currentPlayer.name} (Bot) is thinking..."
                delay(500) // Thinking delay

                // Re-check state before playing, as it might have changed during the thinking delay
                 if (stillCurrentPlayer.id != _players.value.getOrNull(_currentPlayerIndex.value)?.id || _gameState.value == GameState.GAME_OVER) return@launch


                when (_gameState.value) {
                    GameState.PLAYER_TURN -> {
                        val cardToPlay = findPlayForBot(currentPlayer)
                        if (cardToPlay != null) {
                            playCard(_currentPlayerIndex.value, cardToPlay)
                        } else if (currentPlayer.hand.isNotEmpty()) {
                            _gameMessage.value = "Error: Bot ${currentPlayer.name} could not find a card to play."
                        }
                    }
                    GameState.SHOOT_OUT_DRAWING -> {
                        if (currentPlayer.id == shootOutDrawingPlayerId) {
                            shootOutDrawCard(currentPlayer.id)
                        }
                    }
                    GameState.SHOOT_OUT_RESPONDING -> {
                        if (currentPlayer.id == shootOutRespondingPlayerId) {
                            val drawnCard = shootOutDrawnCard ?: return@launch
                            var cardToPlayByBot: Card? = null
                            cardToPlayByBot = currentPlayer.hand.filter { it.rank.value > drawnCard.rank.value }
                                .minByOrNull { it.rank.value }
                            if (cardToPlayByBot == null) {
                                cardToPlayByBot = currentPlayer.hand.minByOrNull { it.rank.value }
                            }
                            if (cardToPlayByBot != null) {
                                shootOutRespond(currentPlayer.id, cardToPlayByBot)
                            } else if (currentPlayer.hand.isNotEmpty()) {
                                _gameMessage.value = "Error: Bot ${currentPlayer.name} has no cards to respond in Shoot-Out but hand not empty."
                            }
                        }
                    }
                    else -> { /* Do nothing */ }
                }
            }
        }
    }

    // --- Shoot-Out (SR4B) Implementation ---
    private fun initiateShootOut(playerAId: String, playerBId: String) {
        shootOutDrawingPlayerId = playerAId
        shootOutRespondingPlayerId = playerBId
        _gameState.value = GameState.SHOOT_OUT_SETUP
        _gameMessage.value = "SR4B: Shoot-Out! ${getPlayerById(playerAId)?.name} vs ${getPlayerById(playerBId)?.name}. ${getPlayerById(playerAId)?.name} to draw."
        _currentPlayerIndex.value = _players.value.indexOfFirst{it.id == playerAId}
        _gameState.value = GameState.SHOOT_OUT_DRAWING
        autoPlayBotIfNeeded()
    }

    fun shootOutDrawCard(drawingPlayerId: String) {
        if (drawingPlayerId != shootOutDrawingPlayerId || _gameState.value != GameState.SHOOT_OUT_DRAWING) {
            _gameMessage.value = "SR4B Error: Not your turn or wrong state to draw for shootout."
            return
        }
        if (_discardPile.value.size < 3) {
            _gameMessage.value = "SR4B: Not enough cards in discard pile for Shoot-Out! Game ends."
            updatePlayerBhabhiStatus(drawingPlayerId, true)
            _gameState.value = GameState.GAME_OVER
            return
        }

        val drawingPlayer = getPlayerById(drawingPlayerId)!!
        val mutableDiscard = _discardPile.value.toMutableList()
        val drawableCards = mutableDiscard.dropLast(2)
        if (drawableCards.isEmpty()) {
             _gameMessage.value = "SR4B: No drawable cards for Shoot-Out! Game ends."
             updatePlayerBhabhiStatus(drawingPlayerId, true)
            _gameState.value = GameState.GAME_OVER
            return
        }

        val randomIndex = Random.nextInt(drawableCards.size)
        shootOutDrawnCard = drawableCards[randomIndex]
        mutableDiscard.remove(shootOutDrawnCard)
        _discardPile.value = mutableDiscard

        updatePlayerHand(drawingPlayer, (drawingPlayer.hand + shootOutDrawnCard!!).toMutableList())
        _gameMessage.value = "${drawingPlayer.name} drew ${shootOutDrawnCard!!.rank} of ${shootOutDrawnCard!!.suit}. ${getPlayerById(shootOutRespondingPlayerId!!)?.name} to respond."
        _gameState.value = GameState.SHOOT_OUT_RESPONDING
        _currentPlayerIndex.value = _players.value.indexOfFirst { it.id == shootOutRespondingPlayerId }
        autoPlayBotIfNeeded()
    }

    fun shootOutRespond(respondingPlayerId: String, cardToPlay: Card) {
        if (respondingPlayerId != shootOutRespondingPlayerId || _gameState.value != GameState.SHOOT_OUT_RESPONDING || shootOutDrawnCard == null) {
            _gameMessage.value = "SR4B Error: Not your turn or wrong state to respond for shootout."
            return
        }
        val respondingPlayer = getPlayerById(respondingPlayerId)!!
        if (!respondingPlayer.hand.contains(cardToPlay)) {
            _gameMessage.value = "SR4B: You don't have that card!"
            return
        }

        _gameMessage.value = "${respondingPlayer.name} responds with ${cardToPlay.rank} of ${cardToPlay.suit} against ${shootOutDrawnCard!!.rank} of ${shootOutDrawnCard!!.suit}."
        val mutableResponderHand = respondingPlayer.hand.toMutableList()
        mutableResponderHand.remove(cardToPlay)
        updatePlayerHand(respondingPlayer, mutableResponderHand)
        val shootOutPile = mutableListOf(shootOutDrawnCard!!, cardToPlay)

        var winnerId: String? = null
        var loserId: String? = null

        if (cardToPlay.rank.value > shootOutDrawnCard!!.rank.value) {
            winnerId = respondingPlayerId; loserId = shootOutDrawingPlayerId
            _gameMessage.value += " ${respondingPlayer.name} wins the Shoot-Out round!"
        } else if (cardToPlay.rank.value < shootOutDrawnCard!!.rank.value) {
            winnerId = shootOutDrawingPlayerId; loserId = respondingPlayerId
            _gameMessage.value += " ${getPlayerById(shootOutDrawingPlayerId!!)?.name} wins the Shoot-Out round!"
        } else {
            _discardPile.update { it + shootOutPile }
            _gameMessage.value += " Same rank! Re-Shoot! Cards added to discard. ${getPlayerById(shootOutDrawingPlayerId!!)?.name} draws again."
            shootOutDrawnCard = null
            _gameState.value = GameState.SHOOT_OUT_DRAWING
            _currentPlayerIndex.value = _players.value.indexOfFirst { it.id == shootOutDrawingPlayerId }
            autoPlayBotIfNeeded()
            return
        }
        
        val loserPlayer = getPlayerById(loserId!!)!!
        val winnerPlayer = getPlayerById(winnerId!!)!!
        val mutableLoserHand = loserPlayer.hand.toMutableList()
        mutableLoserHand.addAll(shootOutPile.map { it.copy(isPlayed = false) })
        updatePlayerHand(loserPlayer, mutableLoserHand)
        _gameMessage.value += " ${loserPlayer.name} picks up ${shootOutPile.size} cards."

        shootOutDrawnCard = null; shootOutDrawingPlayerId = null; shootOutRespondingPlayerId = null

        if (winnerPlayer.hand.isEmpty()) {
            _gameMessage.value += " ${winnerPlayer.name} has finished all cards!"
            updatePlayerBhabhiStatus(loserPlayer.id, true)
            _gameState.value = GameState.GAME_OVER
        } else if (loserPlayer.hand.isEmpty() && winnerPlayer.hand.isNotEmpty()) {
            updatePlayerBhabhiStatus(winnerPlayer.id, true)
            _gameState.value = GameState.GAME_OVER
        } else {
            _currentPlayerIndex.value = _players.value.indexOfFirst { it.id == winnerId }
            _gameState.value = GameState.PLAYER_TURN
            _gameMessage.value += " It's ${getPlayerById(winnerId)?.name}'s turn."
            autoPlayBotIfNeeded()
        }
        checkForBhabhi()
    }
}
