package tss.t.tsiptv.core.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Enum representing different WiFi internet connection states.
 */
enum class WifiInternetStatus {
    CONNECTED_WIFI_INTERNET,      // Connected to Wi-Fi with validated Internet
    CONNECTED_WIFI_NO_INTERNET,   // Connected to Wi-Fi, but no validated Internet
    NOT_CONNECTED_TO_WIFI,       // Not connected to any Wi-Fi network (might be on mobile data or nothing)
    NO_NETWORK_CONNECTION        // No active network connection at all
}

class AndroidNetworkConnectivityChecker(
    private val context: Context,
) : NetworkConnectivityChecker {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun isNetworkAvailable(): Boolean {
        // Use the WiFi internet status to determine if network is available
        val status = getWifiInternetStatus()
        return status == WifiInternetStatus.CONNECTED_WIFI_INTERNET ||
                status == WifiInternetStatus.NOT_CONNECTED_TO_WIFI // This includes mobile data connections
    }

    /**
     * Gets the current WiFi internet connection status.
     *
     * @return The current WiFi internet status
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getWifiInternetStatus(): WifiInternetStatus {
        val network = connectivityManager.activeNetwork
        if (network == null) {
            return WifiInternetStatus.NO_NETWORK_CONNECTION
        }

        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            return WifiInternetStatus.NO_NETWORK_CONNECTION
        }
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                WifiInternetStatus.CONNECTED_WIFI_INTERNET
            } else {
                WifiInternetStatus.CONNECTED_WIFI_NO_INTERNET
            }
        } else {
            WifiInternetStatus.NOT_CONNECTED_TO_WIFI
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun observeNetworkStatus(): Flow<Boolean> {
        return callbackFlow {
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // When a network becomes available, check if it provides internet connectivity
                    trySend(isNetworkAvailable())
                }

                override fun onLost(network: Network) {
                    // When a network is lost, check if there's another network providing internet connectivity
                    trySend(isNetworkAvailable())
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    // When network capabilities change (e.g., internet validation status),
                    // check if it affects internet connectivity
                    trySend(isNetworkAvailable())
                }
            }

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

            // Send the initial value
            trySend(isNetworkAvailable())

            awaitClose {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        }
    }
}

