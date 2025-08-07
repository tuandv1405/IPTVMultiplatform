package tss.t.tsiptv.core.network

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.readByteArray
import okio.Buffer
import okio.GzipSource
import okio.buffer
import okio.use

/**
 * Common implementation of NetworkClient using Ktor.
 * This class provides a base implementation that can be extended by platform-specific implementations.
 */
abstract class KtorNetworkClient : NetworkClient {
    /**
     * The Ktor HttpClient instance to use for network requests.
     * This should be provided by platform-specific implementations.
     */
    protected abstract val client: HttpClient

    override suspend fun get(url: String, headers: Map<String, String>): String {
        val response = client.get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        return response.bodyAsText()
    }

    override suspend fun getManualGzipIfNeed(url: String, headers: Map<String, String>): String {
        val response = client.get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        val body = response.bodyAsBytes()
        val isGzip = isGzipCompressed(body)
        println("Gzip: $isGzip")
        return if (isGzip) {
            decompressGzip(body)
        } else {
            body.decodeToString()
        }
    }

    /**
     * Checks if a ByteArray is GZIP-compressed by inspecting its magic numbers.
     *
     * @param data The byte array to check.
     * @return `true` if the data starts with the GZIP magic numbers, `false` otherwise.
     */
    fun isGzipCompressed(data: ByteArray): Boolean {
        // A GZIP file must be at least 2 bytes long to hold the magic number.
        if (data.size < 2) {
            return false
        }
        // The GZIP magic numbers are 0x1f and 0x8b.
        // We must use .toByte() because hex literals in Kotlin are Ints.
        return data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    }

    /**
     * Decompresses a GZIP-compressed byte array and returns it as a UTF-8 String.
     * This uses the Okio library and works on all platforms (JVM, Native, JS).
     *
     * @param data The GZIP-compressed byte array.
     * @return The decompressed string.
     * @throws okio.IOException if the data is not valid GZIP.
     */
    fun decompressGzip(data: ByteArray): String {
        // 1. Create a Buffer, which is Okio's powerful byte string
        val buffer = Buffer()
        buffer.write(data)

        // 2. Create a GzipSource to read from the buffer
        val gzipSource = GzipSource(buffer)

        // 3. Use .buffer() for efficient reading and read the decompressed data
        return gzipSource.buffer().use {
            it.readUtf8()
        }
    }


    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
        val response = client.post(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body)
        }
        return response.bodyAsText()
    }

    override suspend fun put(url: String, body: String, headers: Map<String, String>): String {
        val response = client.put(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body)
        }
        return response.bodyAsText()
    }

    override suspend fun delete(url: String, headers: Map<String, String>): String {
        val response = client.delete(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        return response.bodyAsText()
    }

    override suspend fun downloadFile(url: String, headers: Map<String, String>): Flow<DownloadProgress> = flow {
        val response = client.get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }

        val contentLength = response.contentLength() ?: -1L
        var bytesDownloaded = 0L
        val channel = response.bodyAsChannel()
        val data = ByteArray(contentLength.toInt().coerceAtLeast(0))

        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(4096L)
            val bytes = packet.readByteArray()
            val offset = bytesDownloaded.toInt()

            if (data.size >= offset + bytes.size) {
                bytes.copyInto(data, offset)
            }

            bytesDownloaded += bytes.size
            emit(
                DownloadProgress(
                    bytesDownloaded = bytesDownloaded,
                    contentLength = contentLength,
                    isCompleted = false
                )
            )
        }

        emit(
            DownloadProgress(
                bytesDownloaded = bytesDownloaded,
                contentLength = contentLength,
                isCompleted = true,
                data = data
            )
        )
    }

    override suspend fun uploadFile(
        url: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        headers: Map<String, String>
    ): Flow<UploadProgress> = flow {
        val contentLength = fileBytes.size.toLong()

        // Emit initial progress
        emit(
            UploadProgress(
                bytesUploaded = 0,
                contentLength = contentLength,
                isCompleted = false
            )
        )

        // Perform the upload
        val response = client.post(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }

            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", fileBytes, Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=$fileName")
                        })
                    }
                )
            )
        }

        // Emit completed progress with response
        emit(
            UploadProgress(
                bytesUploaded = contentLength,
                contentLength = contentLength,
                isCompleted = true,
                response = response.bodyAsText()
            )
        )
    }
}
