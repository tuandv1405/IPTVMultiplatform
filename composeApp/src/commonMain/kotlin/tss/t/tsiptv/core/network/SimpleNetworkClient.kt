package tss.t.tsiptv.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A simple implementation of NetworkClient that can be used as a placeholder.
 * This implementation doesn't actually make network requests, but provides a basic structure
 * that can be extended by platform-specific implementations.
 */
class SimpleNetworkClient : NetworkClient {
    override suspend fun get(url: String, headers: Map<String, String>): String {
        // Placeholder implementation
        return "GET response from $url"
    }

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
        // Placeholder implementation
        return "POST response from $url with body: $body"
    }

    override suspend fun put(url: String, body: String, headers: Map<String, String>): String {
        // Placeholder implementation
        return "PUT response from $url with body: $body"
    }

    override suspend fun delete(url: String, headers: Map<String, String>): String {
        // Placeholder implementation
        return "DELETE response from $url"
    }

    override suspend fun downloadFile(url: String, headers: Map<String, String>): Flow<DownloadProgress> = flow {
        // Simulate download progress
        val totalBytes = 1000L

        // Emit progress updates
        for (i in 1..10) {
            val bytesDownloaded = (totalBytes * i) / 10
            emit(
                DownloadProgress(
                    bytesDownloaded = bytesDownloaded,
                    contentLength = totalBytes,
                    isCompleted = i == 10,
                    data = if (i == 10) ByteArray(totalBytes.toInt()) else null
                )
            )
        }
    }

    override suspend fun uploadFile(
        url: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        headers: Map<String, String>
    ): Flow<UploadProgress> = flow {
        // Simulate upload progress
        val totalBytes = fileBytes.size.toLong()

        // Emit progress updates
        for (i in 1..10) {
            val bytesUploaded = (totalBytes * i) / 10
            emit(
                UploadProgress(
                    bytesUploaded = bytesUploaded,
                    contentLength = totalBytes,
                    isCompleted = i == 10,
                    response = if (i == 10) "Upload completed for $fileName" else null
                )
            )
        }
    }
}
