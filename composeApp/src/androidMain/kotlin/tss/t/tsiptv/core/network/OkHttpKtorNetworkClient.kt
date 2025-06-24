package tss.t.tsiptv.core.network

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Android-specific implementation of KtorNetworkClient using OkHttp engine.
 * OkHttp is a widely used HTTP client for Android that provides efficient connection pooling,
 * transparent GZIP compression, and response caching.
 */
class OkHttpKtorNetworkClient(context: Context) : KtorNetworkClient() {

    /**
     * The Ktor HttpClient instance configured with OkHttp engine.
     */
    override val client: HttpClient = HttpClient(OkHttp) {
        // Configure OkHttp engine
        engine {
            // Configure connection settings
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
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
