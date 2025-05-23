package com.example.bhabhigameandroid.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import com.example.bhabhigameandroid.GameEngine
import com.example.bhabhigameandroid.PlayedCardInfo
import com.example.bhabhigameandroid.Player
import com.example.bhabhigameandroid.GameState
import com.example.bhabhigameandroid.Card


@Composable
private fun PlayedCardsDisplay(
    gameEngine: GameEngine, // Pass full GameEngine to observe cardCollectionAnimationInfo
    playedCardsInfo: List<PlayedCardInfo>,
    players: List<Player>
) {
    val cardCollectionAnimInfo by gameEngine.cardCollectionAnimationInfo.collectAsState()

    if (playedCardsInfo.isEmpty() && cardCollectionAnimInfo == null) { // Also check if collection anim is done
        Text("No cards in trick", color = Color(0xFFFAF8F0).copy(alpha = 0.8f))
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp).fillMaxWidth()
        ) {
            items(playedCardsInfo, key = { it.card.id }) { cardInfo ->
                AnimatedPlayedCard(
                    cardInfo = cardInfo,
                    players = players,
                    collectionInfo = cardCollectionAnimInfo,
                    isCurrentlyInPlayedCards = true // Flag to differentiate from already collected cards
                )
            }
            // Render cards that are currently being collected but might already be removed from currentPlayedCardsInfo
            // This ensures they are visible during their exit animation.
            // This part is tricky if currentPlayedCardsInfo is cleared too soon by GameEngine.
            // The GameEngine delay before clearing currentPlayedCardsInfo is meant to handle this.
            // If cardCollectionAnimInfo is not null, and its cards are NOT in playedCardsInfo,
            // it means they are purely in exit animation.
             cardCollectionAnimInfo?.first?.filterNot { animCardInfo -> playedCardsInfo.any { it.card.id == animCardInfo.card.id } }?.forEach { cardInfo ->
                AnimatedPlayedCard(
                    cardInfo = cardInfo,
                    players = players,
                    collectionInfo = cardCollectionAnimInfo,
                    isCurrentlyInPlayedCards = false // These are only for exit animation
                )
            }
        }
    }
}

@Composable
private fun AnimatedPlayedCard(
    cardInfo: PlayedCardInfo,
    players: List<Player>,
    collectionInfo: Pair<List<PlayedCardInfo>, Int>?,
    isCurrentlyInPlayedCards: Boolean // True if this card is still in the main currentPlayedCardsInfo list
) {
    val playerWhoPlayed = players.find { it.id == cardInfo.playerId }
    val playerIndexOfWhoPlayed = players.indexOf(playerWhoPlayed)

    val animatedAlpha = remember { Animatable(if (isCurrentlyInPlayedCards) 0f else 1f) } // Start visible if only for exit
    val animatedScale = remember { Animatable(if (isCurrentlyInPlayedCards) 0.5f else 1f) }
    val initialEntryOffsetY = if (playerIndexOfWhoPlayed == 0 && !(playerWhoPlayed?.isBot ?: false)) 100f else -100f
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(if (isCurrentlyInPlayedCards) initialEntryOffsetY else 0f) }

    val isCollectingThisCard = collectionInfo?.first?.any { it.card.id == cardInfo.card.id } == true
    val collectingPlayerIndex = collectionInfo?.second ?: -1


    LaunchedEffect(key1 = cardInfo.card.id, key2 = isCollectingThisCard, key3 = isCurrentlyInPlayedCards) {
        if (isCollectingThisCard) {
            // Exit Animation
            val targetOffsetX = 0f // Keep centered horizontally
            val targetOffsetY = when (collectingPlayerIndex) {
                -1 -> 0f // Discard pile (center, slightly down perhaps or just fade)
                0 -> 200f // Human player (bottom)
                else -> -200f // Bot players (top)
            }
            launch { animatedAlpha.animateTo(0f, tween(400)) }
            launch { animatedScale.animateTo(0.3f, tween(400)) }
            launch { animatedOffsetX.animateTo(targetOffsetX, tween(400)) }
            launch { animatedOffsetY.animateTo(targetOffsetY, tween(400)) }
        } else if (isCurrentlyInPlayedCards) { // Only run entry animation if not collecting and is part of current trick
            // Entry Animation (from previous step)
            // Reset to initial state before entry animation if it's a new card instance in playedCardsInfo
            // This check might need to be more robust if cards can re-enter without collectionInfo changing.
            // For now, assume playedCardsInfo.size change is a good proxy for new cards being added.
            // Or better, if animatedAlpha is 0f (initial state for entry)
            if (animatedAlpha.value == 0f && animatedScale.value == 0.5f) { // Only run if in initial entry state
                 launch { animatedAlpha.animateTo(1f, tween(300)) }
                 launch { animatedScale.animateTo(1f, tween(300)) }
                 launch { animatedOffsetX.animateTo(0f, tween(300)) }
                 launch { animatedOffsetY.animateTo(0f, tween(300)) }
            } else if (animatedAlpha.value != 1f || animatedScale.value != 1f || animatedOffsetY.value != 0f) {
                // If card is not collecting and not in its final entry state, snap it there.
                // This can happen if collectionInfo becomes null mid-animation.
                launch{ animatedAlpha.snapTo(1f) }
                launch{ animatedScale.snapTo(1f) }
                launch{ animatedOffsetX.snapTo(0f) }
                launch{ animatedOffsetY.snapTo(0f) }
            }
        }
    }

    Box(modifier = Modifier.graphicsLayer {
        alpha = animatedAlpha.value
        scaleX = animatedScale.value
        scaleY = animatedScale.value
        translationX = animatedOffsetX.value
        translationY = animatedOffsetY.value
    }) {
        // Only render the card if it's supposed to be visible
        if (animatedAlpha.value > 0.01f) { // Avoid rendering completely transparent items
             PlayingCardView(cardInfo = cardInfo)
        }
    }
}

@Composable
private fun PlayerZone(
    player: Player,
    playerIndex: Int,
    isCurrentPlayer: Boolean,
    gameState: GameState,
    canPlay: Boolean,
    selectedCard: Card?,
    onCardSelected: (Card) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            // Background modifier removed
    ) {
        PlayerHandView(
            player = player,
            playerIndex = playerIndex,
            isCurrentPlayer = isCurrentPlayer,
            gameState = gameState,
            canPlay = canPlay,
            selectedCard = selectedCard,
            onCardSelected = onCardSelected
        )
    }
}

@Composable
fun GameTable(
    gameEngine: GameEngine,
    selectedCard: Card?, // Pass selectedCard down from GameScreen
    onCardSelected: (Card) -> Unit // Pass callback down from GameScreen
) {
    val players by gameEngine.players.collectAsState()
    val currentPlayerIndex by gameEngine.currentPlayerIndex.collectAsState()
    val currentPlayedCardsInfo by gameEngine.currentPlayedCardsInfo.collectAsState()
    val gameState by gameEngine.gameState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3A6B35)) // Muted Green table
    ) {
        // Central Play Area
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f) // Takes 80% of width
                .aspectRatio(2f / 1f) // Maintain a certain aspect ratio
                .background(Color(0xFF2F5D2F)) // Darker Muted Green
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            PlayedCardsDisplay(gameEngine, currentPlayedCardsInfo, players)
        }

        // Player Areas - Dynamic layout based on player count
        // This is a simplified layout. More complex layouts might use ConstraintLayout
        // or custom Layout composables for precise positioning around a table.

        if (players.isNotEmpty()) {
            val humanPlayer = players.firstOrNull { !it.isBot } ?: players.first() // Assume first player is human or default
            val humanPlayerIndex = players.indexOf(humanPlayer)

            val otherPlayers = players.filter { it.id != humanPlayer.id }

            // Player 0 (Human) - Bottom
            PlayerZone(
                player = humanPlayer,
                playerIndex = humanPlayerIndex,
                isCurrentPlayer = humanPlayerIndex == currentPlayerIndex && !humanPlayer.hasLost,
                gameState = gameState,
                canPlay = humanPlayerIndex == currentPlayerIndex && gameState == GameState.PLAYER_TURN && !humanPlayer.hasLost,
                selectedCard = selectedCard,
                onCardSelected = onCardSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
            )

            // Bot Players - Top area, arranged in a Row
            if (otherPlayers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    otherPlayers.take(3).forEach { botPlayer -> // Max 3 bots at top for this layout
                        val botPlayerIndex = players.indexOf(botPlayer)
                        PlayerZone(
                            player = botPlayer,
                            playerIndex = botPlayerIndex,
                            isCurrentPlayer = botPlayerIndex == currentPlayerIndex && !botPlayer.hasLost,
                            gameState = gameState,
                            canPlay = false, // Bots play automatically, UI selection not needed
                            selectedCard = null, // Bots don't use UI selection
                            onCardSelected = {}, // No-op for bots
                            modifier = Modifier.weight(1f) // Distribute space
                        )
                    }
                }
            }
        }

        // Conceptual Deck Area (Top-Right corner)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color(0xFF2F5D2F)) // Darker Muted Green
                .size(60.dp, 90.dp) // Approx card size
        ) {
            // Could display deck size or a card back if needed
            // For now, just a placeholder area
        }
    }
}
