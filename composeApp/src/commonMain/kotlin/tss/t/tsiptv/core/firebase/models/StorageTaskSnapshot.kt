package tss.t.tsiptv.core.firebase.models

import tss.t.tsiptv.core.firebase.exceptions.FirebaseStorageException

/**
 * Data class representing a snapshot of a storage task.
 *
 * @property bytesTransferred The number of bytes transferred
 * @property totalBytes The total number of bytes to transfer
 * @property isComplete Whether the task is complete
 * @property isSuccessful Whether the task was successful
 * @property error The error if the task failed, or null if successful
 * @property downloadUrl The download URL if the task was an upload and is complete, or null otherwise
 * @property bytes The file data if the task was a download and is complete, or null otherwise
 */
data class StorageTaskSnapshot(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val isComplete: Boolean,
    val isSuccessful: Boolean,
    val error: FirebaseStorageException? = null,
    val downloadUrl: String? = null,
    val bytes: ByteArray? = null
) {
    /**
     * Returns the progress as a percentage.
     */
    val progress: Float
        get() = if (totalBytes > 0) {
            bytesTransferred.toFloat() / totalBytes.toFloat() * 100f
        } else {
            0f
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as StorageTaskSnapshot

        if (bytesTransferred != other.bytesTransferred) return false
        if (totalBytes != other.totalBytes) return false
        if (isComplete != other.isComplete) return false
        if (isSuccessful != other.isSuccessful) return false
        if (error != other.error) return false
        if (downloadUrl != other.downloadUrl) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytesTransferred.hashCode()
        result = 31 * result + totalBytes.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + isSuccessful.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (downloadUrl?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        return result
    }
}