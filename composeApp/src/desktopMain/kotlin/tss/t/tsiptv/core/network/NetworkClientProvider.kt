package tss.t.tsiptv.core.network

/**
 * Desktop-specific implementation of NetworkClientProvider.
 */
class DesktopNetworkClientProvider : NetworkClientProvider {
    override fun getNetworkClient(): NetworkClient {
        return DesktopKtorNetworkClient()
    }
}
