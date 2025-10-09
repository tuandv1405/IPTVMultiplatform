package tss.t.tsiptv.core.firebase.exceptions

/**
 * Exception thrown when a Firebase Storage operation fails.
 *
 * @property code The error code
 * @property message The error message
 */
class FirebaseStorageException(val code: String, override val message: String) : Exception(message)