package tss.t.tsiptv.core.firebase

import kotlinx.coroutines.flow.Flow

/**
 * Interface for Firebase Authentication.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface IFirebaseAuth {
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

    suspend fun signInWithGoogle(idToken: String): FirebaseUser

    suspend fun signInWithFacebook(accessToken: String): FirebaseUser

    suspend fun signInWithApple(idToken: String): FirebaseUser

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
     * Updates the current user's display name.
     *
     * @param displayName The new display name
     * @throws FirebaseAuthException if updating the display name fails
     */
    suspend fun updateDisplayName(displayName: String)

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
    val isEmailVerified: Boolean,
)

/**
 * Exception thrown when a Firebase Authentication operation fails.
 *
 * @property code The error code
 * @property message The error message
 */
class FirebaseAuthException(val code: String, override val message: String) : Exception(message)