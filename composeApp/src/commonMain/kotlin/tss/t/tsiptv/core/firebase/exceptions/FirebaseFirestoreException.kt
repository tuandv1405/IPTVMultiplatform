package tss.t.tsiptv.core.firebase.exceptions

/**
 * Exception thrown when a Firebase Firestore operation fails.
 *
 * @property code The error code
 * @property message The error message
 */
class FirebaseFirestoreException(val code: String, override val message: String) : Exception(message)