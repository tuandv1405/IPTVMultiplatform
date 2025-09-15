package tss.t.tsiptv.core.network

/**
 * Android implementation of NetworkClientFactory.
 * This object is used to create a platform-specific NetworkClientProvider.
 */
actual object NetworkClientFactory {
    private var networkClientProvider: AndroidNetworkClientProvider? = null

    /**
     * Creates an Android-specific NetworkClientProvider.
     * @return A NetworkClientProvider instance for Android.
     * @throws IllegalStateException if initialize has not been called.
     */
    actual fun create(): NetworkClientProvider {
        return networkClientProvider ?: AndroidNetworkClientProvider().also {
            networkClientProvider = it
        }
    }

    actual fun get(): NetworkClientProvider {
        return networkClientProvider ?: AndroidNetworkClientProvider().also {
            networkClientProvider = it
        }
    }
}