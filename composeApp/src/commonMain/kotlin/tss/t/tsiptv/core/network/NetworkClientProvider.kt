package tss.t.tsiptv.core.network

/**
 * Interface for providing platform-specific NetworkClient instances.
 * This allows for different implementations on different platforms.
 */
interface NetworkClientProvider {
    /**
     * Get a platform-specific NetworkClient instance.
     * @return A NetworkClient instance appropriate for the current platform.
     */
    fun getNetworkClient(): NetworkClient
}

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
}
