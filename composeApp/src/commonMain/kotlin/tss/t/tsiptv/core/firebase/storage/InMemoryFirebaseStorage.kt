package tss.t.tsiptv.core.firebase.storage

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tss.t.tsiptv.core.firebase.IFirebaseStorage
import tss.t.tsiptv.core.firebase.exceptions.FirebaseStorageException
import tss.t.tsiptv.core.firebase.models.StorageMetadata
import tss.t.tsiptv.core.firebase.models.StorageTaskSnapshot

/**
 * A simple in-memory implementation of IFirebaseStorage.
 * This implementation doesn't actually interact with Firebase, but provides a basic structure
 * that can be extended by platform-specific implementations.
 */
class InMemoryFirebaseStorage : IFirebaseStorage {
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
            delay(100)
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
            delay(100)
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