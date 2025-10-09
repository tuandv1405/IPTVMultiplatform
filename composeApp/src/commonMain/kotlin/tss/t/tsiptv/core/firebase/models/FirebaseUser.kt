package tss.t.tsiptv.core.firebase.models

/**
 * Data class representing a Firebase user.
 *
 * @property uid The user's unique ID
 * @property email The user's email, or null if not available
 * @property displayName The user's display name, or null if not available
 * @property photoUrl The URL of the user's profile photo, or null if not available
 * @property isEmailVerified Whether the user's email is verified
 */
data class FirebaseUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isEmailVerified: Boolean,
)