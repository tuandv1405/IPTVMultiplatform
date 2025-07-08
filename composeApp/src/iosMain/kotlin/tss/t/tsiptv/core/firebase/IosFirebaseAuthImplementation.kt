package tss.t.tsiptv.core.firebase

import cocoapods.FirebaseAuth.FIRAuth
import cocoapods.FirebaseAuth.FIRGoogleAuthProvider
import cocoapods.FirebaseAuth.FIRUser
import cocoapods.FirebaseCore.FIRApp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS implementation of IFirebaseAuth.
 *
 * This implementation uses the Firebase SDK for iOS through Kotlin/Native interop.
 * It demonstrates how to properly integrate with the FirebaseAuthBridge.swift file.
 *
 * Note: To use this implementation, you need to:
 * 1. Add IFirebaseAuth as a dependency to the iOS project using Swift Package Manager
 * 2. Set up proper Kotlin/Native interop to call the FirebaseAuthBridge.swift file
 * 3. Replace the commented code with actual calls to the generated bindings
 */
@OptIn(ExperimentalForeignApi::class)
class IosFirebaseAuthImplementation : IFirebaseAuth {

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    override val currentUser: Flow<FirebaseUser?> = _currentUser

    init {
        FIRAuth.auth()
            .addAuthStateDidChangeListener { auth, currentUser ->
                if (currentUser != null) {
                    _currentUser.update {
                        currentUser.toFirebaseUser()
                    }
                } else {
                    _currentUser.update { null }
                }
            }
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        return suspendCancellableCoroutine { continuation ->
            FIRAuth.auth()
                .signInWithEmail(email = email, password = password) { result, error ->
                    val uid = result?.user()?.uid()
                    if (error != null) {
                        val nsError = error
                        continuation.resumeWithException(
                            FirebaseAuthException(
                                nsError.domain!!,
                                nsError.localizedDescription
                            )
                        )
                    } else if (uid != null) {
                        val firebaseUser = result.user().toFirebaseUser()
                        continuation.resume(firebaseUser)
                    } else {
                        continuation.resumeWithException(
                            FirebaseAuthException("auth/unknown", "Unknown error signing in")
                        )
                    }
                }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val clientId = FIRApp.defaultApp()?.options?.clientID ?: throw FirebaseAuthException(
            "auth/no-client-id",
            "No client ID found in Firebase configuration"
        )

        // Create Google Sign In configuration object.
//        val config = GIDConfiguration(clientID: clientID)
//        GIDSignIn.sharedInstance.configuration = config

        // Use the GoogleSignIn implementation to sign in with Google
        // In a real implementation, this would use the idToken from GoogleSignIn
        // to authenticate with Firebase

        // For now, we'll create a credential using the FIRGoogleAuthProvider
        // and sign in with that credential
        return suspendCancellableCoroutine { continuation ->
            val credential = FIRGoogleAuthProvider.credentialWithIDToken(
                idToken = idToken,
                accessToken = "" // Empty string as a placeholder for the access token
            )

            FIRAuth.auth().signInWithCredential(credential) { result, error ->
                val uid = result?.user()?.uid()
                if (error != null) {
                    val nsError = error
                    continuation.resumeWithException(
                        FirebaseAuthException(
                            nsError.domain!!,
                            nsError.localizedDescription
                        )
                    )
                } else if (uid != null) {
                    val firebaseUser = result.user().toFirebaseUser()
                    continuation.resume(firebaseUser)
                } else {
                    continuation.resumeWithException(
                        FirebaseAuthException("auth/unknown", "Unknown error signing in with Google")
                    )
                }
            }
        }
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
        return suspendCancellableCoroutine { continuation ->
            FIRAuth.auth()
                .createUserWithEmail(email = email, password = password) { result, error ->
                    val uid = result?.user()?.uid()
                    if (error != null) {
                        val nsError = error
                        continuation.resumeWithException(
                            FirebaseAuthException(
                                nsError.domain!!,
                                nsError.localizedDescription
                            )
                        )
                    } else if (uid != null) {
                        val firebaseUser = result.user().toFirebaseUser()
                        continuation.resume(firebaseUser)
                    } else {
                        continuation.resumeWithException(
                            FirebaseAuthException("auth/unknown", "Unknown error signing in")
                        )
                    }
                }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun signOut() {
        try {
            FIRAuth.auth().signOut(null)
        } catch (e: Exception) {
            val nsError = e as? NSError
            throw FirebaseAuthException(
                nsError?.domain ?: "auth/unknown",
                nsError?.localizedDescription ?: e.message ?: "Unknown error signing out"
            )
        }
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        // In a real implementation, this would be:
        /*
        return suspendCancellableCoroutine { continuation ->
            bridge.sendPasswordResetEmail(email = email) { error ->
                if (error != null) {
                    val nsError = error as NSError
                    continuation.resumeWithException(
                        FirebaseAuthException(
                            nsError.domain,
                            nsError.localizedDescription ?: "Unknown error"
                        )
                    )
                } else {
                    continuation.resume(Unit)
                }
            }
        }
        */

        // For now, we'll use the InMemoryFirebaseAuth implementation
        InMemoryFirebaseAuth().sendPasswordResetEmail(email)
    }

    override suspend fun updateEmail(email: String) {
        // In a real implementation, this would be:
        /*
        return suspendCancellableCoroutine { continuation ->
            bridge.updateEmail(email = email) { error ->
                if (error != null) {
                    val nsError = error as NSError
                    continuation.resumeWithException(
                        FirebaseAuthException(
                            nsError.domain,
                            nsError.localizedDescription ?: "Unknown error"
                        )
                    )
                } else {
                    continuation.resume(Unit)
                }
            }
        }
        */

        // For now, we'll use the InMemoryFirebaseAuth implementation
        InMemoryFirebaseAuth().updateEmail(email)
    }

    override suspend fun updatePassword(password: String) {
        // In a real implementation, this would be:
        /*
        return suspendCancellableCoroutine { continuation ->
            bridge.updatePassword(password = password) { error ->
                if (error != null) {
                    val nsError = error as NSError
                    continuation.resumeWithException(
                        FirebaseAuthException(
                            nsError.domain,
                            nsError.localizedDescription ?: "Unknown error"
                        )
                    )
                } else {
                    continuation.resume(Unit)
                }
            }
        }
        */

        // For now, we'll use the InMemoryFirebaseAuth implementation
        InMemoryFirebaseAuth().updatePassword(password)
    }

    override suspend fun deleteUser() {
        // In a real implementation, this would be:
        /*
        return suspendCancellableCoroutine { continuation ->
            bridge.deleteUser { error ->
                if (error != null) {
                    val nsError = error as NSError
                    continuation.resumeWithException(
                        FirebaseAuthException(
                            nsError.domain,
                            nsError.localizedDescription ?: "Unknown error"
                        )
                    )
                } else {
                    continuation.resume(Unit)
                }
            }
        }
        */

        // For now, we'll use the InMemoryFirebaseAuth implementation
        InMemoryFirebaseAuth().deleteUser()
    }

    /**
     * Clean up resources when the instance is no longer needed.
     * This should be called when the app is shutting down or when this instance is no longer needed.
     */
    fun cleanup() {
        // In a real implementation, this would be:
        /*
        authStateListener?.let { listener ->
            bridge.removeAuthStateListener(listener)
            authStateListener = null
        }
        */
    }

    fun FIRUser.toFirebaseUser(): FirebaseUser {
        return FirebaseUser(
            uid = uid(),
            email = email(),
            displayName = displayName(),
            photoUrl = photoURL()?.toString(),
            isEmailVerified = isEmailVerified()
        )
    }
}
