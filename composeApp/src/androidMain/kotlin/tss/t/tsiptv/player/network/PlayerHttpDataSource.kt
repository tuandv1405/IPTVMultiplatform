package tss.t.tsiptv.player.network

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import tss.t.tsiptv.core.network.SSLTrustAllUtils
import tss.t.tsiptv.core.parser.iptv.m3u.HeaderSuffixParser
import java.security.SecureRandom
import javax.net.ssl.SSLContext

/**
 * One client for every stream: it holds the connection pool and [EdgeFailover]'s memory of
 * refusing edges, both of which would be lost if each channel built its own.
 *
 * Certificates are not checked, as before: IPTV playlists routinely point at servers
 * with self-signed or expired certificates. Requests that an edge refuses with 403
 * are moved to another edge; see [EdgeFailover].
 */
private val playerClient: OkHttpClient by lazy {
    // DNS-over-HTTPS queries go out with normal certificate checks.
    val plain = OkHttpClient()
    val failover = EdgeFailover(plain)

    val trustManager = SSLTrustAllUtils.createTrustAllTrustManager()
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }

    plain.newBuilder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier(SSLTrustAllUtils.trustAllHostnameVerifier)
        .dns(failover.dns)
        .addInterceptor(failover.retryOnOtherEdge)
        .addNetworkInterceptor(failover.recordRefusals)
        .build()
}

/**
 * The HTTP stack a stream is loaded through.
 *
 * Build one per channel: [headers] then apply to that channel's manifest, variant, segment and
 * key requests only, and switching channel cannot carry them over to the next one.
 *
 * @param headers The channel's own headers; its `User-Agent`, if any, replaces the default
 */
@UnstableApi
internal fun playerHttpDataSourceFactory(rawHeaders: Map<String, String> = emptyMap()): HttpDataSource.Factory {
    // OkHttp throws on control characters or non-ASCII; rows stored before the parser checked
    // for that must not crash playback.
    val headers = HeaderSuffixParser.sanitize(rawHeaders)
    val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
    // OkHttpDataSource adds its user agent on top of the request properties, so a
    // "User-Agent" left in the map would be sent twice.
    val others = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
    return OkHttpDataSource.Factory(playerClient)
        // The Dalvik agent HttpURLConnection sent, not "okhttp/x", which some servers block.
        .setUserAgent(userAgent ?: System.getProperty("http.agent"))
        .setDefaultRequestProperties(others)
}
