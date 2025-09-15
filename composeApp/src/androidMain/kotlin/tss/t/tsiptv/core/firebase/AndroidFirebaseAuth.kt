package tss.t.tsiptv.core.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser as AndroidFirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of IFirebaseAuth.
 * This implementation uses the Firebase SDK for Android.
 */
class AndroidFirebaseAuth : IFirebaseAuth {
    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    override val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toFirebaseUser())
        }
        auth.addAuthStateListener(authStateListener)

        trySend(auth.currentUser?.toFirebaseUser())

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            return result.user?.toFirebaseUser()
                ?: throw FirebaseAuthException("auth/unknown", "Unknown error signing in")
        } catch (e: FirebaseAuthInvalidUserException) {
            throw FirebaseAuthException(e.errorCode, "No user found with email $email")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw FirebaseAuthException(e.errorCode, "Wrong password")
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error signing in", e)
            throw FirebaseAuthException("auth/unknown", e.message ?: "Unknown error signing in")
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
            throw FirebaseAuthException("auth/email-already-in-use", "Email already in use")
        } catch (e: Exception) {
            Log.e("AndroidFirebaseAuth", "Error creating user", e)
            throw FirebaseAuthException("auth/unknown", e.message ?: "Unknown error creating user")
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
