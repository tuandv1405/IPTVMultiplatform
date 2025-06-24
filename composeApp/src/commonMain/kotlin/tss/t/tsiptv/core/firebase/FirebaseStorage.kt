package tss.t.tsiptv.core.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Interface for Firebase Storage.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface FirebaseStorage {
    /**
     * Uploads a file to Firebase Storage.
     *
     * @param path The path to upload the file to
     * @param bytes The file data as a ByteArray
     * @param metadata Optional metadata for the file
     * @return A flow that emits the upload progress and the download URL when complete
     */
    fun uploadFile(
        path: String,
        bytes: ByteArray,
        metadata: Map<String, String> = emptyMap()
    ): Flow<StorageTaskSnapshot>

    /**
     * Downloads a file from Firebase Storage.
     *
     * @param path The path to download the file from
     * @return A flow that emits the download progress and the file data when complete
     */
    fun downloadFile(path: String): Flow<StorageTaskSnapshot>

    /**
     * Gets the download URL for a file in Firebase Storage.
     *
     * @param path The path to get the download URL for
     * @return The download URL as a string
     * @throws FirebaseStorageException if the file doesn't exist
     */
    suspend fun getDownloadUrl(path: String): String

    /**
     * Deletes a file from Firebase Storage.
     *
     * @param path The path to delete the file from
     * @throws FirebaseStorageException if the file doesn't exist
     */
    suspend fun deleteFile(path: String)

    /**
     * Lists files in a directory in Firebase Storage.
     *
     * @param path The path to list files from
     * @return A list of file metadata
     */
    suspend fun listFiles(path: String): List<StorageMetadata>

    /**
     * Gets metadata for a file in Firebase Storage.
     *
     * @param path The path to get metadata for
     * @return The file metadata
     * @throws FirebaseStorageException if the file doesn't exist
     */
    suspend fun getMetadata(path: String): StorageMetadata
}

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

/**
 * Exception thrown when a Firebase Storage operation fails.
 *
 * @property code The error code
 * @property message The error message
 */
class FirebaseStorageException(val code: String, override val message: String) : Exception(message)

/**
 * A simple in-memory implementation of FirebaseStorage.
 * This implementation doesn't actually interact with Firebase, but provides a basic structure
 * that can be extended by platform-specific implementations.
 */
class InMemoryFirebaseStorage : FirebaseStorage {
    private val files = mutableMapOf<String, ByteArray>()
    private val metadata = mutableMapOf<String, StorageMetadata>()
    private val downloadUrls = mutableMapOf<String, String>()

    override fun uploadFile(
        path: String,
        bytes: ByteArray,
        metadata: Map<String, String>
    ): Flow<StorageTaskSnapshot> = flow {
        val totalBytes = bytes.size.toLong()

        // Simulate upload progress
        for (i in 1..10) {
            val bytesTransferred = (totalBytes * i) / 10
            emit(
                StorageTaskSnapshot(
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes,
                    isComplete = i == 10,
                    isSuccessful = true
                )
            )

            // Simulate network delay
            kotlinx.coroutines.delay(100)
        }

        // Store the file
        files[path] = bytes

        // Create metadata
        val fileName = path.split("/").last()
        val storageMetadata = StorageMetadata(
            path = path,
            name = fileName,
            sizeBytes = totalBytes,
            contentType = guessContentType(fileName),
            creationTimeMillis = getCurrentTimeMillis(),
            updatedTimeMillis = getCurrentTimeMillis(),
            customMetadata = metadata
        )
        this@InMemoryFirebaseStorage.metadata[path] = storageMetadata

        // Generate a download URL
        val downloadUrl = "https://example.com/storage/$path"
        downloadUrls[path] = downloadUrl

        // Emit final snapshot with download URL
        emit(
            StorageTaskSnapshot(
                bytesTransferred = totalBytes,
                totalBytes = totalBytes,
                isComplete = true,
                isSuccessful = true,
                downloadUrl = downloadUrl
            )
        )
    }

    override fun downloadFile(path: String): Flow<StorageTaskSnapshot> = flow {
        val bytes = files[path] ?: throw FirebaseStorageException(
            "not-found",
            "File not found at path: $path"
        )

        val totalBytes = bytes.size.toLong()

        // Simulate download progress
        for (i in 1..10) {
            val bytesTransferred = (totalBytes * i) / 10
            emit(
                StorageTaskSnapshot(
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes,
                    isComplete = i == 10,
                    isSuccessful = true,
                    bytes = if (i == 10) bytes else null
                )
            )

            // Simulate network delay
            kotlinx.coroutines.delay(100)
        }
    }

    override suspend fun getDownloadUrl(path: String): String {
        return downloadUrls[path] ?: throw FirebaseStorageException(
            "not-found",
            "File not found at path: $path"
        )
    }

    override suspend fun deleteFile(path: String) {
        if (!files.containsKey(path)) {
            throw FirebaseStorageException(
                "not-found",
                "File not found at path: $path"
            )
        }

        files.remove(path)
        metadata.remove(path)
        downloadUrls.remove(path)
    }

    override suspend fun listFiles(path: String): List<StorageMetadata> {
        return metadata.values.filter { it.path.startsWith(path) }
    }

    override suspend fun getMetadata(path: String): StorageMetadata {
        return metadata[path] ?: throw FirebaseStorageException(
            "not-found",
            "File not found at path: $path"
        )
    }

    /**
     * Guesses the content type based on the file name.
     */
    private fun guessContentType(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
            fileName.endsWith(".html", ignoreCase = true) -> "text/html"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            fileName.endsWith(".xml", ignoreCase = true) -> "application/xml"
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    /**
     * Gets the current time in milliseconds.
     * This is a simple implementation that doesn't use platform-specific APIs.
     */
    private fun getCurrentTimeMillis(): Long {
        // This is a placeholder. In a real implementation, we would use a platform-specific API.
        return 0L
    }
}
