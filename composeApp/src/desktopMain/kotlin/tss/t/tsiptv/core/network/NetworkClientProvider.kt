package tss.t.tsiptv.core.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Desktop-specific implementation of KtorNetworkClient using CIO engine.
 */
class DesktopKtorNetworkClient : KtorNetworkClient() {
    /**
     * The Ktor HttpClient instance configured with CIO engine for Desktop.
     */
    override val client: HttpClient = HttpClient(CIO) {
        // Configure CIO engine
        engine {
            // Configure connection settings
            maxConnectionsCount = 1000
            endpoint {
                connectTimeout = 5000
                requestTimeout = 30000
                socketTimeout = 30000
            }
        }

        // Add content negotiation for JSON
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        // Add logging for debug builds
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }
}

/**
 * Desktop-specific implementation of NetworkClientProvider.
 */
class DesktopNetworkClientProvider : NetworkClientProvider {
    override fun getNetworkClient(): NetworkClient {
        return DesktopKtorNetworkClient()
    }
}

/**
 * Desktop implementation of NetworkClientFactory.
 */
actual object NetworkClientFactory {
    /**
     * Creates a Desktop-specific NetworkClientProvider.
     * @return A NetworkClientProvider instance for Desktop.
     */
    actual fun create(): NetworkClientProvider {
        return DesktopNetworkClientProvider()
    }
}
