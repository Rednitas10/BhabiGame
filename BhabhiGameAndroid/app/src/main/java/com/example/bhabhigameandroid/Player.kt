package com.example.bhabhigameandroid

import java.util.UUID

// Define a data class Player
data class Player(
    // id: String (e.g., UUID.randomUUID().toString()) - Kept for now for local game compatibility
    val id: String = UUID.randomUUID().toString(),

    // uid: String - Firebase User ID, primary identifier for network play
    val uid: String = id, // Defaults to id if not specified, useful for local/bot players

    // name: String
    val name: String,

    // hand: MutableList<Card> (initialized as an empty mutable list)
    val hand: MutableList<Card> = mutableListOf(),

    // isBhabhi: Boolean = false (defaults to false)
    var isBhabhi: Boolean = false,

    // hasLost: Boolean = false (alternative to isBhabhi, or can be used to mark players
    // who are out but not necessarily the Bhabhi yet)
    var hasLost: Boolean = false,

    // Add isBot property
    val isBot: Boolean = false,

    // isLocal: Boolean = false - Indicates if this player is the local user
    val isLocal: Boolean = false
) {
    // Optional: Add methods for player actions if needed later,
    // e.g., playCard, drawCard (though drawing is usually from deck or pile)
    // For now, properties are sufficient as per requirements.
}
