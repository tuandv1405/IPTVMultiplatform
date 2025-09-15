package tss.t.tsiptv.core.network

/**
 * Expect declaration for the platform-specific NetworkConnectivityChecker.
 * Each platform will provide its own implementation.
 */
expect object NetworkConnectivityCheckerFactory {
    /**
     * Creates a platform-specific NetworkConnectivityChecker.
     * @return A NetworkConnectivityChecker instance appropriate for the current platform.
     */
    fun create(): NetworkConnectivityChecker
}
