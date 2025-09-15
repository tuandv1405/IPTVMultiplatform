package tss.t.tsiptv.core.network

/**
 * Actual implementation of NetworkConnectivityCheckerFactory for Desktop.
 */
actual object NetworkConnectivityCheckerFactory {
    /**
     * Creates a Desktop-specific NetworkConnectivityChecker.
     * @return A NetworkConnectivityChecker instance for Desktop.
     */
    actual fun create(): NetworkConnectivityChecker {
        return DesktopNetworkConnectivityChecker()
    }
}