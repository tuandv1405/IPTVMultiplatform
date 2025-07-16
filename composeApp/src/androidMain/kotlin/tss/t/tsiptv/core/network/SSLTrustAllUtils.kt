package tss.t.tsiptv.core.network

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Utility class for SSL trust all functionality.
 * WARNING: This should only be used for development or in very specific cases
 * where certificate validation is not required. Using this in production
 * can lead to security vulnerabilities.
 */
object SSLTrustAllUtils {

    /**
     * Creates a TrustManager that trusts all certificates.
     */
    fun createTrustAllTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // No-op, trust all clients
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // No-op, trust all servers
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }
    }

    /**
     * Creates an SSLSocketFactory that trusts all certificates.
     */
    fun createTrustAllSSLSocketFactory(): SSLSocketFactory {
        val trustManager = createTrustAllTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return sslContext.socketFactory
    }

    /**
     * Creates a HostnameVerifier that accepts all hostnames.
     */
    val trustAllHostnameVerifier = HostnameVerifier { _, _ -> true }
}