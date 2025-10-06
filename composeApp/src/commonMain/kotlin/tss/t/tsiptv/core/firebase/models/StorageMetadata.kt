package tss.t.tsiptv.core.firebase.models

/**
 * Data class representing metadata for a file in Firebase Storage.
 *
 * @property path The path to the file
 * @property name The name of the file
 * @property sizeBytes The size of the file in bytes
 * @property contentType The content type of the file
 * @property creationTimeMillis The creation time of the file in milliseconds since epoch
 * @property updatedTimeMillis The last update time of the file in milliseconds since epoch
 * @property customMetadata Custom metadata for the file
 */
data class StorageMetadata(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val contentType: String? = null,
    val creationTimeMillis: Long = 0,
    val updatedTimeMillis: Long = 0,
    val customMetadata: Map<String, String> = emptyMap()
)