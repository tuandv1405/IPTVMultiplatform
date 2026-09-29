package tss.t.tsiptv.core.tsiptv

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException

/**
 * One GET of a source document, include, guide or playlist (PRD §5 "How").
 *
 * @property maxBytes Hard cap on the body as received (after HTTP `Content-Encoding` decoding):
 *   reading stops at `maxBytes + 1` and the fetch fails with [SourceFetchResult.Failed] `too_large`
 * @property sniffCap Called once with the first [SNIFF_BYTES] of the body (or all of it, if
 *   shorter): a non-null value lowers the cap, e.g. to 5 MiB once the body turns out to be a
 *   TS IPTV Source
 * @property etag Sent as `If-None-Match` when non-null
 * @property lastModified Sent as `If-Modified-Since` when non-null
 * @property connectTimeoutMs 15 s for includes (PRD §5)
 * @property totalTimeoutMs 60 s for includes; null = no total limit (large playlists on slow links)
 */
class SourceFetchRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val maxBytes: Long,
    val sniffCap: ((ByteArray) -> Long?)? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
    val totalTimeoutMs: Long? = TOTAL_TIMEOUT_MS,
) {
    /** Never the URL: links carry tokens. */
    override fun toString(): String = "SourceFetchRequest(headers=${headers.keys}, maxBytes=$maxBytes)"

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val TOTAL_TIMEOUT_MS = 60_000L
        const val SNIFF_BYTES = 64 * 1024
    }
}

sealed interface SourceFetchResult {
    /** 2xx. [bytes] may still be a gzip file (`.gz` without `Content-Encoding`). */
    class Ok(val bytes: ByteArray, val etag: String?, val lastModified: String?) : SourceFetchResult {
        override fun toString(): String = "Ok(bytes=${bytes.size})"
    }

    /** `304 Not Modified`: reuse the stored copy (counts 0 towards the budget). */
    data object NotModified : SourceFetchResult

    /**
     * Network error, timeout, non-2xx, or over the cap. [code] is short and never contains a URL:
     * `network`, `timeout`, `too_large`, `http_<status>`.
     */
    data class Failed(val code: String, val status: Int? = null) : SourceFetchResult
}

/** Transport for [SourceFetchRequest]s. Never throws for HTTP or network failures (only cancellation). */
fun interface SourceHttpTransport {
    suspend fun fetch(request: SourceFetchRequest): SourceFetchResult
}

/**
 * Ktor implementation over a client made by
 * [tss.t.tsiptv.core.stremio.createStremioHttpClient] (redirects without https→http downgrade,
 * gzip/deflate `Content-Encoding`, [io.ktor.client.plugins.HttpTimeout], no status exceptions, no
 * logging).
 */
class KtorSourceHttpTransport(private val client: HttpClient) : SourceHttpTransport {
    override suspend fun fetch(request: SourceFetchRequest): SourceFetchResult = try {
        client.prepareGet(request.url) {
            request.headers.forEach { (k, v) -> header(k, v) }
            request.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            request.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
            timeout {
                connectTimeoutMillis = request.connectTimeoutMs
                request.totalTimeoutMs?.let { requestTimeoutMillis = it }
                socketTimeoutMillis = request.totalTimeoutMs ?: SOCKET_TIMEOUT_MS
            }
        }.execute { response ->
            val status = response.status.value
            when {
                status == 304 -> SourceFetchResult.NotModified
                status !in 200..299 -> SourceFetchResult.Failed("http_$status", status)
                else -> {
                    val channel = response.bodyAsChannel()
                    var cap = request.maxBytes
                    val head = channel.readRemaining(SourceFetchRequest.SNIFF_BYTES.toLong()).readByteArray()
                    request.sniffCap?.invoke(head)?.let { cap = minOf(cap, it) }
                    if (head.size > cap) {
                        SourceFetchResult.Failed("too_large")
                    } else {
                        val rest = channel.readRemaining(cap - head.size + 1).readByteArray()
                        if (head.size.toLong() + rest.size > cap) {
                            SourceFetchResult.Failed("too_large")
                        } else {
                            SourceFetchResult.Ok(
                                bytes = if (rest.isEmpty()) head else head + rest,
                                etag = response.headers[HttpHeaders.ETag],
                                lastModified = response.headers[HttpHeaders.LastModified],
                            )
                        }
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Exception messages can contain the URL: only the kind is kept.
        val name = e::class.simpleName.orEmpty()
        SourceFetchResult.Failed(if ("Timeout" in name) "timeout" else "network")
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 60_000L
    }
}
