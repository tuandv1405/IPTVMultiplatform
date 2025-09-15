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

