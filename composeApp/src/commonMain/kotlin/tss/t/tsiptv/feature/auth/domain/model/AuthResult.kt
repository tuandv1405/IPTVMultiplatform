package tss.t.tsiptv.feature.auth.domain.model

import tss.t.tsiptv.core.firebase.FirebaseUser

/**
 * Sealed class representing the result of an authentication operation.
 */
sealed class AuthResult {
    /**
     * The authentication operation was successful.
     *
     * @property user The authenticated user
     * @property token The authentication token, if available
     */
    data class Success(
        val user: FirebaseUser,
        val token: AuthToken? = null
    ) : AuthResult()

    /**
     * The authentication operation failed.
     *
     * @property message The error message
     * @property exception The exception that caused the failure, if available
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : AuthResult()

    /**
     * The authentication operation is in progress.
     */
    object Loading : AuthResult()

    /**
     * The user was signed out.
     */
    object SignedOut : AuthResult()
}