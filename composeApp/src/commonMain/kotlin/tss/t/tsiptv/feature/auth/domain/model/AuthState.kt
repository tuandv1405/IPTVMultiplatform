package tss.t.tsiptv.feature.auth.domain.model

import tss.t.tsiptv.core.firebase.FirebaseUser

/**
 * Data class representing the authentication state of the user.
 *
 * @property isAuthenticated Whether the user is authenticated
 * @property user The authenticated user, or null if not authenticated
 * @property authToken The authentication token, or null if not authenticated
 * @property isLoading Whether an authentication operation is in progress
 * @property error The error message from the last authentication operation, or null if no error
 */
data class AuthState(
    val isAuthenticated: Boolean = false,
    val user: FirebaseUser? = null,
    val authToken: AuthToken? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)