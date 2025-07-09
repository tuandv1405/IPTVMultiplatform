package tss.t.tsiptv.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Interface for checking network connectivity status.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface NetworkConnectivityChecker {
    /**
     * Checks if the device is currently connected to a network.
     *
     * @return true if the device is connected to a network, false otherwise
     */
    fun isNetworkAvailable(): Boolean

    /**
     * Returns a Flow that emits network connectivity status changes.
     *
     * @return A Flow emitting true when network is available and false when it's not
     */
    fun observeNetworkStatus(): Flow<Boolean>
}

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