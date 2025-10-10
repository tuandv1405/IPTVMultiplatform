package tss.t.tsiptv.feature.auth.data.repository

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException
import tss.t.tsiptv.core.firebase.exceptions.FirebaseFirestoreException
import tss.t.tsiptv.core.firebase.models.DeactivationRequest
import tss.t.tsiptv.core.firebase.models.DeactivationStatus
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.model.AuthResult.Success
import tss.t.tsiptv.feature.auth.domain.model.AuthState
import tss.t.tsiptv.feature.auth.domain.model.AuthToken
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Implementation of the AuthRepository interface.
 *
 * @property firebaseAuth The Firebase Authentication service
 * @property firestore The Firebase Firestore service
 * @property keyValueStorage The key-value storage for persisting authentication tokens
 */
class AuthRepositoryImpl(
    private val firebaseAuth: IFirebaseAuth,
    private val keyValueStorage: KeyValueStorage,
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : AuthRepository {

    companion object {
        private const val COLLECTION_DEACTIVE_REQUESTS = "users"
        private const val SUBCOLLECTION_DEACTIVE_REQUESTS = "deactiveRequest"
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
                println("User: $user")
                if (user != null) {
                    val token = getAuthToken()
                    println("Token: $token")
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

    @OptIn(ExperimentalTime::class)
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

            Success(user, token)
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

    @OptIn(ExperimentalTime::class)
    override suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String,
    ): AuthResult {
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

            Success(user, token)
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

    @OptIn(ExperimentalTime::class)
    override suspend fun refreshTokenIfNeeded(): AuthResult {
        val token = getAuthToken() ?: return AuthResult.Error("No token to refresh")

        if (!token.shouldRefresh()) {
            return Success(_authState.value.user!!, token)
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

            Success(_authState.value.user!!, newToken)
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

    @OptIn(ExperimentalTime::class)
    override suspend fun createDeactivationRequest(reason: String?): AuthResult {
        try {
            val deactivationRequest = DeactivationRequest(
                userId = getCurrentUser()?.uid!!,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                reason = reason,
                email = getCurrentUser()?.email
            )
            val docRef = firestore
                .collection(COLLECTION_DEACTIVE_REQUESTS)
                .document(deactivationRequest.userId)
                .collection(SUBCOLLECTION_DEACTIVE_REQUESTS)
                .document(deactivationRequest.userId)


            val firestoreData = deactivationRequest.toFirestoreData()
            docRef.set(firestoreData)
        } catch (e: Exception) {
            return AuthResult.Error(
                message = "Failed to create deactivation request: ${e.message}",
                exception = FirebaseFirestoreException(
                    "firestore/create-failed",
                    "Failed to create deactivation request: ${e.message}"
                )
            )
        }
        return Success(
            user = _authState.value.user!!,
            token = _authState.value.authToken
        )
    }

    override suspend fun getDeactivationRequest(): DeactivationRequest? {
        return try {
            val userId = _authState.value.user?.uid!!
            val docRef = firestore
                .collection(COLLECTION_DEACTIVE_REQUESTS)
                .document(userId)
                .collection(SUBCOLLECTION_DEACTIVE_REQUESTS)
                .document(userId)

            val snapshot = docRef.get()
            if (snapshot.exists) {
                val data = snapshot.data<FirestoreDeactivationRequest>()
                data.toDeactivationRequest()
            } else {
                null
            }
        } catch (e: Exception) {
            throw FirebaseFirestoreException(
                code = "firestore/get-failed",
                message = "Failed to get deactivation request: ${e.message}"
            )
        }
    }

    override fun observeDeactivationRequest(): Flow<DeactivationRequest?> {
        return try {
            val userId = _authState.value.user?.uid!!
            val docRef = firestore
                .collection(COLLECTION_DEACTIVE_REQUESTS)
                .document(userId)
                .collection(SUBCOLLECTION_DEACTIVE_REQUESTS)
                .document(userId)

            docRef.snapshots.map { snapshot ->
                if (snapshot.exists) {
                    val data = snapshot.data<FirestoreDeactivationRequest>()
                    data.toDeactivationRequest()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            throw FirebaseFirestoreException(
                "firestore/observe-failed",
                "Failed to observe deactivation request: ${e.message}"
            )
        }
    }

    override suspend fun cancelDeactivationRequest(): AuthResult {
        return try {
            val userId = _authState.value.user?.uid!!

            val docRef = firestore
                .collection(COLLECTION_DEACTIVE_REQUESTS)
                .document(userId)
                .collection(SUBCOLLECTION_DEACTIVE_REQUESTS)
                .document(userId)

            docRef.delete()
            Success(
                user = _authState.value.user!!,
                token = _authState.value.authToken
            )
        } catch (e: Exception) {
            AuthResult.Error(
                "Failed to delete deactivation request: ${e.message}",
                FirebaseFirestoreException(
                    "firestore/delete-failed",
                    "Failed to delete deactivation request: ${e.message}"
                )
            )
        }
    }
}


/**
 * Serializable data class for Firestore operations
 */
@Serializable
private data class FirestoreDeactivationRequest(
    val userId: String = "",
    val timestamp: Long = 0L,
    val reason: String? = null,
    val status: String = "",
    val email: String? = null,
)


/**
 * Extension function to convert DeactivationRequest to Firestore-compatible format
 */
private fun DeactivationRequest.toFirestoreData(): FirestoreDeactivationRequest {
    return FirestoreDeactivationRequest(
        userId = userId,
        timestamp = timestamp,
        reason = reason,
        status = status.name,
        email = email
    )
}

/**
 * Extension function to convert Firestore data to DeactivationRequest
 */
private fun FirestoreDeactivationRequest.toDeactivationRequest(): DeactivationRequest {
    return DeactivationRequest(
        userId = userId,
        timestamp = timestamp,
        reason = reason,
        status = DeactivationStatus.valueOf(status),
        email = email
    )
}