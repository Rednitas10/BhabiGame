package com.example.bhabhigameandroid.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bhabhigameandroid.FirebaseService
import com.example.bhabhigameandroid.UserFriend
import com.example.bhabhigameandroid.UserProfile
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch // For viewModelScope.launch

class FriendsViewModel : ViewModel() {

    val firebaseService = FirebaseService
    val localPlayerId = firebaseService.getCurrentUserId()
    private var localPlayerDisplayName: String? = null // To store current user's display name

    private val _friendsList = MutableStateFlow<List<UserFriend>>(emptyList())
    val friendsList: StateFlow<List<UserFriend>> = _friendsList.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val isConnected: StateFlow<Boolean> = firebaseService.isConnected // Expose connection state

    private var friendsListener: ListenerRegistration? = null

    init {
        if (localPlayerId != null) {
            listenForFriendUpdates()
            fetchLocalPlayerDisplayName(localPlayerId)
        } else {
            _error.value = "User not logged in."
        }
    }

    private fun fetchLocalPlayerDisplayName(userId: String) {
        firebaseService.getUserProfile(userId,
            onSuccess = { profile ->
                localPlayerDisplayName = profile?.displayName
                if (localPlayerDisplayName == null) {
                    Log.w("FriendsViewModel", "Local user display name not found.")
                    // Potentially set an error or default, but friend requests need it.
                }
            },
            onFailure = { e ->
                Log.e("FriendsViewModel", "Failed to fetch local user profile", e)
                _error.value = "Could not fetch your profile: ${e.message}"
            }
        )
    }

    fun listenForFriendUpdates() {
        if (localPlayerId == null) return
        friendsListener?.remove() // Remove previous listener
        friendsListener = firebaseService.listenToFriends(localPlayerId,
            onUpdate = { friends -> _friendsList.value = friends },
            onError = { e -> _error.value = "Error fetching friends: ${e.message}" }
        )
    }

    fun sendFriendRequest(targetUserProfile: UserProfile) {
        val currentUserId = localPlayerId
        val currentDisplayName = localPlayerDisplayName
        if (currentUserId == null || currentDisplayName == null) {
            _error.value = "Your display name is not set. Cannot send friend request."
            Log.e("FriendsViewModel", "sendFriendRequest: Local user ID or display name is null.")
            return
        }
        if (targetUserProfile.uid.isBlank()) {
            _error.value = "Target user ID is invalid."
            return
        }

        _error.value = null // Clear previous error
        firebaseService.sendFriendRequest(
            targetUserId = targetUserProfile.uid,
            currentUserDisplayName = currentDisplayName,
            targetUserDisplayName = targetUserProfile.displayName,
            onSuccess = {
                _error.value = "Friend request sent to ${targetUserProfile.displayName}." // Using error for feedback
                // Optionally, clear search results or update UI to reflect pending request
                _searchResults.value = _searchResults.value.filterNot { it.uid == targetUserProfile.uid } // Example: remove from search
            },
            onFailure = { e ->
                _error.value = "Failed to send friend request: ${e.message}"
                Log.e("FriendsViewModel", "sendFriendRequest failed", e)
            }
        )
    }

    fun acceptFriendRequest(requesterId: String) {
        _error.value = null
        firebaseService.acceptFriendRequest(requesterId,
            onSuccess = {
                _error.value = "Friend request accepted."
            },
            onFailure = { e ->
                _error.value = "Failed to accept friend request: ${e.message}"
            }
        )
    }

    fun declineOrCancelRequest(otherUserId: String) {
        _error.value = null
        firebaseService.declineOrCancelFriendRequest(otherUserId,
            onSuccess = {
                _error.value = "Friend request action completed."
            },
            onFailure = { e ->
                _error.value = "Failed to process request: ${e.message}"
            }
        )
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _error.value = null
        // Using whereEqualTo for exact match as per subtask.
        // Note: Firestore is case-sensitive for whereEqualTo.
        // For case-insensitive, you'd need to store a normalized (e.g., lowercase) version of displayName.
        firebaseService.db.collection("users")
            .whereEqualTo("displayName", query)
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                val users = documents.mapNotNull { it.toObject(UserProfile::class.java) }
                _searchResults.value = users.filter { it.uid != localPlayerId } // Exclude self
                if (_searchResults.value.isEmpty()) {
                    _error.value = "No users found with that name."
                }
            }
            .addOnFailureListener { e ->
                _error.value = "Search failed: ${e.message}"
                Log.e("FriendsViewModel", "User search failed", e)
            }
    }

    override fun onCleared() {
        super.onCleared()
        friendsListener?.remove()
    }
}
