package tss.t.tsiptv.core.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Interface for Firebase Authentication.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface FirebaseAuth {
    /**
     * The current user, or null if not signed in.
     */
    val currentUser: Flow<FirebaseUser?>

    /**
     * Signs in with email and password.
     *
     * @param email The user's email
     * @param password The user's password
     * @return The signed-in user
     * @throws FirebaseAuthException if sign-in fails
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser

    /**
     * Creates a new user with email and password.
     *
     * @param email The user's email
     * @param password The user's password
     * @return The newly created user
     * @throws FirebaseAuthException if user creation fails
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): FirebaseUser

    /**
     * Signs out the current user.
     */
    suspend fun signOut()

    /**
     * Sends a password reset email.
     *
     * @param email The email to send the password reset to
     * @throws FirebaseAuthException if sending the email fails
     */
    suspend fun sendPasswordResetEmail(email: String)

    /**
     * Updates the current user's email.
     *
     * @param email The new email
     * @throws FirebaseAuthException if updating the email fails
     */
    suspend fun updateEmail(email: String)

    /**
     * Updates the current user's password.
     *
     * @param password The new password
     * @throws FirebaseAuthException if updating the password fails
     */
    suspend fun updatePassword(password: String)

    /**
     * Deletes the current user.
     *
     * @throws FirebaseAuthException if deleting the user fails
     */
    suspend fun deleteUser()
}

/**
 * Data class representing a Firebase user.
 *
 * @property uid The user's unique ID
 * @property email The user's email, or null if not available
 * @property displayName The user's display name, or null if not available
 * @property photoUrl The URL of the user's profile photo, or null if not available
 * @property isEmailVerified Whether the user's email is verified
 */
data class FirebaseUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isEmailVerified: Boolean
)

/**
 * Exception thrown when a Firebase Authentication operation fails.
 *
 * @property code The error code
 * @property message The error message
 */
class FirebaseAuthException(val code: String, override val message: String) : Exception(message)

/**
 * A simple in-memory implementation of FirebaseAuth.
 * This implementation doesn't actually interact with Firebase, but provides a basic structure
 * that can be extended by platform-specific implementations.
 */
class InMemoryFirebaseAuth : FirebaseAuth {
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

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): FirebaseUser {
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
