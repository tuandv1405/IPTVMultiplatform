package tss.t.tsiptv.player.network

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import tss.t.tsiptv.core.network.SSLTrustAllUtils
import java.security.SecureRandom
import javax.net.ssl.SSLContext

/**
 * The HTTP stack every stream is loaded through.
 *
 * Certificates are not checked, as before: IPTV playlists routinely point at servers
 * with self-signed or expired certificates. Requests that an edge refuses with 403
 * are moved to another edge; see [EdgeFailover].
 */
@UnstableApi
internal fun playerHttpDataSourceFactory(): HttpDataSource.Factory {
    // DNS-over-HTTPS queries go out with normal certificate checks.
    val plain = OkHttpClient()
    val failover = EdgeFailover(plain)

    val trustManager = SSLTrustAllUtils.createTrustAllTrustManager()
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }

    val client = plain.newBuilder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier(SSLTrustAllUtils.trustAllHostnameVerifier)
        .dns(failover.dns)
        .addInterceptor(failover.retryOnOtherEdge)
        .addNetworkInterceptor(failover.recordRefusals)
        .build()

    return OkHttpDataSource.Factory(client)
        // The Dalvik agent HttpURLConnection sent, not "okhttp/x", which some servers block.
        .setUserAgent(System.getProperty("http.agent"))
}
