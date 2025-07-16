package tss.t.tsiptv.player.network

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
        // If the URL is HTTPS, configure the connection to trust all certificates
        val uri = dataSpec.uri
        if (uri.scheme?.equals("https", ignoreCase = true) == true) {
            // Configure the connection to trust all certificates
            val connection = delegate.javaClass.getDeclaredField("connection").apply {
                isAccessible = true
            }.get(delegate) as? HttpsURLConnection

            connection?.let {
                it.sslSocketFactory = SSLTrustAllUtils.createTrustAllSSLSocketFactory()
                it.hostnameVerifier = SSLTrustAllUtils.trustAllHostnameVerifier
            }
        }

        // Delegate to the original implementation
        return delegate.open(dataSpec)
    }
}