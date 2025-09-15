package tss.t.tsiptv.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.net.NetworkInterface

/**
 * Desktop implementation of NetworkConnectivityChecker.
 * Uses Java's NetworkInterface to check network connectivity.
 */
class DesktopNetworkConnectivityChecker : NetworkConnectivityChecker {
    override fun isNetworkAvailable(): Boolean {
        // Check if there are any non-loopback, up network interfaces
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { !it.isLoopback && it.isUp }
            .any()
    }

    override fun observeNetworkStatus(): Flow<Boolean> {
        // For desktop, we'll just return a flow with the current status
        // In a real app, you might want to periodically check the status
        return flowOf(isNetworkAvailable())
    }
}
