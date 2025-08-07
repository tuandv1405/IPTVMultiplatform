package tss.t.tsiptv.core.network

/**
 * iOS implementation of NetworkClientFactory.
 */
actual object NetworkClientFactory {
    private var _def = IosNetworkClientProvider()
    /**
     * Creates an iOS-specific NetworkClientProvider.
     * @return A NetworkClientProvider instance for iOS.
     */
    actual fun create(): NetworkClientProvider {
        return IosNetworkClientProvider()
    }

    actual fun get(): NetworkClientProvider {
        return _def
    }
}