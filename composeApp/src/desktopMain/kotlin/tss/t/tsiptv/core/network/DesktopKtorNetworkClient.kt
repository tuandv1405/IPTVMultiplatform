package tss.t.tsiptv.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.xml
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
            xml()
        }

        // Add logging for debug builds
        install(Logging) {
            logger = Logger.Companion.DEFAULT
            level = LogLevel.INFO
        }


        install(ContentEncoding) {
            deflate(1.0F)
            gzip(0.9F)
        }
    }
}
