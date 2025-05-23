package com.example.bhabhigameandroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GameEngineTest {

    private lateinit var gameEngine: GameEngine
    private val testDispatcher = StandardTestDispatcher() // Changed from UnconfinedTestDispatcher

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gameEngine = GameEngine()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Helper to create a list of cards from string representations (e.g., "AS", "KH", "2C")
    private fun createCards(cardStrings: List<String>): List<Card> {
        val cards = mutableListOf<Card>()
        for (cs in cardStrings) {
            val rankChar = cs.dropLast(1)
            val suitChar = cs.last()
            val rank = when (rankChar) {
                "A" -> Rank.ACE
                "K" -> Rank.KING
                "Q" -> Rank.QUEEN
                "J" -> Rank.JACK
                "10" -> Rank.TEN
                "9" -> Rank.NINE
                "8" -> Rank.EIGHT
                "7" -> Rank.SEVEN
                "6" -> Rank.SIX
                "5" -> Rank.FIVE
                "4" -> Rank.FOUR
                "3" -> Rank.THREE
                "2" -> Rank.TWO
                else -> throw IllegalArgumentException("Invalid rank: $rankChar")
            }
            val suit = when (suitChar) {
                'S' -> Suit.SPADES
                'H' -> Suit.HEARTS
                'D' -> Suit.DIAMONDS
                'C' -> Suit.CLUBS
                else -> throw IllegalArgumentException("Invalid suit: $suitChar")
            }
            cards.add(Card(suit, rank))
        }
        return cards
    }


    // --- Game Setup Tests ---
    @Test
    fun `setupGame creates players with correct names and deals cards`() = runTest {
        val playerNames = listOf("Alice", "Bob", "Charlie")
        gameEngine.setupGame(playerNames)

        assertEquals(playerNames.size, gameEngine.players.value.size)
        gameEngine.players.value.forEachIndexed { index, player ->
            assertEquals(playerNames[index], player.name)
            assertTrue("Player ${player.name} should have cards after setup", player.hand.isNotEmpty())
        }
        // Assuming 52 cards are dealt among 3 players: 17, 17, 18
        val expectedCardsPerPlayer = listOf(18, 17, 17) // Or 17,17,18 depending on dealing order
        val handSizes = gameEngine.players.value.map { it.hand.size }.sortedDescending()

        // Check if the hand sizes match one of the possible distributions.
        // The exact distribution depends on the dealing mechanism if not perfectly divisible.
        // For 3 players, it's 52 / 3 = 17 with 1 remainder, so one player gets 18.
        assertTrue(handSizes.containsAll(expectedCardsPerPlayer) && expectedCardsPerPlayer.containsAll(handSizes))


        assertEquals("Deck should be empty after dealing", 0, gameEngine.deck.value.cards.size)
        assertEquals(GameState.PLAYER_TURN, gameEngine.gameState.value)
    }

    @Test
    fun `setupGame identifies player with Ace of Spades as starting player`() = runTest {
        val playerNames = listOf("Alice", "Bob", "Charlie") // Bob will get Ace of Spades
        gameEngine.setupGame(playerNames) // Normal setup, Ace of Spades is random

        // Find who actually has Ace of Spades
        var actualAceHolderIndex = -1
        lateinit var aceOfSpadesPlayerName: String

        gameEngine.players.value.forEachIndexed { index, player ->
            if (player.hand.any { it.rank == Rank.ACE && it.suit == Suit.SPADES }) {
                actualAceHolderIndex = index
                aceOfSpadesPlayerName = player.name
                // println("${player.name} has Ace of Spades. Hand: ${player.hand.joinToString { it.id }}")
            }
        }
        // println("Starting player index from gameEngine: ${gameEngine.currentPlayerIndex.value}, name: ${gameEngine.players.value[gameEngine.currentPlayerIndex.value].name}")


        if (actualAceHolderIndex != -1) {
            assertEquals("Player with Ace of Spades ($aceOfSpadesPlayerName) should be the current player",
                actualAceHolderIndex, gameEngine.currentPlayerIndex.value)
        } else {
            // This case should ideally not happen in a standard 52 card deck with 3+ players
            // but if it does, the first player (index 0) should start.
            // This part of the test might be flaky if Ace of Spades is not dealt (e.g. <4 players).
            // For this test, we assume Ace of Spades is always dealt.
            fail("Ace of Spades was not found in any player's hand, test setup issue or unexpected dealing.")
        }
    }
    
    @Test
    fun `setupGame with less than 2 players does not start game`() = runTest {
        val playerNames = listOf("Alice")
        gameEngine.setupGame(playerNames)
        assertEquals(GameState.INITIALIZING, gameEngine.gameState.value)
        assertTrue(gameEngine.players.value.isEmpty())
        assertTrue(gameEngine.gameMessage.value.contains("Cannot start game with less than 2 players"))
    }

    // --- Card Playing Logic Tests ---
    @Test
    fun `playCard valid play follows suit and updates state`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames) // Alice starts (assuming Ace of Spades or default)

        val startingPlayer = gameEngine.players.value[gameEngine.currentPlayerIndex.value]
        val cardToPlay = startingPlayer.hand.first() // Play the first card

        gameEngine.playCard(gameEngine.currentPlayerIndex.value, cardToPlay)

        assertFalse("Card should be removed from player's hand",
            gameEngine.players.value[gameEngine.currentPlayerIndex.value].hand.contains(cardToPlay))
        assertEquals("Played card should be in currentPlayedCardsInfo",
            cardToPlay, gameEngine.currentPlayedCardsInfo.value.first().card)
        assertEquals("Lead suit should be set", cardToPlay.suit, gameEngine.leadSuit)
        // Game state might change to EVALUATING_ROUND if only one player left or all played
        // or PLAYER_TURN if more players to play in the trick
    }

    @Test
    fun `playCard out of suit when no lead suit cards updates state`() = runTest {
        gameEngine.setupGame(listOf("Alice", "Bob"))
        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!

        // Manipulate hands for test scenario
        val aliceCards = createCards(listOf("AS", "2H")) // Alice has Ace of Spades and a Heart
        val bobCards = createCards(listOf("KS", "3D"))   // Bob has King of Spades and a Diamond
        gameEngine.players.update { listOf(alice.copy(hand = aliceCards.toMutableList()), bob.copy(hand = bobCards.toMutableList())) }
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == alice.id } } // Alice starts

        // Alice plays Ace of Spades
        val aceOfSpades = aliceCards.first { it.rank == Rank.ACE && it.suit == Suit.SPADES }
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, aceOfSpades) // Alice plays AS

        // Now Bob's turn. Lead suit is SPADES. Bob has KS.
        // Let's assume Bob plays KS (follows suit) - this is handled by `valid play`
        // To test out of suit, Bob must not have SPADES.
        // So, new setup for Bob's turn:
        val bobCardsNoSpades = createCards(listOf("3D", "4C"))
        gameEngine.players.update {
            listOf(
                alice.copy(hand = gameEngine.players.value.find{it.id == alice.id}!!.hand), // Alice's hand is already updated by playCard
                bob.copy(hand = bobCardsNoSpades.toMutableList())
            )
        }
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == bob.id } } // Bob's turn
        gameEngine.leadSuit = Suit.SPADES // Lead suit is Spades from Alice's AS

        val cardToPlayByBob = bobCardsNoSpades.first() // Play 3D (out of suit)
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, cardToPlayByBob)

        assertFalse(gameEngine.players.value.find { it.id == bob.id }!!.hand.contains(cardToPlayByBob))
        assertTrue(gameEngine.currentPlayedCardsInfo.value.any { it.card == cardToPlayByBob && it.playerId == bob.id })
        // evaluateRound should be called, and Alice (who played AS) should pick up.
        assertTrue(gameEngine.gameMessage.value.contains("${alice.name} played highest of lead suit") && gameEngine.gameMessage.value.contains("picks up the pile"))
        assertEquals("Alice should have both cards now (AS, 3D)", 2, gameEngine.players.value.find{it.id == alice.id}!!.hand.size)
    }


    @Test
    fun `playCard playing last card updates player state`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)
        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        // Give Alice only one card
        val singleCard = createCards(listOf("AS")).first()
        gameEngine.players.update { listOf(alice.copy(hand = mutableListOf(singleCard)), gameEngine.players.value[1]) }
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == alice.id } }

        gameEngine.playCard(gameEngine.currentPlayerIndex.value, singleCard)

        assertTrue("Alice's hand should be empty", gameEngine.players.value.find { it.id == alice.id }!!.hand.isEmpty())
        assertTrue(gameEngine.gameMessage.value.contains("${alice.name} has played all their cards!"))
        // Further state depends on Bob's hand (e.g., GAME_OVER if Bob also empty, or Bob becomes Bhabhi)
    }
    
    @Test
    fun `playCard out of turn is prevented`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)

        val aliceIndex = gameEngine.players.value.indexOfFirst { it.name == "Alice" }
        val bobIndex = gameEngine.players.value.indexOfFirst { it.name == "Bob" }
        
        // Assume Alice is current player (or set it explicitly if needed after setup)
        val alice = gameEngine.players.value[aliceIndex]
        val cardInAliceHand = alice.hand.firstOrNull()

        // Bob tries to play
        val bob = gameEngine.players.value[bobIndex]
        val cardInBobHand = bob.hand.firstOrNull()
        
        if (cardInBobHand != null && gameEngine.currentPlayerIndex.value == aliceIndex) {
            val originalBobHandSize = bob.hand.size
            val originalPlayedCardsSize = gameEngine.currentPlayedCardsInfo.value.size

            gameEngine.playCard(bobIndex, cardInBobHand) // Bob attempts to play out of turn

            assertTrue(gameEngine.gameMessage.value.contains("It's not ${bob.name}'s turn!"))
            assertEquals("Bob's hand should not change", originalBobHandSize, gameEngine.players.value[bobIndex].hand.size)
            assertEquals("Played cards should not change", originalPlayedCardsSize, gameEngine.currentPlayedCardsInfo.value.size)
        } else {
            // Test setup might be problematic if hands are empty or current player is not as expected
            println("Skipping out of turn test due to setup condition: Alice's hand empty or Bob is current player.")
        }
    }


    // --- Round Evaluation Tests ---
    // Helper to set specific hands for players
    private fun setPlayerHands(vararg playerHands: Pair<String, List<Card>>) {
        val updatedPlayers = gameEngine.players.value.map { p ->
            playerHands.find { it.first == p.name }?.let {
                p.copy(hand = it.second.toMutableList())
            } ?: p
        }.toMutableList()
        gameEngine.players.value = updatedPlayers // Directly update for test setup
    }
    
    @Test
    fun `evaluateRound all play in suit highest card wins trick`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)

        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!

        // Setup specific hands
        val aliceCards = createCards(listOf("7S", "2H"))
        val bobCards = createCards(listOf("KS", "3D")) // Bob has higher Spade
        setPlayerHands("Alice" to aliceCards, "Bob" to bobCards)
        
        // Set Alice to start
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == alice.id } }

        // Alice plays 7S
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, aliceCards.first { it.rank == Rank.SEVEN })
        assertEquals(Suit.SPADES, gameEngine.leadSuit.value)
        
        // Bob plays KS
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, bobCards.first { it.rank == Rank.KING })

        // Bob should have won the trick and lead next
        assertTrue(gameEngine.gameMessage.value.contains("${bob.name} wins the trick"))
        assertEquals("Bob should lead next", gameEngine.players.value.indexOfFirst { it.id == bob.id }, gameEngine.currentPlayerIndex.value)
        assertTrue("Played cards should be cleared", gameEngine.currentPlayedCardsInfo.value.isEmpty())
        assertTrue("Played cards should go to discard pile", gameEngine.discardPile.value.size >= 2) // 7S and KS
    }

    @Test
    fun `evaluateRound out of suit played highest lead suit card picks up`() = runTest {
        val playerNames = listOf("Alice", "Bob", "Charlie")
        gameEngine.setupGame(playerNames)

        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!
        val charlie = gameEngine.players.value.find { it.name == "Charlie" }!!

        // Alice: 7S, QH (Queen of Hearts to break suit)
        // Bob: KS (King of Spades - highest spade)
        // Charlie: 2S (Two of Spades)
        val aliceCards = createCards(listOf("7S", "QH"))
        val bobCards = createCards(listOf("KS"))
        val charlieCards = createCards(listOf("2S"))
        setPlayerHands("Alice" to aliceCards, "Bob" to bobCards, "Charlie" to charlieCards)

        // Alice starts
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == alice.id } }
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, aliceCards.first { it.rank == Rank.SEVEN }) // Alice plays 7S

        // Bob plays KS
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, bobCards.first { it.rank == Rank.KING })

        // Charlie has 2S, must play it.
        // For this test, let's make Charlie play QH (out of suit, from Alice's hand conceptually for this test)
        // This means Charlie needs QH in hand.
        val charlieCardsNew = createCards(listOf("QH")) // Charlie has QH to break suit
        setPlayerHands("Charlie" to charlieCardsNew) // Override Charlie's hand
        // Alice's hand should have 7S played, Bob's KS played.
        // We need to ensure Charlie's turn.
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == charlie.id } }
        gameEngine.leadSuit.update { Suit.SPADES } // Manually ensure lead suit is Spades

        gameEngine.playCard(gameEngine.currentPlayerIndex.value, charlieCardsNew.first { it.rank == Rank.QUEEN }) // Charlie plays QH (out of suit)

        // Bob played KS (highest spade) and should pick up all three cards (7S, KS, QH)
        val bobAfterRound = gameEngine.players.value.find { it.id == bob.id }!!
        assertTrue(gameEngine.gameMessage.value.contains("${bob.name} played highest of lead suit") && gameEngine.gameMessage.value.contains("picks up the pile"))
        // Bob started with 0 (KS played), now picks up 3. Original hand size was 1.
        assertEquals("Bob should have 3 cards now", 3, bobAfterRound.hand.size)
        assertTrue("Played cards should be cleared", gameEngine.currentPlayedCardsInfo.value.isEmpty())
        assertEquals("Bob should lead next", gameEngine.players.value.indexOfFirst { it.id == bob.id }, gameEngine.currentPlayerIndex.value)
    }
    
    @Test
    fun `evaluateRound SR1 first round out of suit discards cards and original starter leads`() = runTest {
        val playerNames = listOf("Alice", "Bob") // Alice has AS, Bob has 2H
        gameEngine.setupGame(playerNames)

        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!
        
        // Ensure Alice has Ace of Spades and Bob has a non-spade
        val aliceHand = mutableListOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.CLUBS, Rank.THREE))
        val bobHand = mutableListOf(Card(Suit.HEARTS, Rank.TWO), Card(Suit.DIAMONDS, Rank.FOUR))
        setPlayerHands("Alice" to aliceHand, "Bob" to bobHand)

        // Find who starts (should be Alice if she has AS)
        var starterIndex = 0
        for ((index, player) in gameEngine.players.value.withIndex()) {
            if (player.hand.any { it.rank == Rank.ACE && it.suit == Suit.SPADES }) {
                starterIndex = index
                break
            }
        }
        gameEngine.currentPlayerIndex.update { starterIndex }
        val originalStarterName = gameEngine.players.value[starterIndex].name

        // Player with AS plays it (Alice)
        val aceOfSpades = gameEngine.players.value[starterIndex].hand.first { it.rank == Rank.ACE && it.suit == Suit.SPADES }
        gameEngine.playCard(starterIndex, aceOfSpades)

        // Next player (Bob) plays out of suit (2H)
        val nextPlayerIndex = gameEngine.currentPlayerIndex.value
        val cardToPlayOutOfSuit = gameEngine.players.value[nextPlayerIndex].hand.first { it.suit == Suit.HEARTS }
        gameEngine.playCard(nextPlayerIndex, cardToPlayOutOfSuit)
        
        // SR1 Applied: Cards go to discard, original starter leads
        assertTrue(gameEngine.gameMessage.value.contains("SR1: Out of suit in first round"))
        assertTrue("Played cards should be cleared", gameEngine.currentPlayedCardsInfo.value.isEmpty())
        assertTrue("Discard pile should contain the played cards", gameEngine.discardPile.value.size >= 2)
        assertEquals("Original starter ($originalStarterName) should lead next", starterIndex, gameEngine.currentPlayerIndex.value)
    }


    // --- Win/Loss Condition Tests ---
    @Test
    fun `checkForBhabhi declares Bhabhi when one player has cards`() = runTest {
        val playerNames = listOf("Alice", "Bob", "Charlie")
        gameEngine.setupGame(playerNames)

        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!
        val charlie = gameEngine.players.value.find { it.name == "Charlie" }!!

        // Set Alice and Bob to have no cards, Charlie has cards
        setPlayerHands(
            "Alice" to emptyList(),
            "Bob" to emptyList(),
            "Charlie" to createCards(listOf("AS", "KH"))
        )
        
        // Trigger a check, e.g. by trying to evaluate an empty round or advance turn
        gameEngine.determineNextPlayerOrEvaluate() // This should eventually call checkForBhabhi

        val charlieAfterCheck = gameEngine.players.value.find { it.id == charlie.id }!!
        assertTrue("Charlie should be Bhabhi", charlieAfterCheck.isBhabhi)
        assertEquals(GameState.GAME_OVER, gameEngine.gameState.value)
        assertTrue(gameEngine.gameMessage.value.contains("${charlie.name} is the Bhabhi!"))
        assertFalse("Alice should not be Bhabhi", gameEngine.players.value.find { it.id == alice.id }!!.isBhabhi)
    }

    // --- Special Rule 2: Take Hand ---
    @Test
    fun `attemptTakeHandFromLeft transfers cards and marks player as lost`() = runTest {
        val playerNames = listOf("Alice", "Bob", "Charlie") // Alice <- Bob <- Charlie <- Alice
        gameEngine.setupGame(playerNames)

        val alice = gameEngine.players.value.find { it.name == "Alice" }!! // Index 0
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!     // Index 1
        val charlie = gameEngine.players.value.find { it.name == "Charlie" }!! // Index 2

        // Alice has [AS], Bob has [KH, QH], Charlie has [JD]
        val aliceCards = createCards(listOf("AS"))
        val bobCards = createCards(listOf("KH", "QH"))
        val charlieCards = createCards(listOf("JD"))
        setPlayerHands("Alice" to aliceCards, "Bob" to bobCards, "Charlie" to charlieCards)
        
        // Bob (index 1) attempts to take hand from Alice (index 0)
        val bobIndex = gameEngine.players.value.indexOfFirst { it.id == bob.id }
        gameEngine.attemptTakeHandFromLeft(bobIndex)

        val aliceAfter = gameEngine.players.value.find { it.id == alice.id }!!
        val bobAfter = gameEngine.players.value.find { it.id == bob.id }!!

        assertTrue("Alice should have lost", aliceAfter.hasLost)
        assertTrue("Alice's hand should be empty", aliceAfter.hand.isEmpty())
        assertEquals("Bob should have 3 cards (KH, QH, AS)", 3, bobAfter.hand.size)
        assertTrue("Bob's hand should contain AS", bobAfter.hand.any { it.rank == Rank.ACE && it.suit == Suit.SPADES })
        assertTrue(gameEngine.gameMessage.value.contains("${bob.name} took ${aliceCards.size} cards from ${alice.name}"))
        assertEquals("Bob (taker) should be current player", bobIndex, gameEngine.currentPlayerIndex.value)
    }

    // --- Special Rule 4 & 4B: Player Out of Cards & Shoot-Out ---
    @Test
    fun `SR4 player wins trick with no cards left player to left leads`() = runTest {
        val playerNames = listOf("Alice", "Bob", "Charlie")
        gameEngine.setupGame(playerNames)

        val alice = gameEngine.players.value.find { it.name == "Alice" }!! // index 0
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!     // index 1
        val charlie = gameEngine.players.value.find { it.name == "Charlie" }!! // index 2

        // Alice has 7S (last card), Bob has KS, Charlie has 2D
        val aliceCards = createCards(listOf("7S"))
        val bobCards = createCards(listOf("KS"))
        val charlieCards = createCards(listOf("2D", "3H")) // Charlie needs cards to be next leader
        setPlayerHands("Alice" to aliceCards, "Bob" to bobCards, "Charlie" to charlieCards)

        // Alice starts and plays 7S (her last card)
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == alice.id } }
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, aliceCards.first()) // Alice plays 7S

        // Bob plays KS (wins the trick)
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, bobCards.first()) 
        // Bob wins, but let's assume Alice won the trick with her last card for SR4.
        // To test SR4 properly: Alice plays highest card and it's her last.
        // New Setup: Alice: KS (last), Bob: 7S. Alice plays KS.
        setPlayerHands("Alice" to createCards(listOf("KS")), "Bob" to createCards(listOf("7S")), "Charlie" to charlieCards)
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == alice.id } }
        gameEngine.currentPlayedCardsInfo.value = emptyList() // Clear trick

        gameEngine.playCard(gameEngine.currentPlayerIndex.value, createCards(listOf("KS")).first()) // Alice plays KS (last card)
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, createCards(listOf("7S")).first()) // Bob plays 7S

        // Alice won with KS, has no cards left. Bob (index 1) is to her left (if 2 players).
        // With 3 players, Charlie (index 2) is to Bob's left. Alice is 0, Bob is 1, Charlie is 2.
        // Alice (0) won. Player to her left is Bob (1). If Bob also empty, then Charlie (2).
        // In this setup, Alice won, her hand is empty. Bob is next.
        assertTrue(gameEngine.gameMessage.value.contains("${alice.name} won/leads but has no cards (SR4)"))
        assertEquals("Bob (to Alice's left) should lead next", gameEngine.players.value.indexOfFirst { it.id == bob.id }, gameEngine.currentPlayerIndex.value)
    }

    @Test
    fun `SR4B Shoot-Out initiates correctly`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)
        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!

        // Alice has 1 card (AS), Bob has multiple (KH, QH)
        setPlayerHands("Alice" to createCards(listOf("AS")), "Bob" to createCards(listOf("KH", "QH")))
        gameEngine.currentPlayerIndex.update { gameEngine.players.value.indexOfFirst { it.id == alice.id } } // Alice's turn

        // Alice plays her last card
        gameEngine.playCard(gameEngine.currentPlayerIndex.value, createCards(listOf("AS")).first())

        assertEquals(GameState.SHOOT_OUT_DRAWING, gameEngine.gameState.value) // Or SHOOT_OUT_SETUP then DRAWING
        assertTrue(gameEngine.gameMessage.value.contains("SR4B: Shoot-Out!"))
        assertEquals(alice.id, gameEngine.shootOutDrawingPlayerIdInternalTestHook) // Alice is drawing
        assertEquals(bob.id, gameEngine.shootOutRespondingPlayerIdInternalTestHook) // Bob is responding
    }

    // Need to expose shootOutDrawingPlayerId and shootOutRespondingPlayerId for testing,
    // or infer from game state and current player. Adding internal test hooks for now.
    val GameEngine.shootOutDrawingPlayerIdInternalTestHook: String? get() = this.shootOutDrawingPlayerId
    val GameEngine.shootOutRespondingPlayerIdInternalTestHook: String? get() = this.shootOutRespondingPlayerId


    @Test
    fun `SR4B Shoot-Out draw card works`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)
        val alice = gameEngine.players.value.find { it.name == "Alice" }!!
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!

        // Setup for shoot-out: Alice to draw, Bob to respond
        gameEngine.discardPile.update { createCards(listOf("2S", "3S", "4S", "5S")) } // Min 3 cards for draw + 2 left
        gameEngine.shootOutDrawingPlayerId = alice.id // Internal access for test
        gameEngine.shootOutRespondingPlayerId = bob.id // Internal access for test
        gameEngine.gameState.update { GameState.SHOOT_OUT_DRAWING }
        val initialAliceHandSize = alice.hand.size

        gameEngine.shootOutDrawCard(alice.id)

        assertEquals(GameState.SHOOT_OUT_RESPONDING, gameEngine.gameState.value)
        assertEquals(initialAliceHandSize + 1, gameEngine.players.value.find { it.id == alice.id }!!.hand.size)
        assertNotNull(gameEngine.shootOutDrawnCardInternalTestHook) // Check card was drawn
        assertTrue(gameEngine.gameMessage.value.contains("${alice.name} drew"))
    }
     val GameEngine.shootOutDrawnCardInternalTestHook: Card? get() = this.shootOutDrawnCard


    @Test
    fun `SR4B Shoot-Out Respond - Responder plays higher same suit - Responder is Bhabhi (Original rule implies responder wins, drawing player picks up / becomes Bhabhi)`() = runTest {
        // Rule interpretation: "If responding player plays a higher card of the same suit, drawing player is Bhabhi."
        // This means drawing player "loses" the shootout round.
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)
        val alice = gameEngine.players.value.find { it.name == "Alice" }!! // Drawing
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!   // Responding

        gameEngine.shootOutDrawingPlayerId = alice.id
        gameEngine.shootOutRespondingPlayerId = bob.id
        gameEngine.shootOutDrawnCard = Card(Suit.SPADES, Rank.SEVEN) // Alice drew 7S
        setPlayerHands("Bob"to createCards(listOf("KS", "2H"))) // Bob has KS
        gameEngine.gameState.update { GameState.SHOOT_OUT_RESPONDING }
        val bobInitialHandSize = bob.hand.size

        gameEngine.shootOutRespond(bob.id, Card(Suit.SPADES, Rank.KING)) // Bob plays KS (higher same suit)

        // Alice (drawing player) should be Bhabhi
        assertTrue(gameEngine.players.value.find { it.id == alice.id }!!.isBhabhi)
        assertEquals(GameState.GAME_OVER, gameEngine.gameState.value)
        assertTrue(gameEngine.gameMessage.value.contains("${bob.name} wins the Shoot-Out round!")) // Bob wins round
        assertTrue(gameEngine.gameMessage.value.contains("${alice.name} is the Bhabhi!")) // Alice is Bhabhi
        // Alice (loser of shootout) picks up the two cards from shootout
        assertEquals(1 /*original drawn card*/ + 2 /*shootout pile*/, gameEngine.players.value.find{it.id == alice.id}!!.hand.size)

    }
    
    @Test
    fun `SR4B Shoot-Out Respond - Responder plays different suit - Drawing player is Bhabhi`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)
        val alice = gameEngine.players.value.find { it.name == "Alice" }!! // Drawing
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!   // Responding

        gameEngine.shootOutDrawingPlayerId = alice.id
        gameEngine.shootOutRespondingPlayerId = bob.id
        gameEngine.shootOutDrawnCard = Card(Suit.SPADES, Rank.SEVEN) // Alice drew 7S
        setPlayerHands("Bob" to createCards(listOf("KH", "2D"))) // Bob has King of Hearts
        gameEngine.gameState.update { GameState.SHOOT_OUT_RESPONDING }

        gameEngine.shootOutRespond(bob.id, Card(Suit.HEARTS, Rank.KING)) // Bob plays KH (different suit)

        // Alice (drawing player) should be Bhabhi
        assertTrue(gameEngine.players.value.find { it.id == alice.id }!!.isBhabhi)
        assertEquals(GameState.GAME_OVER, gameEngine.gameState.value)
        assertTrue(gameEngine.gameMessage.value.contains("${bob.name} wins the Shoot-Out round!")) // Bob wins round by rule
        assertTrue(gameEngine.gameMessage.value.contains("${alice.name} is the Bhabhi!"))
    }

    @Test
    fun `SR4B Shoot-Out Respond - Responder plays lower same suit - Re-Shoot`() = runTest {
        val playerNames = listOf("Alice", "Bob")
        gameEngine.setupGame(playerNames)
        val alice = gameEngine.players.value.find { it.name == "Alice" }!! // Drawing
        val bob = gameEngine.players.value.find { it.name == "Bob" }!!   // Responding

        gameEngine.shootOutDrawingPlayerId = alice.id
        gameEngine.shootOutRespondingPlayerId = bob.id
        gameEngine.shootOutDrawnCard = Card(Suit.SPADES, Rank.KING) // Alice drew KS
        setPlayerHands("Bob" to createCards(listOf("7S", "2H"))) // Bob has 7S
        gameEngine.gameState.update { GameState.SHOOT_OUT_RESPONDING }
        val initialDiscardSize = gameEngine.discardPile.value.size

        gameEngine.shootOutRespond(bob.id, Card(Suit.SPADES, Rank.SEVEN)) // Bob plays 7S (lower same suit)

        assertEquals(GameState.SHOOT_OUT_DRAWING, gameEngine.gameState.value) // Re-shoot
        assertTrue(gameEngine.gameMessage.value.contains("Same rank! Re-Shoot!")) // Or "Lower same suit - Re-shoot"
        assertNull(gameEngine.shootOutDrawnCardInternalTestHook) // Drawn card should be cleared for next draw
        assertEquals(initialDiscardSize + 2, gameEngine.discardPile.value.size) // KS and 7S go to discard
        assertEquals("Alice should be drawing again", alice.id, gameEngine.shootOutDrawingPlayerIdInternalTestHook)
    }
}
