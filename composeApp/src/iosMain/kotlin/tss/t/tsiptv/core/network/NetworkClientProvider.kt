package tss.t.tsiptv.core.network

import io.ktor.client.*
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.xml
import kotlinx.serialization.json.Json

/**
 * iOS-specific implementation of KtorNetworkClient.
 * Uses the default Ktor client for iOS.
 */
class IosKtorNetworkClient : KtorNetworkClient() {
    /**
     * The Ktor HttpClient instance for iOS.
     * The Darwin engine is automatically used on iOS platforms.
     */
    override val client: HttpClient = HttpClient {
        // Add content negotiation for JSON
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
            xml()
        }

        // Add logging for debug builds
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }


        install(ContentEncoding) {
            deflate(1.0F)
            gzip(0.9F)
        }
    }
}

/**
 * iOS-specific implementation of NetworkClientProvider.
 */
class IosNetworkClientProvider : NetworkClientProvider {
    override fun getNetworkClient(): NetworkClient {
        return IosKtorNetworkClient()
    }
}

/**
 * iOS implementation of NetworkClientFactory.
 */
actual object NetworkClientFactory {
    /**
     * Creates an iOS-specific NetworkClientProvider.
     * @return A NetworkClientProvider instance for iOS.
     */
    actual fun create(): NetworkClientProvider {
        return IosNetworkClientProvider()
    }
}
