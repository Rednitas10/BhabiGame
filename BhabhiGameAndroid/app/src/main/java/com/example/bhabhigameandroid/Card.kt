package com.example.bhabhigameandroid

import java.util.UUID

// Define an enum Suit
enum class Suit {
    SPADES, HEARTS, DIAMONDS, CLUBS
}

// Define an enum Rank with integer values
enum class Rank(val value: Int) {
    TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10),
    JACK(11), QUEEN(12), KING(13), ACE(14) // Ace high
}

// Define a data class Card
data class Card(
    val suit: Suit,
    val rank: Rank,
    val id: String = "${rank.name}_OF_${suit.name}", // Unique ID based on rank and suit
    var isPlayed: Boolean = false
) : Comparable<Card> {
    override fun compareTo(other: Card): Int {
        // Primary sort by rank value, secondary by suit (e.g., Spades > Hearts > Diamonds > Clubs)
        // This order is arbitrary for suits but provides a consistent sort.
        if (this.rank.value != other.rank.value) {
            return this.rank.value.compareTo(other.rank.value)
        }
        return this.suit.compareTo(other.suit) // Enum compareTo is by ordinal
    }
}
