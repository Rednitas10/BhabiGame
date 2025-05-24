package com.example.bhabhigameandroid

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

// Represents a game room in Firestore
@IgnoreExtraProperties
data class Room(
    val id: String = "", // Document ID
    val roomName: String = "",
    val hostPlayerId: String = "",
    val status: String = RoomStatus.WAITING.name, // e.g., WAITING, IN_PROGRESS, FINISHED
    @ServerTimestamp val createdAt: Timestamp? = null,
    val maxPlayers: Int = 4,
    var currentPlayerCount: Int = 0,
    val access: String = RoomAccess.PUBLIC.name // e.g., PUBLIC, PRIVATE
) {
    // Default constructor for Firebase
    constructor() : this("", "", "", RoomStatus.WAITING.name, null, 4, 0, RoomAccess.PUBLIC.name)
}

enum class RoomStatus {
    WAITING,
    IN_PROGRESS,
    FINISHED,
    CANCELLED
}

enum class RoomAccess {
    PUBLIC,
    PRIVATE // Potentially with a password or invite code in the future
}

// Represents a player within a game room in Firestore (sub-collection of Room)
@IgnoreExtraProperties
data class PlayerInRoom(
    val id: String = "", // User ID (from Firebase Auth)
    val displayName: String = "Guest",
    @ServerTimestamp val joinedAt: Timestamp? = null,
    val isHost: Boolean = false,
    val status: String = PlayerStatus.JOINED.name // e.g., JOINED, READY, LEFT, KICKED
) {
    // Default constructor for Firebase
    constructor() : this("", "Guest", null, false, PlayerStatus.JOINED.name)
}

enum class PlayerStatus {
    JOINED,
    READY,
    PLAYING, // Actively in the game round
    SPECTATING, // Joined but not playing (e.g. late join)
    LEFT, // Voluntarily left
    KICKED // Kicked by host
}


// Network-friendly representation of a Card
@IgnoreExtraProperties
data class CardNet(
    val suit: String = "", // e.g., "SPADES", "HEARTS"
    val rank: String = ""  // e.g., "ACE", "TWO", "KING"
) {
    // Default constructor for Firebase
    constructor() : this("", "")

    @Exclude
    fun toDomainCard(): Card {
        return Card(Suit.valueOf(suit.uppercase()), Rank.valueOf(rank.uppercase()))
    }
}

// Network-friendly representation of PlayedCardInfo
@IgnoreExtraProperties
data class PlayedCardInfoNet(
    val card: CardNet = CardNet(),
    val playerId: String = ""
) {
    // Default constructor for Firebase
    constructor() : this(CardNet(), "")

    @Exclude
    fun toDomainPlayedCardInfo(): PlayedCardInfo {
        return PlayedCardInfo(card.toDomainCard(), playerId)
    }
}

// Represents the overall game state in Firestore (sub-collection of Room)
@IgnoreExtraProperties
data class GameStateData(
    val id: String = "current_game", // Usually a single document per room
    val currentPlayerIndex: Int = 0,
    val currentPlayedCards: List<PlayedCardInfoNet> = emptyList(),
    val playerHands: Map<String, List<CardNet>> = emptyMap(), // Key: PlayerID
    val playerDisplayNames: Map<String, String> = emptyMap(), // Key: PlayerID, Value: Display Name
    val discardPile: List<CardNet> = emptyList(),
    val gameMessage: String = "Game starting...",
    val isBhabhiPlayerId: String? = null,
    val playersWhoLost: List<String> = emptyList(), // List of PlayerIDs who have lost (not necessarily the Bhabhi)
    val playerTurnOrder: List<String> = emptyList(), // List of PlayerIDs
    val gameStatus: String = GameNetStatus.DEALING.name, // e.g., DEALING, PLAYER_TURN, TRICK_END, GAME_OVER
    val lastTrickWinnerId: String? = null
) {
    // Default constructor for Firebase
    constructor() : this(
        "current_game", 0, emptyList(), emptyMap(), emptyMap(), emptyList(),
        "Game starting...", null, emptyList(), emptyList(), GameNetStatus.DEALING.name, null
    )
}

enum class GameNetStatus {
    INITIALIZING, // Game object created, waiting for players or setup
    WAITING,      // Room created, waiting for enough players to start
    DEALING,
    PLAYER_TURN,
    TRICK_COLLECTING, // Cards are being collected by a player (optional explicit state)
    EVALUATING_TRICK, // Server is evaluating the trick (optional explicit state)
    TRICK_END, // Trick finished, preparing for next lead or checking win condition
    SHOOT_OUT_DRAWING,
    SHOOT_OUT_RESPONDING,
    GAME_OVER,
    PAUSED // Future: if a player disconnects mid-game
}

// Helper to convert domain Card to CardNet
fun Card.toCardNet(): CardNet {
    return CardNet(suit = this.suit.name, rank = this.rank.name)
}

// Helper to convert domain PlayedCardInfo to PlayedCardInfoNet
fun PlayedCardInfo.toPlayedCardInfoNet(): PlayedCardInfoNet {
    return PlayedCardInfoNet(card = this.card.toCardNet(), playerId = this.playerId)
}

// --- Friend System Models ---

enum class FriendStatus {
    PENDING_SENT,     // Request sent by local user to another user
    PENDING_RECEIVED, // Request received by local user from another user
    FRIENDS,          // Both users have accepted the friend request
    BLOCKED           // For future use (e.g., user blocks another user)
}

@IgnoreExtraProperties
data class UserFriend(
    val friendId: String = "", // UID of the friend
    @ServerTimestamp var since: Timestamp? = null, // Timestamp of when the friendship/request was initiated or accepted
    var status: String = FriendStatus.PENDING_SENT.name, // Current status of the friendship
    var displayName: String? = null // Denormalized display name of the friend for easier UI rendering
) {
    // Default constructor for Firebase
    constructor() : this("", null, FriendStatus.PENDING_SENT.name, null)
}

@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "", // User's unique ID from Firebase Auth
    var displayName: String = "", // User's chosen display name
    var email: String? = null, // Optional: user's email
    @ServerTimestamp val createdAt: Timestamp? = null, // When the profile was created
    @ServerTimestamp var lastSeen: Timestamp? = null // For presence indication
    // Add other public profile info here if needed (e.g., avatar URL)
) {
    // Default constructor for Firebase
    constructor() : this("", "", null, null, null)
}

// --- Game Invite System Models ---

enum class GameInviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED, // For future use
    CANCELED // If inviter cancels
}

@IgnoreExtraProperties
data class GameInvite(
    val id: String = "", // Document ID
    val inviterId: String = "",
    val inviterName: String = "", // Denormalized
    val inviteeId: String = "",
    val roomId: String = "",
    val roomName: String = "", // Denormalized
    @ServerTimestamp var createdAt: Timestamp? = null,
    var status: String = GameInviteStatus.PENDING.name
) {
    // Default constructor for Firebase
    constructor() : this("", "", "", "", "", "", null, GameInviteStatus.PENDING.name)
}
