package com.example.bhabhigameandroid

import com.google.android.gms.tasks.TaskExecutors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.tasks.TaskExecutors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.TaskExecutors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

object FirebaseService {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions = Firebase.functions

    private val _isConnected = MutableStateFlow(true) // Assume connected initially
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val connected = snapshot.getValue(Boolean::class.java) ?: false
            _isConnected.value = connected
        }

        override fun onCancelled(error: DatabaseError) {
            _isConnected.value = false // Assume disconnected on error
        }
    }

    init {
        startConnectionListener()
    }

    private fun startConnectionListener() {
        val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
        connectedRef.addValueEventListener(connectionListener)
    }

    fun stopConnectionListener() { // Optional: call this if FirebaseService can be "stopped"
        val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
        connectedRef.removeEventListener(connectionListener)
    }


    fun signInAnonymously(onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        auth.signInAnonymously()
            .addOnCompleteListener(TaskExecutors.MAIN_THREAD) { task ->
                if (task.isSuccessful) {
                    val user: FirebaseUser? = auth.currentUser
                    user?.uid?.let {
                        onSuccess(it)
                    } ?: onFailure(Exception("User UID is null after successful anonymous sign-in."))
                } else {
                    task.exception?.let {
                        onFailure(it)
                    } ?: onFailure(Exception("Anonymous sign-in failed with no specific exception."))
                }
            }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun createRoom(
        roomName: String,
        hostDisplayName: String,
        maxPlayers: Int,
        access: String, // Should be RoomAccess.PUBLIC.name or RoomAccess.PRIVATE.name
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val hostPlayerId = getCurrentUserId()
        if (hostPlayerId == null) {
            onFailure(Exception("User not authenticated"))
            return
        }

        // Room data using a map to include FieldValue.serverTimestamp() directly
        val roomData = hashMapOf(
            "roomName" to roomName,
            "hostPlayerId" to hostPlayerId,
            "status" to RoomStatus.WAITING.name,
            "createdAt" to FieldValue.serverTimestamp(), // Firestore will set this on the server
            "maxPlayers" to maxPlayers,
            "currentPlayerCount" to 1,
            "access" to access
        )

        db.collection("rooms")
            .add(roomData)
            .addOnSuccessListener { documentReference ->
                val newRoomId = documentReference.id
                // Update the room document with its own ID for easier access if needed
                // Though not strictly necessary for this step, it's good practice
                documentReference.update("id", newRoomId)
                    .addOnSuccessListener {
                        // Now add the host as a player in the subcollection
                        val playerInRoomData = PlayerInRoom(
                            id = hostPlayerId,
                            displayName = hostDisplayName,
                            isHost = true,
                            status = PlayerStatus.JOINED.name, // Changed from CONNECTED
                            // joinedAt will be set by @ServerTimestamp in PlayerInRoom model
                        )

                        db.collection("rooms").document(newRoomId)
                            .collection("players").document(hostPlayerId)
                            .set(playerInRoomData) // Let Firebase handle joinedAt via @ServerTimestamp
                            .addOnSuccessListener { onSuccess(newRoomId) }
                            .addOnFailureListener { e -> onFailure(e) }
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToPublicRooms(
        onUpdate: (List<Room>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("rooms")
            .whereEqualTo("access", RoomAccess.PUBLIC.name)
            .whereEqualTo("status", RoomStatus.WAITING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val rooms = snapshot.documents.mapNotNull { document ->
                        // Manually mapping to Room, ensuring 'id' is set from document.id
                        val room = document.toObject(Room::class.java)
                        room?.copy(id = document.id) // Set the ID from the document
                    }
                    onUpdate(rooms)
                }
            }
    }

    fun listenToRoomDetails(
        roomId: String,
        onUpdate: (Room?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("rooms").document(roomId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val room = snapshot.toObject(Room::class.java)?.copy(id = snapshot.id)
                    onUpdate(room)
                } else {
                    onUpdate(null) // Room deleted or does not exist
                }
            }
    }

    fun joinRoom(
        roomId: String,
        playerDisplayName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            onFailure(Exception("User not authenticated"))
            return
        }

        val roomRef = db.collection("rooms").document(roomId)
        val playerRef = roomRef.collection("players").document(currentUserId)

        db.runTransaction { transaction ->
            val roomSnapshot = transaction.get(roomRef)
            if (!roomSnapshot.exists()) {
                throw FirebaseFirestoreException("Room not found", FirebaseFirestoreException.Code.NOT_FOUND)
            }

            val room = roomSnapshot.toObject(Room::class.java)
                ?: throw FirebaseFirestoreException("Failed to parse room data", FirebaseFirestoreException.Code.DATA_LOSS)

            if (room.status != RoomStatus.WAITING.name) {
                throw FirebaseFirestoreException("Game has already started or finished", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }

            if (room.currentPlayerCount >= room.maxPlayers) {
                throw FirebaseFirestoreException("Room is full", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }

            // Check if player is already in the room (idempotency)
            val playerSnapshot = transaction.get(playerRef)
            if (playerSnapshot.exists()) {
                // Player already joined, consider this a success or specific handling
                return@runTransaction // Or call onSuccess directly if this is acceptable
            }

            val playerInRoom = PlayerInRoom(
                id = currentUserId,
                displayName = playerDisplayName,
                isHost = false,
                status = PlayerStatus.JOINED.name
                // joinedAt will be set by @ServerTimestamp
            )

            transaction.set(playerRef, playerInRoom)
            transaction.update(roomRef, "currentPlayerCount", FieldValue.increment(1))
            null // Successful transaction returns null
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToPlayersInRoom(
        roomId: String,
        onUpdate: (List<PlayerInRoom>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("rooms").document(roomId).collection("players")
            .orderBy("joinedAt", Query.Direction.ASCENDING) // Order by joinedAt to maintain join order
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val players = snapshot.documents.mapNotNull { document ->
                        val player = document.toObject(PlayerInRoom::class.java)
                        player?.copy(id = document.id) // Ensure ID is set from document
                    }
                    onUpdate(players)
                }
            }
    }

    fun listenToGameState(
        roomId: String,
        onUpdate: (GameStateData) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val gameStateRef = db.collection("rooms").document(roomId)
            .collection("gameState").document("live") // Fixed ID for game state doc

        return gameStateRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                onError(e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val gameState = snapshot.toObject(GameStateData::class.java)
                if (gameState != null) {
                    onUpdate(gameState)
                } else {
                    // Data exists but couldn't be parsed, could be an error or schema mismatch
                    onError(Exception("Failed to parse game state data."))
                }
            } else {
                // Game state document doesn't exist yet, provide a default/initial state
                // This could be a new GameStateData() or a specific one indicating not started
                onUpdate(GameStateData(gameStatus = GameNetStatus.INITIALIZING.name))
            }
        }
    }

    fun sendGameAction(
        roomId: String,
        actionName: String,
        params: Map<String, Any>,
        onSuccess: (String?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "roomId" to roomId
        )
        data.putAll(params) // Add other parameters

        functions
            .getHttpsCallable(actionName)
            .call(data)
            .addOnCompleteListener(TaskExecutors.MAIN_THREAD) { task ->
                if (task.isSuccessful) {
                    val resultData = task.result?.data
                    val message = if (resultData is Map<*, *>) {
                        (resultData as? Map<String, Any>)?.get("message") as? String
                    } else {
                        resultData?.toString() // Or handle other types of results
                    }
                    onSuccess(message ?: "Action completed successfully.")
                } else {
                    val exception = task.exception
                    if (exception != null) {
                        onFailure(exception)
                    } else {
                        onFailure(Exception("Cloud function call failed with no specific exception."))
                    }
                }
            }
    }

    fun signalStartGame(roomId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        sendGameAction(roomId, "startGame", emptyMap(), onSuccess, onFailure)
    }

    fun signalPlayCard(roomId: String, cardNet: CardNet, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        val params = mapOf(
            "card" to mapOf(
                "suit" to cardNet.suit,
                "rank" to cardNet.rank
            )
        )
        sendGameAction(roomId, "playCard", params, onSuccess, onFailure)
    }

    fun signalTakeHandFromLeft(roomId: String, playerId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        sendGameAction(roomId, "takeHandFromLeft", mapOf("playerId" to playerId), onSuccess, onFailure)
    }

    fun signalShootOutDraw(roomId: String, playerId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        sendGameAction(roomId, "shootOutDraw", mapOf("playerId" to playerId), onSuccess, onFailure)
    }

    fun signalShootOutRespond(roomId: String, playerId: String, cardNet: CardNet, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        val params = mapOf(
            "playerId" to playerId,
            "card" to mapOf(
                "suit" to cardNet.suit,
                "rank" to cardNet.rank
            )
        )
        sendGameAction(roomId, "shootOutRespond", params, onSuccess, onFailure)
    }

    fun signalRestartGame(roomId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        sendGameAction(roomId, "restartGame", emptyMap(), onSuccess, onFailure)
    }

    // --- User Profile and Friends System ---

    fun createUserProfileDocument(
        userId: String,
        displayName: String,
        email: String? = null,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userProfile = UserProfile(
            uid = userId,
            displayName = displayName,
            email = email
            // createdAt and lastSeen will be handled by @ServerTimestamp
        )
        db.collection("users").document(userId)
            .set(userProfile) // Using set with merge might be better if profile can be partially updated later
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getUserProfile(
        userId: String,
        onSuccess: (UserProfile?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val userProfile = documentSnapshot.toObject(UserProfile::class.java)
                    onSuccess(userProfile)
                } else {
                    onSuccess(null) // Profile does not exist
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun sendFriendRequest(
        targetUserId: String,
        currentUserDisplayName: String, // For denormalizing into target's friend entry
        targetUserDisplayName: String,  // For denormalizing into current user's friend entry
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            onFailure(Exception("User not authenticated"))
            return
        }
        if (currentUserId == targetUserId) {
            onFailure(Exception("Cannot send friend request to yourself"))
            return
        }

        val currentUserFriendEntry = UserFriend(
            friendId = targetUserId,
            status = FriendStatus.PENDING_SENT.name,
            displayName = targetUserDisplayName // Denormalized name of the target user
            // 'since' will be set by @ServerTimestamp
        )
        val targetUserFriendEntry = UserFriend(
            friendId = currentUserId,
            status = FriendStatus.PENDING_RECEIVED.name,
            displayName = currentUserDisplayName // Denormalized name of the current user
            // 'since' will be set by @ServerTimestamp
        )

        val batch = db.batch()

        val currentUserFriendRef = db.collection("users").document(currentUserId)
            .collection("friends").document(targetUserId)
        batch.set(currentUserFriendRef, currentUserFriendEntry)

        val targetUserFriendRef = db.collection("users").document(targetUserId)
            .collection("friends").document(currentUserId)
        batch.set(targetUserFriendRef, targetUserFriendEntry)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun acceptFriendRequest(
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            onFailure(Exception("User not authenticated"))
            return
        }

        val batch = db.batch()

        val currentUserFriendRef = db.collection("users").document(currentUserId)
            .collection("friends").document(requesterId)
        batch.update(currentUserFriendRef, "status", FriendStatus.FRIENDS.name)

        val requesterUserFriendRef = db.collection("users").document(requesterId)
            .collection("friends").document(currentUserId)
        batch.update(requesterUserFriendRef, "status", FriendStatus.FRIENDS.name)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun declineOrCancelFriendRequest(
        otherUserId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            onFailure(Exception("User not authenticated"))
            return
        }

        val batch = db.batch()

        val currentUserFriendRef = db.collection("users").document(currentUserId)
            .collection("friends").document(otherUserId)
        batch.delete(currentUserFriendRef)

        val otherUserFriendRef = db.collection("users").document(otherUserId)
            .collection("friends").document(currentUserId)
        batch.delete(otherUserFriendRef)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToFriends(
        userId: String,
        onUpdate: (List<UserFriend>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("users").document(userId).collection("friends")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val friendsList = snapshot.documents.mapNotNull { document ->
                        document.toObject(UserFriend::class.java)
                        // Note: displayName in UserFriend might be null initially
                        // if not set during request/acceptance or if profile names change.
                        // A more robust solution might fetch current display names separately
                        // or use cloud functions to keep them updated.
                    }
                    onUpdate(friendsList)
                }
            }
    }

    // --- Game Invite System ---

    fun sendGameInvite(
        inviteeId: String,
        inviterName: String, // Current user's display name
        roomId: String,
        roomName: String,
        onSuccess: (String) -> Unit, // Returns inviteId
        onFailure: (Exception) -> Unit
    ) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            onFailure(Exception("User not authenticated for sending invite"))
            return
        }
        if (currentUserId == inviteeId) {
            onFailure(Exception("Cannot invite yourself to a game"))
            return
        }

        val newInviteRef = db.collection("invites").document()
        val gameInvite = GameInvite(
            id = newInviteRef.id,
            inviterId = currentUserId,
            inviterName = inviterName,
            inviteeId = inviteeId,
            roomId = roomId,
            roomName = roomName,
            status = GameInviteStatus.PENDING.name
            // createdAt will be set by @ServerTimestamp
        )

        newInviteRef.set(gameInvite)
            .addOnSuccessListener { onSuccess(newInviteRef.id) }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun acceptGameInvite(
        inviteId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("invites").document(inviteId)
            .update("status", GameInviteStatus.ACCEPTED.name)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun declineGameInvite(
        inviteId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("invites").document(inviteId)
            .update("status", GameInviteStatus.DECLINED.name)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToGameInvites(
        userId: String, // This is the inviteeId
        onUpdate: (List<GameInvite>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("invites")
            .whereEqualTo("inviteeId", userId)
            .whereEqualTo("status", GameInviteStatus.PENDING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val invites = snapshot.documents.mapNotNull { document ->
                        document.toObject(GameInvite::class.java)?.copy(id = document.id)
                    }
                    onUpdate(invites)
                }
            }
    }
}

// Helper class for Firestore exceptions within transactions if needed
class FirebaseFirestoreException(message: String, val code: Code) : Exception(message) {
    enum class Code {
        NOT_FOUND,
        FAILED_PRECONDITION,
        DATA_LOSS
        // Add other relevant codes as needed
    }
}
