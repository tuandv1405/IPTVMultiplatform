package tss.t.tsiptv.core.network

/**
 * Actual implementation of NetworkConnectivityCheckerFactory for iOS.
 */
actual object NetworkConnectivityCheckerFactory {
    /**
     * Creates an iOS-specific NetworkConnectivityChecker.
     * @return A NetworkConnectivityChecker instance for iOS.
     */
    actual fun create(): NetworkConnectivityChecker {
        return IosNetworkConnectivityChecker()
    }
}