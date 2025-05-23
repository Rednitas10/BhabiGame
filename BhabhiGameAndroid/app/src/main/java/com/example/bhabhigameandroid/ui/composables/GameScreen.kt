package com.example.bhabhigameandroid.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bhabhigameandroid.Card
import com.example.bhabhigameandroid.GameEngine
import com.example.bhabhigameandroid.GameState
import com.example.bhabhigameandroid.PlayedCardInfo
import com.example.bhabhigameandroid.Player
import androidx.lifecycle.viewmodel.compose.viewModel // For viewModel()
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border

// Helper function to determine player color
@Composable
fun playerColor(playerIndex: Int): Color {
    val colors = listOf(
        Color(0xFFE91E63), // Pink
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFFC107), // Amber
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4)  // Cyan
    )
    return colors[playerIndex % colors.size]
}

@Composable
fun PlayingCardView(
    cardInfo: PlayedCardInfo? = null, // Used for played cards area
    card: Card? = null, // Used for player hands
    isSelected: Boolean = false,
    isClickable: Boolean = false,
    onCardClick: () -> Unit = {}
) {
    val displayCard = cardInfo?.card ?: card
    if (displayCard == null) {
        // Show a placeholder or an empty space if no card
        Box(modifier = Modifier.padding(2.dp).width(60.dp).height(90.dp).background(Color.LightGray.copy(alpha = 0.2f)))
        return
    }


    val suitSymbol = when (displayCard.suit) {
        com.example.bhabhigameandroid.Suit.SPADES -> "♠"
        com.example.bhabhigameandroid.Suit.HEARTS -> "♥"
        com.example.bhabhigameandroid.Suit.DIAMONDS -> "♦"
        com.example.bhabhigameandroid.Suit.CLUBS -> "♣"
    }
    val rankSymbol = when (displayCard.rank) {
        com.example.bhabhigameandroid.Rank.ACE -> "A"
        com.example.bhabhigameandroid.Rank.KING -> "K"
        com.example.bhabhigameandroid.Rank.QUEEN -> "Q"
        com.example.bhabhigameandroid.Rank.JACK -> "J"
        else -> displayCard.rank.value.toString()
    }

    val cardColor = if (displayCard.suit == com.example.bhabhigameandroid.Suit.HEARTS || displayCard.suit == com.example.bhabhigameandroid.Suit.DIAMONDS) Color.Red else Color.Black
    val border = if (isSelected) BorderStroke(3.dp, Color.Blue) else BorderStroke(1.dp, Color.Gray)
    val elevation = if (isSelected) 4.dp else 2.dp

    Card(
        modifier = Modifier
            .padding(2.dp)
            .width(60.dp)
            .height(90.dp)
            .then(if (isClickable) Modifier.clickable(onClick = onCardClick) else Modifier),
        border = border,
        elevation = elevation
    ) {
        Column(
            modifier = Modifier.padding(4.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = rankSymbol, fontSize = 20.sp, color = cardColor, fontWeight = FontWeight.Bold)
            Text(text = suitSymbol, fontSize = 18.sp, color = cardColor)
            if (cardInfo != null) {
                 Text(
                    text = getPlayerById(gameEngine = null, playerId = cardInfo.playerId, playersState = null)?.name?.take(3) ?: "P?", // Placeholder for player name
                    fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
            }
        }
    }
}

// Helper to get player by ID, to be used carefully as gameEngine might not be available here directly
// Or pass players list as a parameter.
fun getPlayerById(gameEngine: GameEngine?, playerId: String, playersState: List<Player>?): Player? {
    return playersState?.find { it.id == playerId } ?: gameEngine?.players?.value?.find { it.id == playerId }
}


@Composable
fun PlayerHandView(
    player: Player,
    playerIndex: Int, // Added playerIndex for avatar color
    isCurrentPlayer: Boolean,
    gameState: GameState,
    canPlay: Boolean, // Derived from isCurrentPlayer and gameState
    selectedCard: Card?,
    onCardSelected: (Card) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .background(if (isCurrentPlayer && gameState != GameState.GAME_OVER && !player.hasLost) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = playerColor(playerIndex),
                        shape = CircleShape
                    )
                    .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${player.name} (${player.hand.size}) ${if (player.isBhabhi) " - BHABHI" else if (player.hasLost) " - LOST" else ""}",
                fontWeight = if (isCurrentPlayer && !player.hasLost) FontWeight.Bold else FontWeight.Normal,
                color = if (player.isBhabhi) Color.Red else if (player.hasLost) Color.DarkGray else Color.Black,
                style = MaterialTheme.typography.subtitle1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp), // Indent cards to align under text
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(player.hand.sorted()) { card -> // Keep hand sorted for consistent display
                PlayingCardView(
                    card = card,
                    isSelected = selectedCard == card && isCurrentPlayer,
                    isClickable = canPlay,
                    onCardClick = { if (canPlay) onCardSelected(card) }
                )
            }
        }
    }
}

@Composable
fun GameScreen(
    navController: androidx.navigation.NavController, // Added for potential navigation from GameScreen
    gameEngine: GameEngine = viewModel() // GameScreen now gets its own GameEngine instance
) {
    val players by gameEngine.players.collectAsState()
    val currentPlayerIndex by gameEngine.currentPlayerIndex.collectAsState()
    val currentPlayedCardsInfo by gameEngine.currentPlayedCardsInfo.collectAsState()
    val gameState by gameEngine.gameState.collectAsState()
    val gameMessage by gameEngine.gameMessage.collectAsState()

    var selectedCard by remember { mutableStateOf<Card?>(null) }
    // For player name input in INITIALIZING state
    val playerNamesState = remember { mutableStateListOf("You", "Bot Alice", "Bot Bob") } // Default for "Play vs Bots"

    // Setup game when screen is first composed after navigation
    LaunchedEffect(Unit) {
        if (players.isEmpty() || gameState == GameState.INITIALIZING) { // Only setup if not already set up (e.g. after config change)
            gameEngine.setupGame(playerNamesState.toList())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()), // Make screen scrollable for smaller devices
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Game Message Area
        Text(
            text = gameMessage,
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
            style = MaterialTheme.typography.subtitle1,
            color = if (gameMessage.contains("Error", ignoreCase = true) || gameMessage.contains("Bhabhi", ignoreCase = true)) Color.Red else MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Played Cards Area
        if (gameState != GameState.INITIALIZING && gameState != GameState.DEALING) {
            Text("Played Cards (Trick):", style = MaterialTheme.typography.h6)
            if (currentPlayedCardsInfo.isEmpty()) {
                Text("No cards played in current trick.", style = MaterialTheme.typography.caption)
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(100.dp), // Fixed height for played cards area
                    horizontalArrangement = Arrangement.Center
                ) {
                    items(currentPlayedCardsInfo) { playedCardInfo ->
                        PlayingCardView(cardInfo = playedCardInfo)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }


        // Player Areas
        if (players.isNotEmpty()) {
            players.forEachIndexed { index, player ->
                val isCurrentPlayer = index == currentPlayerIndex && !player.hasLost
                val canPlay = isCurrentPlayer && (gameState == GameState.PLAYER_TURN ||
                                                 (gameState == GameState.SHOOT_OUT_RESPONDING && player.id == gameEngine.shootOutRespondingPlayerId)
                                                )

                PlayerHandView(
                    player = player,
                    playerIndex = index, // Pass playerIndex
                    isCurrentPlayer = isCurrentPlayer,
                    gameState = gameState,
                    canPlay = canPlay,
                    selectedCard = selectedCard,
                    onCardSelected = { card -> if(canPlay) selectedCard = card }
                )
                if (index < players.size -1) Divider() // Don't add divider after last player
            }
        }
        Spacer(modifier = Modifier.weight(1f)) // Push buttons to bottom

        // Action Buttons
        if (gameState != GameState.INITIALIZING) {
            val currentPlayer = players.getOrNull(currentPlayerIndex)

            if (gameState == GameState.PLAYER_TURN && currentPlayer != null && !currentPlayer.hasLost) {
                Button(
                    onClick = {
                        selectedCard?.let {
                            gameEngine.playCard(currentPlayerIndex, it)
                            selectedCard = null // Deselect after playing
                        }
                    },
                    enabled = selectedCard != null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("Play Selected Card")
                }
                // SR2 Button
                Button(
                    onClick = { gameEngine.attemptTakeHandFromLeft(currentPlayerIndex) },
                    enabled = currentPlayedCardsInfo.isEmpty(), // Can only take if trick pile is empty
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("SR2: Take Hand From Left")
                }
            }

            // Shoot-Out Buttons
            if (gameState == GameState.SHOOT_OUT_DRAWING && currentPlayer?.id == gameEngine.shootOutDrawingPlayerId) {
                Button(
                    onClick = { gameEngine.shootOutDrawCard(currentPlayer.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) { Text("SR4B: Draw Card for Shoot-Out") }
            }
            if (gameState == GameState.SHOOT_OUT_RESPONDING && currentPlayer?.id == gameEngine.shootOutRespondingPlayerId) {
                 Button(
                    onClick = {
                        selectedCard?.let {
                            gameEngine.shootOutRespond(currentPlayer.id, it)
                            selectedCard = null
                        }
                    },
                    enabled = selectedCard != null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) { Text("SR4B: Respond with Selected Card") }
            }


            if (gameState == GameState.GAME_OVER || players.all{it.hasLost || it.isBhabhi}) {
                Button(
                    onClick = {
                        selectedCard = null
                        gameEngine.setupGame(playerNamesState.toList()) // Restart with same default names
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("Restart Game")
                }
                Button(
                    onClick = {
                        navController.popBackStack("main_menu", inclusive = false)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("Back to Main Menu")
                }
            }
        }
    }
}
