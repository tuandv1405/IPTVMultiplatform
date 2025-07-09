package tss.t.tsiptv.core.googlesignin

import kotlinx.coroutines.flow.Flow

/**
 * Interface for Google Sign-In.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface GoogleSignIn {
    /**
     * The current user, or null if not signed in.
     */
    val currentUser: Flow<GoogleSignInUser?>

    /**
     * Signs in with Google.
     *
     * @param clientId The client ID from the Google API Console
     * @return The signed-in user
     * @throws GoogleSignInException if sign-in fails
     */
    suspend fun signIn(clientId: String): GoogleSignInUser

    /**
     * Signs out the current user.
     */
    suspend fun signOut()

    /**
     * Checks if a user is currently signed in.
     *
     * @return true if a user is signed in, false otherwise
     */
    suspend fun isSignedIn(): Boolean

    /**
     * Gets the current user, or null if not signed in.
     *
     * @return The current user, or null if not signed in
     */
    suspend fun getCurrentUser(): GoogleSignInUser?

    /**
     * Restores a previous sign-in.
     *
     * @return The signed-in user, or null if no previous sign-in was found
     */
    suspend fun restorePreviousSignIn(): GoogleSignInUser?
}

/**
 * Data class representing a Google Sign-In user.
 *
 * @property idToken The ID token
 * @property accessToken The access token
 * @property userId The user's unique ID
 * @property email The user's email, or null if not available
 * @property displayName The user's display name, or null if not available
 * @property photoUrl The URL of the user's profile photo, or null if not available
 */
data class GoogleSignInUser(
    val idToken: String,
    val accessToken: String,
    val userId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

/**
 * Exception thrown when a Google Sign-In operation fails.
 *
 * @property code The error code
 * @property message The error message
 */
class GoogleSignInException(val code: String, override val message: String) : Exception(message)