package tss.t.tsiptv.core.firebase

import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.firebase.models.FirebaseUser

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
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException if sign-in fails
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
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException if user creation fails
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
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException if sending the email fails
     */
    suspend fun sendPasswordResetEmail(email: String)

    /**
     * Updates the current user's email.
     *
     * @param email The new email
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException if updating the email fails
     */
    suspend fun updateEmail(email: String)

    /**
     * Updates the current user's display name.
     *
     * @param displayName The new display name
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException if updating the display name fails
     */
    suspend fun updateDisplayName(displayName: String)

    /**
     * Updates the current user's password.
     *
     * @param password The new password
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException if updating the password fails
     */
    suspend fun updatePassword(password: String)

    /**
     * Deletes the current user.
     *
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseAuthException if deleting the user fails
     */
    suspend fun deleteUser()
}

