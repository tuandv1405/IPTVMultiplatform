package tss.t.tsiptv.core.network

import kotlinx.coroutines.flow.Flow

/**
 * NetworkClient interface for making network requests.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface NetworkClient {
    /**
     * Performs a GET request to the specified URL.
     *
     * @param url The URL to make the request to
     * @param headers Optional headers to include in the request
     * @return A Flow emitting the response as a String
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Performs a GET request to the specified URL.
     *
     * @param url The URL to make the request to
     * @param headers Optional headers to include in the request
     * @return A Flow emitting the response as a String
     */
    suspend fun getManualGzipIfNeed(url: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Performs a POST request to the specified URL.
     *
     * @param url The URL to make the request to
     * @param body The request body as a String
     * @param headers Optional headers to include in the request
     * @return A Flow emitting the response as a String
     */
    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Performs a PUT request to the specified URL.
     *
     * @param url The URL to make the request to
     * @param body The request body as a String
     * @param headers Optional headers to include in the request
     * @return A Flow emitting the response as a String
     */
    suspend fun put(url: String, body: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Performs a DELETE request to the specified URL.
     *
     * @param url The URL to make the request to
     * @param headers Optional headers to include in the request
     * @return A Flow emitting the response as a String
     */
    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Downloads a file from the specified URL.
     *
     * @param url The URL to download the file from
     * @param headers Optional headers to include in the request
     * @return A Flow emitting the download progress and the downloaded bytes
     */
    suspend fun downloadFile(url: String, headers: Map<String, String> = emptyMap()): Flow<DownloadProgress>

    /**
     * Uploads a file to the specified URL.
     *
     * @param url The URL to upload the file to
     * @param fileBytes The file bytes to upload
     * @param fileName The name of the file
     * @param mimeType The MIME type of the file
     * @param headers Optional headers to include in the request
     * @return A Flow emitting the upload progress and the response
     */
    suspend fun uploadFile(
        url: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        headers: Map<String, String> = emptyMap()
    ): Flow<UploadProgress>
}

/**
 * Data class representing the progress of a download.
 *
 * @property bytesDownloaded The number of bytes downloaded
 * @property contentLength The total size of the file in bytes, or -1 if unknown
 * @property isCompleted Whether the download is completed
 * @property data The downloaded bytes, only available when isCompleted is true
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val contentLength: Long,
    val isCompleted: Boolean,
    val data: ByteArray? = null
) {
    /**
     * Returns the download progress as a percentage.
     * Returns -1 if the content length is unknown.
     */
    val progress: Float
        get() = if (contentLength > 0) {
            bytesDownloaded.toFloat() / contentLength.toFloat() * 100f
        } else {
            -1f
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DownloadProgress

        if (bytesDownloaded != other.bytesDownloaded) return false
        if (contentLength != other.contentLength) return false
        if (isCompleted != other.isCompleted) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytesDownloaded.hashCode()
        result = 31 * result + contentLength.hashCode()
        result = 31 * result + isCompleted.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Data class representing the progress of an upload.
 *
 * @property bytesUploaded The number of bytes uploaded
 * @property contentLength The total size of the file in bytes
 * @property isCompleted Whether the upload is completed
 * @property response The response from the server, only available when isCompleted is true
 */
data class UploadProgress(
    val bytesUploaded: Long,
    val contentLength: Long,
    val isCompleted: Boolean,
    val response: String? = null
) {
    /**
     * Returns the upload progress as a percentage.
     */
    val progress: Float
        get() = bytesUploaded.toFloat() / contentLength.toFloat() * 100f
}
