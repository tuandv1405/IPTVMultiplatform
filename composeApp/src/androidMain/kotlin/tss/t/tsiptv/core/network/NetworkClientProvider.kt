package tss.t.tsiptv.core.network

/**
 * Android-specific implementation of NetworkClientProvider.
 * Uses OkHttpKtorNetworkClient for improved performance and reliability.
 */
class AndroidNetworkClientProvider() : NetworkClientProvider {
    private lateinit var _client: OkHttpKtorNetworkClient

    override fun getNetworkClient(): NetworkClient {
        if (!this::_client.isInitialized) {
            _client = OkHttpKtorNetworkClient()
        }
        return _client
    }
}

