package tss.t.tsiptv.core.firebase

import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.firebase.models.StorageMetadata
import tss.t.tsiptv.core.firebase.models.StorageTaskSnapshot

/**
 * Interface for Firebase Storage.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface IFirebaseStorage {
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
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseStorageException if the file doesn't exist
     */
    suspend fun getDownloadUrl(path: String): String

    /**
     * Deletes a file from Firebase Storage.
     *
     * @param path The path to delete the file from
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseStorageException if the file doesn't exist
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
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseStorageException if the file doesn't exist
     */
    suspend fun getMetadata(path: String): StorageMetadata
}

