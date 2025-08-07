package tss.t.tsiptv.core.network

/**
 * Expect declaration for the platform-specific NetworkClientProvider.
 * Each platform will provide its own implementation.
 */
expect object NetworkClientFactory {
    /**
     * Creates a platform-specific NetworkClientProvider.
     * @return A NetworkClientProvider instance appropriate for the current platform.
     */
    fun create(): NetworkClientProvider
    fun get(): NetworkClientProvider
}
