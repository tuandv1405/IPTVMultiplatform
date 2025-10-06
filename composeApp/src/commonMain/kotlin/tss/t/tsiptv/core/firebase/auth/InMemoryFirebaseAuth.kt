package tss.t.tsiptv.core.firebase.auth

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException
import tss.t.tsiptv.core.firebase.models.FirebaseUser

/**
 * A simple in-memory implementation of IFirebaseAuth.
 * This implementation doesn't actually interact with Firebase, but provides a basic structure
 * that can be extended by platform-specific implementations.
 */
@VisibleForTesting
class InMemoryFirebaseAuth : IFirebaseAuth {
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    override val currentUser: Flow<FirebaseUser?> = _currentUser

    private val users = mutableMapOf<String, FirebaseUser>()
    private val passwords = mutableMapOf<String, String>()

    override suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        val user = users.values.find { it.email == email }
            ?: throw FirebaseAuthException("auth/user-not-found", "No user found with email $email")

        if (passwords[user.uid] != password) {
            throw FirebaseAuthException("auth/wrong-password", "Wrong password")
        }

        _currentUser.value = user
        return user
    }

    override suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        TODO("Not yet implemented")
    }

    override suspend fun signInWithFacebook(accessToken: String): FirebaseUser {
        TODO("Not yet implemented")
    }

    override suspend fun signInWithApple(idToken: String): FirebaseUser {
        TODO("Not yet implemented")
    }

    override suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String,
    ): FirebaseUser {
        if (users.values.any { it.email == email }) {
            throw FirebaseAuthException("auth/email-already-in-use", "Email already in use")
        }

        val uid = "user_${users.size + 1}"
        val user = FirebaseUser(
            uid = uid,
            email = email,
            displayName = null,
            photoUrl = null,
            isEmailVerified = false
        )

        users[uid] = user
        passwords[uid] = password
        _currentUser.value = user

        return user
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        val user = users.values.find { it.email == email }
            ?: throw FirebaseAuthException("auth/user-not-found", "No user found with email $email")

        // In a real implementation, this would send an email
        // For now, we'll just do nothing
    }

    override suspend fun updateEmail(email: String) {
        val user = _currentUser.value
            ?: throw FirebaseAuthException("auth/no-current-user", "No current user")

        if (users.values.any { it.email == email && it.uid != user.uid }) {
            throw FirebaseAuthException("auth/email-already-in-use", "Email already in use")
        }

        val updatedUser = user.copy(email = email)
        users[user.uid] = updatedUser
        _currentUser.value = updatedUser
    }

    override suspend fun updateDisplayName(displayName: String) {
        val user = _currentUser.value
            ?: throw FirebaseAuthException("auth/no-current-user", "No current user")
        val updatedUser = user.copy(displayName = displayName)
        users[user.uid] = updatedUser
        _currentUser.value = updatedUser
    }

    override suspend fun updatePassword(password: String) {
        val user = _currentUser.value
            ?: throw FirebaseAuthException("auth/no-current-user", "No current user")

        passwords[user.uid] = password
    }

    override suspend fun deleteUser() {
        val user = _currentUser.value
            ?: throw FirebaseAuthException("auth/no-current-user", "No current user")

        users.remove(user.uid)
        passwords.remove(user.uid)
        _currentUser.value = null
    }
}