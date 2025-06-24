package tss.t.tsiptv.core.network

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android-specific implementation of NetworkClientProvider.
 * Uses OkHttpKtorNetworkClient for improved performance and reliability.
 */
class AndroidNetworkClientProvider(private val context: Context) : NetworkClientProvider {
    override fun getNetworkClient(): NetworkClient {
        return OkHttpKtorNetworkClient(context)
    }
}

/**
 * Android implementation of NetworkClientFactory.
 * This object is used to create a platform-specific NetworkClientProvider.
 */
actual object NetworkClientFactory {
    private var applicationContext: Context? = null

    /**
     * Initialize the factory with the application context.
     * This should be called early in the application lifecycle.
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Creates an Android-specific NetworkClientProvider.
     * @return A NetworkClientProvider instance for Android.
     * @throws IllegalStateException if initialize has not been called.
     */
    actual fun create(): NetworkClientProvider {
        val context = applicationContext
            ?: throw IllegalStateException("NetworkClientFactory not initialized. Call initialize() first.")
        return AndroidNetworkClientProvider(context)
    }
}

/**
 * Composable function to get a NetworkClient instance.
 * This is a convenience function for use in Composable functions.
 */
@Composable
fun rememberNetworkClient(): NetworkClient {
    val context = LocalContext.current
    // Initialize the factory if it hasn't been already
    NetworkClientFactory.initialize(context)
    return remember { NetworkClientFactory.create().getNetworkClient() }
}
