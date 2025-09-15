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

