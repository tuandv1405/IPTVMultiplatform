package tss.t.tsiptv.feature.clip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * [ClipHttp] over HttpURLConnection. Certificates are not checked, for these connections only:
 * the desktop player accepts the same streams (DesktopMediaPlayer answers libVLC's "Insecure site").
 */
internal class DesktopClipHttp : ClipHttp {

    override suspend fun fetch(url: String, headers: Map<String, String>, range: HlsByteRange?): ClipHttpBody =
        withContext(Dispatchers.IO) {
            val connection = open(url, headers, range)
            try {
                val bytes = runInterruptible { connection.inputStream.use { it.readBytes() } }
                ClipHttpBody(bytes, connection.url.toString())
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun stream(url: String, headers: Map<String, String>, onChunk: suspend (ByteArray, Int) -> Boolean) =
        withContext(Dispatchers.IO) {
            val connection = open(url, headers, range = null)
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = runInterruptible { input.read(buffer) }
                        if (read < 0 || !onChunk(buffer, read)) break
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

    /** Opens [url], following redirects across http and https (HttpURLConnection does not). */
    private fun open(url: String, headers: Map<String, String>, range: HlsByteRange?): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = URI(current).toURL().openConnection() as HttpURLConnection
            if (connection is HttpsURLConnection) {
                connection.sslSocketFactory = trustAll
                connection.setHostnameVerifier { _, _ -> true }
            }
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            range?.let { connection.setRequestProperty("Range", "bytes=${it.offset}-${it.offset + it.length - 1}") }
            val status = connection.responseCode
            when (status) {
                in 200..299 -> return connection
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    current = resolveUrl(current, location ?: throw ClipException(ClipFailure.NETWORK, "redirect"))
                }
                else -> {
                    connection.disconnect()
                    throw ClipException(ClipFailure.NETWORK, "HTTP $status")
                }
            }
        }
        throw ClipException(ClipFailure.NETWORK, "too many redirects")
    }

    private companion object {
        const val MAX_REDIRECTS = 6

        val trustAll: SSLSocketFactory by lazy {
            val manager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), SecureRandom()) }.socketFactory
        }
    }
}

internal val JvmAes128Cbc = Aes128Cbc { key, iv, data ->
    Cipher.getInstance("AES/CBC/PKCS5Padding")
        .apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        .doFinal(data)
}
