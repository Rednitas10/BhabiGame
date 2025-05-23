package com.example.bhabhigameandroid

import kotlin.random.Random

// Define a class Deck
class Deck {
    // Add a property cards: MutableList<Card> initialized as an empty list.
    var cards: MutableList<Card> = mutableListOf()

    // Implement an initializer (init block or secondary constructor) that populates the
    // cards list with a standard 52-card deck (one of each rank for each suit).
    init {
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                cards.add(Card(suit, rank))
            }
        }
    }

    // Implement a method shuffle() that randomizes the order of cards in the cards list.
    fun shuffle() {
        cards.shuffle(Random(System.nanoTime())) // Seed for better randomness if needed
    }

    // Implement a method deal(players: List<Player>): Boolean. This method should:
    // Distribute the cards from the deck as evenly as possible among the players.
    // Handle cases where the number of cards doesn't divide evenly.
    // Clear the cards in the deck after dealing.
    // Return true if dealing was successful, false otherwise (e.g., no players, no cards).
    fun deal(players: List<Player>): Boolean {
        if (players.isEmpty() || cards.isEmpty()) {
            return false
        }

        shuffle() // Shuffle before dealing

        var playerIndex = 0
        while (cards.isNotEmpty()) {
            val cardToDeal = cards.removeAt(0) // Take from the "top" of the deck
            players[playerIndex].hand.add(cardToDeal)
            playerIndex = (playerIndex + 1) % players.size
        }
        // cards list is now empty
        return true
    }

    // Add a method drawRandomCard(): Card? that removes and returns a random card
    // from the deck (useful for Shoot-Out rule). If the deck is empty, it returns null.
    fun drawRandomCard(): Card? {
        if (cards.isEmpty()) {
            return null
        }
        val randomIndex = Random.nextInt(cards.size)
        return cards.removeAt(randomIndex)
    }

    // Optional: A way to reset the deck if needed for a new game without creating a new Deck instance
    fun resetDeck() {
        cards.clear()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                cards.add(Card(suit, rank))
            }
        }
        shuffle()
    }
}
