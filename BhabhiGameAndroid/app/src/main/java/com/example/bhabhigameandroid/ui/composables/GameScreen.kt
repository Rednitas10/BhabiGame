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
// import com.example.bhabhigameandroid.GameEngine // GameEngine directly is no longer used
import com.example.bhabhigameandroid.Player // Domain Player
import com.example.bhabhigameandroid.GameState as BhabhiGameState // Alias for enum
import com.example.bhabhigameandroid.PlayedCardInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import com.example.bhabhigameandroid.ui.viewmodels.GameViewModel
import com.example.bhabhigameandroid.ui.viewmodels.GameViewModelFactory
import androidx.compose.foundation.shape.RoundedCornerShape // Ensure RoundedCornerShape is imported
import androidx.compose.animation.core.* // For rememberInfiniteTransition, animateFloat, etc.
import androidx.compose.ui.draw.scale // For Modifier.scale()

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
        Box(modifier = Modifier.padding(2.dp).width(60.dp).height(90.dp).background(Color.LightGray.copy(alpha = 0.1f)))
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

    val cardColor = if (displayCard.suit == com.example.bhabhigameandroid.Suit.HEARTS || displayCard.suit == com.example.bhabhigameandroid.Suit.DIAMONDS) Color(0xFFD32F2F) else Color(0xFF212121) // Red or Black for suits
    val border = if (isSelected) BorderStroke(2.5.dp, Color(0xFF008080)) else BorderStroke(1.5.dp, Color(0xFF4682B4)) // Deep Teal (Selected) or Calm Blue (Normal)
    val elevation = if (isSelected) 8.dp else 4.dp

    Card(
        modifier = Modifier
            .padding(2.dp)
            .width(60.dp)
            .height(90.dp)
            .then(if (isClickable) Modifier.clickable(onClick = onCardClick) else Modifier),
        shape = RoundedCornerShape(6.dp),
        backgroundColor = Color(0xFFFAF8F0), // Light Cream
        border = border,
        elevation = elevation
    ) {
        Column(
            modifier = Modifier.padding(6.dp).fillMaxSize(), // Updated padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = rankSymbol,
                fontSize = 22.sp, // Updated fontSize
                fontWeight = FontWeight.Bold, // Updated fontWeight
                color = cardColor // Rank text color same as suit
            )
            Text(
                text = suitSymbol,
                fontSize = 20.sp, // Updated fontSize
                color = cardColor // Suit symbol color same as suit
            )
            if (cardInfo != null) {
                 Text(
                    text = getPlayerById(gameEngine = null, playerId = cardInfo.playerId, playersState = null)?.name?.take(3) ?: "P?",
                    fontSize = 10.sp, // Updated fontSize
                    color = Color(0xFF333333), // Dark Gray/Off-Black for player abbreviation
                    modifier = Modifier.align(Alignment.Start)
                )
            }
        }
    }
}

import com.example.bhabhigameandroid.ui.theme.BhabhiGameAndroidTheme // Assuming this exists

// Helper to get player by ID, to be used carefully as gameEngine might not be available here directly
// Or pass players list as a parameter.
// This helper might need to be adapted or removed if GameEngine instance is not available.
// For PlayingCardView, player name can be fetched from GameViewModel's players list using playerId.
fun getPlayerNameById(playerId: String, playersList: List<Player>): String {
    return playersList.find { it.uid == playerId }?.name ?: "P?"
}


@Composable
fun PlayerHandView(
    player: Player,
    playerIndex: Int, // For avatar color
    isCurrentPlayer: Boolean,
    isLocalPlayer: Boolean,
    currentGameState: BhabhiGameState,
    selectedCardUi: Card?,
    onCardSelected: (Card) -> Unit,
    localPlayerId: String? // Pass localPlayerId to determine if this hand's interactions are enabled
) {
    val canPlay = isCurrentPlayer && player.uid == localPlayerId && !player.hasLost &&
                  (currentGameState == BhabhiGameState.PLAYER_TURN ||
                   (currentGameState == BhabhiGameState.SHOOT_OUT_RESPONDING && player.uid == localPlayerId))


    Column(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .background(if (isCurrentPlayer && currentGameState != BhabhiGameState.GAME_OVER && !player.hasLost) Color(0xFF008080).copy(alpha = 0.1f) else Color.Transparent)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val isMyTurnAndCanPlay = isCurrentPlayer && currentGameState == BhabhiGameState.PLAYER_TURN && !player.hasLost && isLocalPlayer

            val avatarScale = if (isMyTurnAndCanPlay) { // Pulsing for local player's turn
                val infiniteTransition = rememberInfiniteTransition(label = "avatarPulsingScale")
                infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "avatarScaleFactor"
                ).value
            } else {
                1f // Default scale
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .scale(avatarScale) // Apply the animated scale
                    .background(
                        color = playerColor(playerIndex), // Unchanged
                        shape = CircleShape
                    )
                    .border(1.dp, Color(0xFF333333).copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${player.name} (${player.hand.size}) ${if (player.isBhabhi) " - BHABHI" else if (player.hasLost) " - LOST" else ""}",
                fontWeight = if (player.isBhabhi) FontWeight.Bold else if (isCurrentPlayer && !player.hasLost) FontWeight.Bold else FontWeight.Normal,
                color = if (player.isBhabhi) Color(0xFFD32F2F)
                        else if (player.hasLost) Color(0xFF757575)
                        else Color(0xFF333333),
                style = MaterialTheme.typography.subtitle1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(player.hand.sorted()) { card ->
                PlayingCardView(
                    card = card,
                    isSelected = selectedCardUi == card && player.uid == localPlayerId, // Highlight only if it's the local player's selected card
                    isClickable = canPlay,
                    onCardClick = { if (canPlay) onCardSelected(card) }
                )
            }
        }
    }
}

@Composable
fun GameScreen(
    navController: NavController,
    roomId: String
) {
    val gameViewModel: GameViewModel = viewModel(factory = GameViewModelFactory(roomId))

    val players by gameViewModel.players.collectAsState()
    val currentPlayerIndex by gameViewModel.currentPlayerIndex.collectAsState()
    val currentPlayedCardsInfo by gameViewModel.currentPlayedCardsInfo.collectAsState()
    val gameState by gameViewModel.gameState.collectAsState()
    val gameMessage by gameViewModel.gameMessage.collectAsState()
    val selectedCard by gameViewModel.selectedCard.collectAsState() // This is gameViewModel.gameEngine.selectedCard
    val localPlayerId = gameViewModel.localPlayerId
    val error by gameViewModel.error.collectAsState()
    // val roomDetails by lazy {players.isNotEmpty()} // This placeholder is no longer needed for GameTableComposable
    val isConnected by gameViewModel.isConnected.collectAsState()
    val isActionPending by gameViewModel.isActionPending.collectAsState()


    // Shoot-out specific states from ViewModel
    val shootOutDrawingPlayerId by gameViewModel.shootOutDrawingPlayerId.collectAsState()
    val shootOutRespondingPlayerId by gameViewModel.shootOutRespondingPlayerId.collectAsState()

    BhabhiGameAndroidTheme { // Apply the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAF8F0) // Theme background for the screen
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
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
                    text = gameMessage,
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.subtitle1,
                    color = if (gameMessage.contains("Error", ignoreCase = true) || gameMessage.contains("Bhabhi", ignoreCase = true)) Color(0xFFD32F2F) else Color(0xFF333333)
                )
                error?.let {
                    Text(
                        text = "Error: $it",
                        color = Color.Red,
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (players.isEmpty() && gameState == BhabhiGameState.INITIALIZING && error == null) {
                        CircularProgressIndicator(color = Color(0xFF008080))
                    } else if (players.isNotEmpty() && gameState !in listOf(BhabhiGameState.INITIALIZING, BhabhiGameState.WAITING, BhabhiGameState.DEALING)) {
                        GameTableComposable(
                            players = players,
                            currentPlayerIndex = currentPlayerIndex,
                            currentPlayedCardsInfo = currentPlayedCardsInfo,
                            onCardSelected = { card -> gameViewModel.onCardSelected(card) },
                            selectedCardUi = selectedCard,
                            localPlayerId = localPlayerId,
                            currentGameState = gameState,
                            getPlayerNameById = { pId -> getPlayerNameById(pId, players) }
                        )
                    } else { // Covers INITIALIZING (if players not empty but still in init), WAITING, DEALING
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF3A6B35)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when (gameState) {
                                        BhabhiGameState.INITIALIZING -> "Initializing Game..."
                                        BhabhiGameState.WAITING -> "Waiting for more players..."
                                        BhabhiGameState.DEALING -> "Dealing cards..."
                                        else -> "Loading game..."
                                    },
                                    style = MaterialTheme.typography.h6,
                                    color = Color.White
                                )
                                if (gameState == BhabhiGameState.INITIALIZING || gameState == BhabhiGameState.WAITING) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                val currentPlayerObject = players.getOrNull(currentPlayerIndex)
                val isLocalPlayerTurn = currentPlayerObject?.uid == localPlayerId && currentPlayerObject?.hasLost == false

                if (gameState == BhabhiGameState.PLAYER_TURN && isLocalPlayerTurn) {
                    Button(
                        onClick = { gameViewModel.onPlayCardAction() },
                        enabled = selectedCard != null && !isActionPending && isConnected,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080),
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFFD3D3D3),
                        disabledContentColor = Color(0xFFA9A9A9)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Play Selected Card", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { gameViewModel.onTakeHandFromLeftAction() },
                    enabled = currentPlayedCardsInfo.isEmpty() && localPlayerCanPlay,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080),
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFFD3D3D3),
                        disabledContentColor = Color(0xFFA9A9A9)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("SR2: Take Hand From Left", fontWeight = FontWeight.SemiBold)
                }
            }

            if (gameState == BhabhiGameState.SHOOT_OUT_DRAWING && shootOutDrawingPlayerId == localPlayerId && localPlayerCanPlay) {
                Button(
                    onClick = { gameViewModel.onShootOutDrawAction() },
                    enabled = localPlayerCanPlay, // Redundant if already checked but good for clarity
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF008080), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("SR4B: Draw Card for Shoot-Out", fontWeight = FontWeight.SemiBold) }
            }
            if (gameState == BhabhiGameState.SHOOT_OUT_RESPONDING && shootOutRespondingPlayerId == localPlayerId) {
                 Button(
                    onClick = { gameViewModel.onShootOutRespondAction() },
                    enabled = selectedCard != null,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080),
                        contentColor = Color(0xFFFFFFFF),
                        disabledBackgroundColor = Color(0xFFD3D3D3),
                        disabledContentColor = Color(0xFFA9A9A9)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("SR4B: Respond with Selected Card", fontWeight = FontWeight.SemiBold) }
            }

            if (gameState == BhabhiGameState.GAME_OVER) {
                // Check if local player is host for restart capability
                // val amIHost = players.find { it.uid == localPlayerId }?.isHost ?: false
                // if (amIHost) { // For now, allow anyone to trigger restart signal
                Button(
                    onClick = { gameViewModel.onRestartGameAction() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080),
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Restart Game", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        navController.popBackStack("main_menu", inclusive = false)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080),
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Back to Main Menu", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
