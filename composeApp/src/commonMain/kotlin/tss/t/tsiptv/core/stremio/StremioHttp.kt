package tss.t.tsiptv.core.stremio

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** A response as the addon client needs it. [headers] keys are lower-case. */
data class StremioHttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    /** Redacted: bodies and headers (Set-Cookie, Location with tokens) are never printed. */
    override fun toString(): String = "StremioHttpResponse(status=$status, headers=${headers.keys}, bodyChars=${body.length})"
}

/**
 * The one HTTP operation the addon protocol needs. Implementations throw on network errors and
 * timeouts; non-2xx statuses are returned, not thrown.
 *
 * The existing `core/network/NetworkClient` is not used: it returns only the body, and the addon
 * client needs the status code and `Cache-Control`.
 */
fun interface StremioHttpTransport {
    suspend fun get(url: String, headers: Map<String, String>, kind: StremioRequestKind): StremioHttpResponse
}

/**
 * Ktor implementation. Pass a client made by [createStremioHttpClient] (redirects followed, gzip,
 * [HttpTimeout] installed, `expectSuccess = false`).
 */
class KtorStremioHttpTransport(
    private val client: HttpClient,
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
) : StremioHttpTransport {
    override suspend fun get(url: String, headers: Map<String, String>, kind: StremioRequestKind): StremioHttpResponse {
        val response = client.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
            timeout {
                connectTimeoutMillis = kind.connectTimeoutMs
                requestTimeoutMillis = kind.totalTimeoutMs
                socketTimeoutMillis = kind.totalTimeoutMs
            }
        }
        val responseHeaders = LinkedHashMap<String, String>()
        response.headers.forEach { name, values -> responseHeaders[name.lowercase()] = values.joinToString(", ") }
        // Content-Length is the wire size (maybe gzip); the decoded body is limited again below.
        val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > maxBodyBytes) throw StremioBodyTooLargeException()
        val bytes = response.bodyAsChannel().readRemaining(maxBodyBytes + 1).readByteArray()
        if (bytes.size > maxBodyBytes) throw StremioBodyTooLargeException()
        return StremioHttpResponse(response.status.value, responseHeaders, bytes.decodeToString())
    }
}

/** Thrown by transports when a body exceeds [MAX_BODY_BYTES]; the client maps it to `too_large`. */
class StremioBodyTooLargeException : Exception("Addon response too large")

/** 8 MiB: far above any real catalog page (Cinemeta's 100-item pages are ~100 KB). */
const val MAX_BODY_BYTES: Long = 8L * 1024 * 1024

/**
 * Derives the shared addon client from a platform client (sharing its engine): follows redirects
 * (SDK `redirect` → 307), accepts gzip/deflate, per-request timeouts, never throws on status.
 * No logging plugin: addon URLs can carry secrets.
 */
fun createStremioHttpClient(base: HttpClient): HttpClient = base.config {
    followRedirects = true
    expectSuccess = false
    // Redirects (SDK `redirect` → 307) are followed, but never from https to http.
    install(HttpRedirect) { allowHttpsDowngrade = false }
    install(HttpTimeout)
    install(ContentEncoding) {
        gzip()
        deflate()
    }
}
