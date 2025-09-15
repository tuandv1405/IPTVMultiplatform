package tss.t.tsiptv.core.network

/**
 * Desktop implementation of NetworkClientFactory.
 */
actual object NetworkClientFactory {
    private val _def = DesktopNetworkClientProvider()

    /**
     * Creates a Desktop-specific NetworkClientProvider.
     * @return A NetworkClientProvider instance for Desktop.
     */
    actual fun create(): NetworkClientProvider {
        return DesktopNetworkClientProvider()
    }

    actual fun get(): NetworkClientProvider {
        return _def
    }
}
