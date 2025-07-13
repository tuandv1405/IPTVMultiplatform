package tss.t.tsiptv.feature.auth.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.FirebaseAuthException
import tss.t.tsiptv.core.firebase.FirebaseUser
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.model.AuthState
import tss.t.tsiptv.feature.auth.domain.model.AuthToken
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository

/**
 * Implementation of the AuthRepository interface.
 *
 * @property firebaseAuth The Firebase Authentication service
 * @property keyValueStorage The key-value storage for persisting authentication tokens
 */
class AuthRepositoryImpl(
    private val firebaseAuth: IFirebaseAuth,
    private val keyValueStorage: KeyValueStorage,
) : AuthRepository {

    companion object {
        private const val KEY_ACCESS_TOKEN = "auth_access_token"
        private const val KEY_REFRESH_TOKEN = "auth_refresh_token"
        private const val KEY_TOKEN_EXPIRES_IN = "auth_token_expires_in"
        private const val KEY_TOKEN_CREATED_AT = "auth_token_created_at"
    }

    private val _authState = MutableStateFlow(AuthState())
    override val authState: Flow<AuthState> = _authState

    init {
        CoroutineScope(Dispatchers.IO).launch {
            firebaseAuth.currentUser.collect { user ->
                println(user)
                if (user != null) {
                    val token = getAuthToken()
                    _authState.value = AuthState(
                        isAuthenticated = true,
                        user = user,
                        authToken = token,
                        isLoading = false
                    )
                } else {
                    _authState.value = AuthState(
                        isAuthenticated = false,
                        user = null,
                        authToken = null,
                        isLoading = false
                    )
                }
            }
        }
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val user = firebaseAuth.signInWithEmailAndPassword(email, password)

            // For demonstration purposes, we'll create a dummy token
            // In a real app, this would come from your backend
            val token = AuthToken(
                accessToken = "dummy_access_token_${Clock.System.now().toEpochMilliseconds()}",
                refreshToken = "dummy_refresh_token_${Clock.System.now().toEpochMilliseconds()}",
                expiresIn = 3600 // 1 hour
            )

            saveAuthToken(token)

            _authState.value = AuthState(
                isAuthenticated = true,
                user = user,
                authToken = token,
                isLoading = false
            )

            AuthResult.Success(user, token)
        } catch (e: FirebaseAuthException) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = e.message
            )
            AuthResult.Error(e.message, e)
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = e.message ?: "Unknown error"
            )
            AuthResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val user = firebaseAuth.createUserWithEmailAndPassword(email, password)

            // For demonstration purposes, we'll create a dummy token
            // In a real app, this would come from your backend
            val token = AuthToken(
                accessToken = "dummy_access_token_${Clock.System.now().toEpochMilliseconds()}",
                refreshToken = "dummy_refresh_token_${Clock.System.now().toEpochMilliseconds()}",
                expiresIn = 3600 // 1 hour
            )

            saveAuthToken(token)

            _authState.value = AuthState(
                isAuthenticated = true,
                user = user,
                authToken = token,
                isLoading = false
            )

            AuthResult.Success(user, token)
        } catch (e: FirebaseAuthException) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = e.message
            )
            AuthResult.Error(e.message, e)
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = e.message ?: "Unknown error"
            )
            AuthResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun signInWithGoogle(): AuthResult {
        // In a real app, this would use the platform-specific Google Sign-In SDK
        // For now, we'll just return an error
        _authState.value = _authState.value.copy(
            isLoading = false,
            error = "Google Sign-In not implemented yet"
        )
        return AuthResult.Error("Google Sign-In not implemented yet")
    }

    override suspend fun signInWithApple(): AuthResult {
        // In a real app, this would use the platform-specific Apple Sign-In SDK
        // For now, we'll just return an error
        _authState.value = _authState.value.copy(
            isLoading = false,
            error = "Apple Sign-In not implemented yet"
        )
        return AuthResult.Error("Apple Sign-In not implemented yet")
    }

    override suspend fun signOut(): AuthResult {
        return try {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            firebaseAuth.signOut()
            clearAuthToken()

            _authState.value = AuthState(
                isAuthenticated = false,
                user = null,
                authToken = null,
                isLoading = false
            )

            AuthResult.SignedOut
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = e.message ?: "Unknown error"
            )
            AuthResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun refreshTokenIfNeeded(): AuthResult {
        val token = getAuthToken() ?: return AuthResult.Error("No token to refresh")

        if (!token.shouldRefresh()) {
            return AuthResult.Success(_authState.value.user!!, token)
        }

        // In a real app, this would call your backend to refresh the token
        // For now, we'll just create a new dummy token
        return try {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val newToken = AuthToken(
                accessToken = "dummy_access_token_${Clock.System.now().toEpochMilliseconds()}",
                refreshToken = token.refreshToken, // Keep the same refresh token
                expiresIn = 3600 // 1 hour
            )

            saveAuthToken(newToken)

            _authState.value = _authState.value.copy(
                authToken = newToken,
                isLoading = false
            )

            AuthResult.Success(_authState.value.user!!, newToken)
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = e.message ?: "Unknown error"
            )
            AuthResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun getAuthToken(): AuthToken? {
        val accessToken = keyValueStorage.getString(KEY_ACCESS_TOKEN, "")
        val refreshToken = keyValueStorage.getString(KEY_REFRESH_TOKEN, "")
        val expiresIn = keyValueStorage.getLong(KEY_TOKEN_EXPIRES_IN, 0)
        val createdAt = keyValueStorage.getLong(KEY_TOKEN_CREATED_AT, 0)

        if (accessToken.isEmpty() || refreshToken.isEmpty() || expiresIn == 0L || createdAt == 0L) {
            return null
        }

        return AuthToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            createdAt = createdAt
        )
    }

    override suspend fun getCurrentUser(): FirebaseUser? {
        return _authState.value.user
    }

    override suspend fun isAuthenticated(): Boolean {
        return _authState.value.isAuthenticated
    }

    /**
     * Saves the authentication token to persistent storage.
     *
     * @param token The token to save
     */
    private suspend fun saveAuthToken(token: AuthToken) {
        keyValueStorage.putString(KEY_ACCESS_TOKEN, token.accessToken)
        keyValueStorage.putString(KEY_REFRESH_TOKEN, token.refreshToken)
        keyValueStorage.putLong(KEY_TOKEN_EXPIRES_IN, token.expiresIn)
        keyValueStorage.putLong(KEY_TOKEN_CREATED_AT, token.createdAt)
    }

    /**
     * Clears the authentication token from persistent storage.
     */
    private suspend fun clearAuthToken() {
        keyValueStorage.remove(KEY_ACCESS_TOKEN)
        keyValueStorage.remove(KEY_REFRESH_TOKEN)
        keyValueStorage.remove(KEY_TOKEN_EXPIRES_IN)
        keyValueStorage.remove(KEY_TOKEN_CREATED_AT)
    }

    override suspend fun isTokenExpired(): Boolean {
        return getAuthToken()?.isExpired() ?: true
    }
}
