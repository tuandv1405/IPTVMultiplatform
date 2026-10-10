package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Platform pieces of the LAN link. Android implements them (NSD + sockets, `androidMain`); iOS and
 * desktop use the `Unsupported*` objects below until they get their own (PRD §1 non-goals).
 */

/** Browses `_tsiptv._tcp`. Emits the full list each time it changes; stops when collection stops. */
interface LanDiscovery {
    val isSupported: Boolean
    fun discover(): Flow<List<LanDevice>>
}

/** Sends one request line and returns the answer line. Throws on any network failure. */
interface LanTransport {
    suspend fun exchange(host: String, port: Int, line: String, maxAnswerBytes: Int = LanProtocol.MAX_REQUEST_BYTES): String
}

/** The TV's TCP server and its mDNS registration. */
interface LanServer {
    val isSupported: Boolean

    /**
     * Starts listening and advertises the port as [serviceName] with [deviceId] in TXT. Listens on
     * [preferredPort] when it is free (a stable port per install, for "connect by IP"), else on a
     * random one. Every request line goes to [handler]; its result is the answer. Returns the port.
     *
     * A request line may exceed [LanProtocol.MAX_REQUEST_BYTES] (up to [LanProtocol.MAX_OFFER_BYTES])
     * only when [largeRequestAllowed] accepts its first [LanProtocol.MAX_REQUEST_BYTES] bytes, i.e.
     * a signed request from a paired sender: nobody unauthenticated can make the TV buffer more.
     */
    suspend fun start(
        serviceName: String,
        deviceId: String,
        preferredPort: Int = 0,
        largeRequestAllowed: suspend (prefix: String) -> Boolean = { false },
        handler: suspend (String) -> String,
    ): Int

    suspend fun stop()

    /** This device's LAN IPv4 addresses, for "connect by IP". */
    fun localAddresses(): List<String>
}

object UnsupportedLanDiscovery : LanDiscovery {
    override val isSupported = false
    override fun discover(): Flow<List<LanDevice>> = flowOf(emptyList())
}

object UnsupportedLanTransport : LanTransport {
    override suspend fun exchange(host: String, port: Int, line: String, maxAnswerBytes: Int): String =
        throw UnsupportedOperationException("LAN not supported on this platform")
}

object UnsupportedLanServer : LanServer {
    override val isSupported = false
    override suspend fun start(
        serviceName: String,
        deviceId: String,
        preferredPort: Int,
        largeRequestAllowed: suspend (prefix: String) -> Boolean,
        handler: suspend (String) -> String,
    ): Int = throw UnsupportedOperationException("LAN not supported on this platform")

    override suspend fun stop() = Unit
    override fun localAddresses(): List<String> = emptyList()
}
