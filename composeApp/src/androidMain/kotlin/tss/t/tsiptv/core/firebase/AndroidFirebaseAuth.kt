package tss.t.tsiptv.core.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.google.firebase.auth.FirebaseUser as AndroidFirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException
import tss.t.tsiptv.core.firebase.models.FirebaseUser

/**
 * Android implementation of IFirebaseAuth.
 * This implementation uses the Firebase SDK for Android.
 */
class AndroidFirebaseAuth : IFirebaseAuth {
    private val auth by lazy {
        FirebaseAuth.getInstance()
    }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _userShareFlow by lazy {
        MutableSharedFlow<FirebaseUser?>()
    }

    init {

        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            coroutineScope.launch {
                _userShareFlow.emit(firebaseAuth.currentUser?.toFirebaseUser())
                runCatching {
                    firebaseAuth.getAccessToken(true).await()
                }.onFailure {
                    when (it) {
                        is FirebaseAuthInvalidUserException -> {
                            signOut()
                        }
                    }
                }
            }
        }

        auth.addAuthStateListener(authStateListener)
    }

    override val currentUser: Flow<FirebaseUser?> = _userShareFlow.asSharedFlow()

    override suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            return result.user?.toFirebaseUser()
                ?: throw FirebaseAuthException("auth/unknown", "Unknown error signing in")
        } catch (e: FirebaseAuthInvalidUserException) {
            throw FirebaseAuthException(
                e.errorCode,
                e.localizedMessage ?: "No user found with email $email"
            )
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw FirebaseAuthException(e.errorCode, e.localizedMessage ?: "Invalid Credentials")
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error signing in", e)
            throw FirebaseAuthException(
                "auth/unknown",
                e.localizedMessage ?: "Unknown error signing in"
            )
        }
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
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            return result.user?.toFirebaseUser()
                ?: throw FirebaseAuthException("auth/unknown", "Unknown error creating user")
        } catch (e: FirebaseAuthUserCollisionException) {
            throw FirebaseAuthException(
                "auth/email-already-in-use",
                e.localizedMessage ?: "Email already in use"
            )
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error creating user", e)
            throw FirebaseAuthException(
                "auth/unknown",
                e.localizedMessage ?: "Unknown error creating user"
            )
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            throw FirebaseAuthException("auth/user-not-found", "No user found with email $email")
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error sending password reset email", e)
            throw FirebaseAuthException(
                "auth/unknown",
                e.message ?: "Unknown error sending password reset email"
            )
        }
    }

    override suspend fun updateEmail(email: String) {
        try {
            val user = auth.currentUser ?: throw FirebaseAuthException(
                "auth/no-current-user",
                "No current user"
            )
            user.updateEmail(email).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            throw FirebaseAuthException("auth/email-already-in-use", "Email already in use")
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error updating email", e)
            throw FirebaseAuthException("auth/unknown", e.message ?: "Unknown error updating email")
        }
    }

    override suspend fun updateDisplayName(displayName: String) {
        try {
            val user = auth.currentUser ?: throw FirebaseAuthException(
                "auth/no-current-user",
                "No current user"
            )
            user.updateProfile(
                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
            ).await()

        } catch (e: Exception) {
        }
    }

    override suspend fun updatePassword(password: String) {
        try {
            val user = auth.currentUser ?: throw FirebaseAuthException(
                "auth/no-current-user",
                "No current user"
            )
            user.updatePassword(password).await()
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error updating password", e)
            throw FirebaseAuthException(
                "auth/unknown",
                e.message ?: "Unknown error updating password"
            )
        }
    }

    override suspend fun deleteUser() {
        try {
            val user = auth.currentUser ?: throw FirebaseAuthException(
                "auth/no-current-user",
                "No current user"
            )
            user.delete().await()
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error deleting user", e)
            throw FirebaseAuthException("auth/unknown", e.message ?: "Unknown error deleting user")
        }
    }

    /**
     * Converts an Android FirebaseUser to our FirebaseUser model.
     */
    private fun AndroidFirebaseUser.toFirebaseUser(): FirebaseUser {
        return FirebaseUser(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            isEmailVerified = isEmailVerified
        )
    }
}
