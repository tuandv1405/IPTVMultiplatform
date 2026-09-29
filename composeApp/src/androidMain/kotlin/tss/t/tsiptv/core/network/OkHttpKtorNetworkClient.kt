package tss.t.tsiptv.core.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.xml
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Android-specific implementation of KtorNetworkClient using OkHttp engine.
 * OkHttp is a widely used HTTP client for Android that provides efficient connection pooling,
 * transparent GZIP compression, and response caching.
 */
class OkHttpKtorNetworkClient() : KtorNetworkClient() {

    /**
     * The Ktor HttpClient instance configured with OkHttp engine.
     */
    override val client: HttpClient = HttpClient(OkHttp) {
        // Configure OkHttp engine
        engine {
            // Configure connection settings
            config {
                connectTimeout(60, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(120, TimeUnit.SECONDS)
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

        // Request lines, headers and bodies here are playlist links, tokens and whole
        // playlists; none of it may reach logcat. Raise the level locally when debugging.
        install(Logging) {
            logger = Logger.ANDROID
            level = LogLevel.NONE
            this.format = LoggingFormat.Default
        }

        install(ContentEncoding) {
            gzip()
            deflate()
        }
    }
}
