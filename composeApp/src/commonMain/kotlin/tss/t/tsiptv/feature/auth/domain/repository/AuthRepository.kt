package tss.t.tsiptv.feature.auth.domain.repository

import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.firebase.models.DeactivationRequest
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.model.AuthState
import tss.t.tsiptv.feature.auth.domain.model.AuthToken

/**
 * Repository interface for authentication operations.
 */
interface AuthRepository {
    /**
     * The current authentication state as a Flow.
     */
    val authState: Flow<AuthState>

    /**
     * Signs in with email and password.
     *
     * @param email The user's email
     * @param password The user's password
     * @return The result of the sign-in operation
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult

    /**
     * Creates a new user with email and password.
     *
     * @param email The user's email
     * @param password The user's password
     * @return The result of the registration operation
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult

    /**
     * Signs in with Google.
     *
     * @return The result of the sign-in operation
     */
    suspend fun signInWithGoogle(): AuthResult

    /**
     * Signs in with Apple.
     *
     * @return The result of the sign-in operation
     */
    suspend fun signInWithApple(): AuthResult

    /**
     * Signs out the current user.
     *
     * @return The result of the sign-out operation
     */
    suspend fun signOut(): AuthResult

    /**
     * Refreshes the access token if it's expired or about to expire.
     *
     * @return The result of the refresh operation
     */
    suspend fun refreshTokenIfNeeded(): AuthResult

    /**
     * Gets the current authentication token.
     *
     * @return The current authentication token, or null if not authenticated
     */
    suspend fun getAuthToken(): AuthToken?

    /**
     * Gets the current authenticated user.
     *
     * @return The current authenticated user, or null if not authenticated
     */
    suspend fun getCurrentUser(): FirebaseUser?

    /**
     * Checks if the current user is authenticated.
     *
     * @return True if the user is authenticated, false otherwise
     */
    suspend fun isAuthenticated(): Boolean


    /**
     * Checks if the access token is expired.
     *
     * @return True if the token is expired, false otherwise
     */
    suspend fun isTokenExpired(): Boolean

    /**
     * Creates a deactivation request for the current user.
     *
     * @param reason Optional reason for deactivation
     * @return The result of the deactivation request operation
     */
    suspend fun createDeactivationRequest(reason: String? = null): AuthResult

    /**
     * Gets the current user's deactivation request.
     *
     * @return The deactivation request or null if not found
     */
    suspend fun getDeactivationRequest(): DeactivationRequest?

    /**
     * Observes the current user's deactivation request.
     *
     * @return Flow of deactivation request or null if not found
     */
    fun observeDeactivationRequest(): Flow<DeactivationRequest?>

    /**
     * Cancels the current user's deactivation request.
     *
     * @return The result of the cancellation operation
     */
    suspend fun cancelDeactivationRequest(): AuthResult
}
