package com.example.bhabhigameandroid.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun RulesScreen(navController: NavController) {
    val rulesText = """
    Rules:

    This game uses a standard deck of cards (with no jokers) which is dealt out completely to the players. The object of the game is to get rid of all your cards. The last person with cards has lost and is named the "Bhabhi." The Bhabhi is the loser of the game - all other players have won the game.

    The player with the Ace of Spades starts the game by playing that card. Play proceeds clockwise with each player following suit if possible. Players must play one card of the suit that was lead if they have one, but may play a card of any value of that suit.

    If all players have played one card of the same suit, play stops and the complete round of cards are removed from the game (placed in a discard pile). The player who played the highest card of the suit now has the lead and may play any card from his hand to start the next round.

    If, on his turn, a player cannot play a card of the suit that was lead, he may play any other card from his hand. Play stops immediately and the player who played the highest card of the suit that was lead must pick up all the cards in play (all the cards of the suit that was lead, plus the one out of suit card that stopped play) and add them to his hand. This player now has the lead and may play any card from his hand to start the next round.

    Play continues in this manner until only one player has cards left. They are the Bhabhi, and have lost the game. All other players have won.

    Special Rule 1:
    On the first round, if a player plays out of suit, play continues until all players have played one card. All these cards are added to the discard pile, even if one or more players has played out of suit. For this reason, turn order is not important on the first round and players may all throw out a Spade (or another card if they have no Spades) as soon as they have their hands arranged.

    Special Rule 2:
    At any time in the game (except in the middle of a round of play) a player may take the entire hand of cards from the player on his left. The player whose hand was taken immediately becomes one of the winners of the game. A player may take the hands of multiple players, so long as they are always taking from their immediate left.

    Special Rule 3:
    If play should stop because one player has played out of suit, but the next player still plays a card, that player will have to pick up all the cards and add them to their hand (not the player who had the highest card of the suit that was lead).

    Special Rule 4:
    If there are more than two players left in the game, and all players have played a card of the same suit to complete the round, but the player who played the highest card of that suit (the player who should therefore start the next round) has no cards left, that player has become one of the winners by getting rid of all their cards and is out of the game. The player on that player's left will start the next round.

    Special Rule 4B: The Shoot-Out!
    Sometimes the end of the game will occur in a special way which will force a shoot-out between the last two players in the game to determine the Bhabhi. This occurs when there are only two players left in the game, and the player with fewer cards plays their last card, but the other player manages to play a card of the same suit but lower in value. In this situation (similar to Special Rule 3) the cards are removed from the game, but the player who now has the lead (but has no cards) is not yet out of the game. This player (the Drawing Player) must randomly draw a card from the general discard pile (excluding the two cards that forced the shoot-out). The other player (the Responding Player) must play a card according to the regular rules of play. One of three things could happen:

    1) If the Responding Player plays a card of the same suit as the drawn card, but of a lower value, then Drawing Player is still in the lead and must draw randomly again from the general discard pile. Play continues until one of the following occurs:

    2) If the Responding Player must play a card of the same suit as the drawn card but of a higher value, he has lost the game and becomes the Bhabhi.

    3)If the Responding Player has no cards of the suit of the drawn card, he can play any other card and the Drawing Player has lost the game and becomes the Bhabhi.
    """.trimIndent()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFAF8F0) // Light Cream Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bhabhi Game Rules",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333), // Dark Gray/Off-Black
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { navController.popBackStack() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF008080), // Deep Teal
                        contentColor = Color(0xFFFFFFFF) // White
                    )
                ) {
                    Text("Back", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // For simplicity, the entire rules text is one block.
                // For bolding specific headings within rulesText like "Rules:" or "Special Rule X:",
                // a more complex parsing or multiple Text composables would be needed.
                // The current instruction is to apply bold to "Headings" in general,
                // which the main "Bhabhi Game Rules" title already covers.
                // The body text will use FontWeight.Normal.
                Text(
                    text = rulesText,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF333333), // Dark Gray/Off-Black
                    lineHeight = 22.sp // Improve readability
                )
            }
        }
    }
}
