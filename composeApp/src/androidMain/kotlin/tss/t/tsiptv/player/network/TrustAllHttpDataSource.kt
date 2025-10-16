package tss.t.tsiptv.player.network

import androidx.annotation.Keep
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import tss.t.tsiptv.core.network.SSLTrustAllUtils
import javax.net.ssl.HttpsURLConnection

/**
 * A custom implementation of DefaultHttpDataSource that trusts all SSL certificates.
 * This is useful for handling self-signed certificates or certificate validation issues.
 *
 * WARNING: This should only be used for development or in very specific cases
 * where certificate validation is not required. Using this in production
 * can lead to security vulnerabilities.
 */
@UnstableApi
@Keep
class TrustAllHttpDataSource private constructor(
    private val delegate: DefaultHttpDataSource,
) : HttpDataSource by delegate {

    /**
     * Factory for [TrustAllHttpDataSource] instances.
     */
    class Factory : HttpDataSource.Factory {
        private val defaultFactory = DefaultHttpDataSource.Factory()

        /**
         * Sets whether cross-protocol redirects (i.e., redirects from HTTPS to HTTP and vice versa)
         * are enabled.
         */
        @OptIn(UnstableApi::class)
        fun setAllowCrossProtocolRedirects(allowCrossProtocolRedirects: Boolean): Factory {
            defaultFactory.setAllowCrossProtocolRedirects(allowCrossProtocolRedirects)
            return this
        }

        /**
         * Sets the connect timeout, in milliseconds.
         */
        fun setConnectTimeoutMs(connectTimeoutMs: Int): Factory {
            defaultFactory.setConnectTimeoutMs(connectTimeoutMs)
            return this
        }

        /**
         * Sets the read timeout, in milliseconds.
         */
        fun setReadTimeoutMs(readTimeoutMs: Int): Factory {
            defaultFactory.setReadTimeoutMs(readTimeoutMs)
            return this
        }

        /**
         * Sets the user agent that will be used.
         */
        fun setUserAgent(userAgent: String): Factory {
            defaultFactory.setUserAgent(userAgent)
            return this
        }


        /**
         * Creates a [TrustAllHttpDataSource] instance.
         */
        override fun createDataSource(): HttpDataSource {
            val dataSource = defaultFactory.createDataSource()
            return TrustAllHttpDataSource(dataSource)
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: kotlin.collections.Map<String, String>): HttpDataSource.Factory {
            defaultFactory.setDefaultRequestProperties(defaultRequestProperties)
            return this
        }
    }

    /**
     * Overrides the open method to configure SSL trust all before delegating to the original implementation.
     */
    override fun open(dataSpec: DataSpec): Long {
        // If the URL is HTTPS, configure SSL trust all globally or attempt reflection safely
        val uri = dataSpec.uri
        if (uri.scheme?.equals("https", ignoreCase = true) == true) {
            try {
                // Try to access the connection field safely with proper error handling
                val connectionField = delegate.javaClass.getDeclaredField("connection")
                connectionField.isAccessible = true
                val connection = connectionField.get(delegate) as? HttpsURLConnection

                connection?.let {
                    it.sslSocketFactory = SSLTrustAllUtils.createTrustAllSSLSocketFactory()
                    it.hostnameVerifier = SSLTrustAllUtils.trustAllHostnameVerifier
                }
            } catch (e: NoSuchFieldException) {
                // Field doesn't exist (likely due to obfuscation or version change)
                // Configure SSL trust all globally as fallback
                try {
                    HttpsURLConnection.setDefaultSSLSocketFactory(SSLTrustAllUtils.createTrustAllSSLSocketFactory())
                    HttpsURLConnection.setDefaultHostnameVerifier(SSLTrustAllUtils.trustAllHostnameVerifier)
                } catch (globalException: Exception) {
                    android.util.Log.w("TrustAllHttpDataSource", "Failed to configure global SSL trust all", globalException)
                }
            } catch (e: Exception) {
                // Other reflection errors
                android.util.Log.w("TrustAllHttpDataSource", "Failed to configure SSL trust all via reflection", e)
            }
        }

        // Delegate to the original implementation
        return delegate.open(dataSpec)
    }
}