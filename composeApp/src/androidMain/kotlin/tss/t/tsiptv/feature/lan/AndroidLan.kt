package tss.t.tsiptv.feature.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tss.t.tsiptv.feature.account.DeviceNameProvider
import java.io.ByteArrayOutputStream
import java.io.InputStream
import android.os.SystemClock
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.KeyAgreement
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Ephemeral ECDH P-256 with the platform JCA provider (Conscrypt on Android). */
class JvmLanKeyAgreement : LanKeyAgreement {
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    override val publicKey: ByteArray = pair.public.encoded

    override fun agree(peerPublicKey: ByteArray): ByteArray {
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicKey)) as ECPublicKey
        val own = pair.public as ECPublicKey
        require(peer.params.curve == own.params.curve) { "Not P-256" }
        return KeyAgreement.getInstance("ECDH").run {
            init(pair.private)
            doPhase(peer, true)
            generateSecret()
        }
    }
}

object JvmLanKeyAgreementFactory : LanKeyAgreementFactory {
    override fun create(): LanKeyAgreement? = runCatching { JvmLanKeyAgreement() }.getOrNull()
}

/** "Living room TV" (Settings > Device name) when set, else the model. */
class AndroidDeviceNameProvider(private val context: Context) : DeviceNameProvider {
    override fun deviceName(): String {
        val userSet = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        if (!userSet.isNullOrBlank()) return userSet
        val model = Build.MODEL.orEmpty()
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }
}

/** Reads one `\n`-terminated line of at most [max] bytes; null when it is longer (or never ends). */
internal fun InputStream.readLineCapped(max: Int): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) return if (out.size() > 0) out.toString(Charsets.UTF_8.name()) else null
        val newline = (0 until n).firstOrNull { buffer[it] == '\n'.code.toByte() }
        val take = newline ?: n
        if (out.size() + take > max) return null
        out.write(buffer, 0, take)
        if (newline != null) return out.toString(Charsets.UTF_8.name())
    }
}

/** One request per TCP connection (protocol v1). */
object SocketLanTransport : LanTransport {
    override suspend fun exchange(host: String, port: Int, line: String, maxAnswerBytes: Int): String =
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), LanProtocol.CONNECT_TIMEOUT_MS)
                socket.soTimeout = LanProtocol.READ_TIMEOUT_MS
                socket.getOutputStream().apply {
                    write((line + "\n").toByteArray(Charsets.UTF_8))
                    flush()
                }
                socket.getInputStream().readLineCapped(maxAnswerBytes) ?: error("No answer")
            }
        }
}

/** Browses `_tsiptv._tcp` with NsdManager and resolves each service (one at a time, as old APIs need). */
class NsdLanDiscovery(private val context: Context) : LanDiscovery {
    override val isSupported: Boolean = true

    @Suppress("DEPRECATION")
    override fun discover(): Flow<List<LanDevice>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = ConcurrentHashMap<String, LanDevice>()
        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                trySend(found.values.toList())
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.trimEnd('.').endsWith(LanProtocol.SERVICE_TYPE)) toResolve.trySend(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                if (found.remove(info.serviceName) != null) trySend(found.values.toList())
            }
        }
        trySend(emptyList())
        nsd.discoverServices(LanProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        launch(Dispatchers.IO) {
            for (info in toResolve) {
                val resolved = withTimeoutOrNull(5_000) {
                    suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                if (cont.isActive) cont.resume(null)
                            }
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (cont.isActive) cont.resume(serviceInfo)
                            }
                        })
                    }
                } ?: continue
                val host = resolved.host?.hostAddress ?: continue
                val attrs = resolved.attributes
                val id = attrs[LanProtocol.TXT_ID]?.toString(Charsets.UTF_8).orEmpty()
                val version = attrs[LanProtocol.TXT_VERSION]?.toString(Charsets.UTF_8)?.toIntOrNull() ?: 1
                found[info.serviceName] = LanDevice(id = id, name = resolved.serviceName, host = host, port = resolved.port, version = version)
                trySend(found.values.toList())
            }
        }
        awaitClose {
            toResolve.close()
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}

/**
 * The TV's receiver: a TCP server plus its NSD registration (protocol v1, one request per
 * connection). Abuse limits (QC round 1):
 * - only peers on the local network (site-local, link-local, unique-local IPv6) or this device
 *   itself (loopback, e.g. `adb forward`) are served;
 * - at most [MAX_CONNECTIONS] connections, at most [MAX_PER_IP] per remote LAN address;
 * - every connection has a total deadline of [LanProtocol.READ_TIMEOUT_MS] from accept (not per
 *   read), so trickling bytes cannot hold a slot;
 * - an unauthenticated request is read up to [LanProtocol.MAX_REQUEST_BYTES]; only a signed request
 *   from a paired phone may go on up to [LanProtocol.MAX_OFFER_BYTES].
 */
class NsdSocketLanServer(private val context: Context) : LanServer {
    override val isSupported: Boolean = true

    private var scope: CoroutineScope? = null
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private val active = AtomicInteger(0)
    private val perAddress = ConcurrentHashMap<InetAddress, AtomicInteger>()

    override suspend fun start(
        serviceName: String,
        deviceId: String,
        preferredPort: Int,
        largeRequestAllowed: suspend (prefix: String) -> Boolean,
        handler: suspend (String) -> String,
    ): Int = withContext(Dispatchers.IO) {
        stop()
        val socket = bind(preferredPort)
        server = socket
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serverScope
        serverScope.launch {
            while (isActive) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                val remote = client.inetAddress
                if (remote == null || !isLocalPeer(remote)) {
                    runCatching { client.close() }
                    continue
                }
                val forAddress = perAddress.computeIfAbsent(remote) { AtomicInteger(0) }
                // Loopback is this device itself (or `adb forward` during development): no LAN peer
                // can use it, so only the total cap applies there.
                val perIpCap = if (remote.isLoopbackAddress) MAX_CONNECTIONS else MAX_PER_IP
                if (active.incrementAndGet() > MAX_CONNECTIONS || forAddress.incrementAndGet() > perIpCap) {
                    active.decrementAndGet()
                    forAddress.decrementAndGet()
                    runCatching { client.close() }
                    continue
                }
                launch {
                    try {
                        serve(client, largeRequestAllowed, handler)
                    } finally {
                        active.decrementAndGet()
                        if (forAddress.decrementAndGet() <= 0) perAddress.remove(remote, forAddress)
                    }
                }
            }
        }
        register(serviceName, deviceId, socket.localPort)
        socket.localPort
    }

    /** [preferred] when it is a free unprivileged port, else a random one. */
    private fun bind(preferred: Int): ServerSocket {
        if (preferred in 1024..65535) {
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(preferred))
                return socket
            } catch (_: Exception) {
                runCatching { socket.close() }
            }
        }
        return ServerSocket(0)
    }

    private suspend fun serve(
        client: Socket,
        largeRequestAllowed: suspend (prefix: String) -> Boolean,
        handler: suspend (String) -> String,
    ) = coroutineScope {
        val acceptedAt = SystemClock.elapsedRealtime()
        val deadline = java.util.concurrent.atomic.AtomicLong(acceptedAt + LanProtocol.READ_TIMEOUT_MS)
        // Closing the socket is the only way to interrupt a blocking read: do it at the deadline
        // (which a granted large offer may move later, see readRequestLine).
        val watchdog = launch {
            while (true) {
                val left = deadline.get() - SystemClock.elapsedRealtime()
                if (left <= 0) break
                delay(left)
            }
            runCatching { client.close() }
        }
        try {
            client.use { s ->
                runCatching {
                    val line = s.getInputStream().readRequestLine(s, deadline, acceptedAt, largeRequestAllowed)
                    val answer = if (line == null) {
                        LanProtocol.json.encodeToString(LanResponse.serializer(), LanResponse.error(LanErrorCode.TOO_LARGE))
                    } else {
                        handler(line)
                    }
                    s.getOutputStream().apply {
                        write((answer + "\n").toByteArray(Charsets.UTF_8))
                        flush()
                    }
                }
            }
        } finally {
            watchdog.cancel()
        }
    }

    /**
     * One `\n`-terminated request line, read within [deadline]. Up to [LanProtocol.MAX_REQUEST_BYTES]
     * for anyone; beyond that only when [largeRequestAllowed] accepts the start of the line (checked
     * once), up to [LanProtocol.MAX_OFFER_BYTES]. null when too long.
     */
    private suspend fun InputStream.readRequestLine(
        socket: Socket,
        deadline: java.util.concurrent.atomic.AtomicLong,
        acceptedAt: Long,
        largeRequestAllowed: suspend (prefix: String) -> Boolean,
    ): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var max = LanProtocol.MAX_REQUEST_BYTES
        var checked = false
        while (true) {
            val left = deadline.get() - SystemClock.elapsedRealtime()
            if (left <= 0) throw SocketTimeoutException("deadline")
            socket.soTimeout = left.toInt().coerceAtLeast(1)
            val n = read(buffer)
            if (n < 0) return if (out.size() > 0) out.toString(Charsets.UTF_8.name()) else null
            val newline = (0 until n).firstOrNull { buffer[it] == '\n'.code.toByte() }
            val take = newline ?: n
            if (out.size() + take > max) {
                if (checked) return null
                checked = true
                // Decide on the first 64 KiB: they carry the request type and the senderId.
                val room = max - out.size()
                out.write(buffer, 0, room)
                if (!largeRequestAllowed(out.toString(Charsets.UTF_8.name()))) return null
                max = LanProtocol.MAX_OFFER_BYTES
                // A large offer from a paired phone may take longer: 10 s + 1 s per 100 KB of the
                // offer limit, at most 30 s from accept.
                deadline.set(acceptedAt + LARGE_DEADLINE_MS)
                if (out.size() + (take - room) > max) return null
                out.write(buffer, room, take - room)
            } else {
                out.write(buffer, 0, take)
            }
            if (newline != null) return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun register(serviceName: String, deviceId: String, port: Int) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = LanProtocol.SERVICE_TYPE
            this.port = port
            setAttribute(LanProtocol.TXT_ID, deviceId)
            setAttribute(LanProtocol.TXT_VERSION, LanProtocol.VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            registration?.let { listener ->
                val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                runCatching { nsd.unregisterService(listener) }
            }
            registration = null
            runCatching { server?.close() }
            server = null
            scope?.cancel()
            scope = null
        }
    }

    override fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_CONNECTIONS = 8
        const val MAX_PER_IP = 2

        /** 10 s + MAX_OFFER_BYTES / (100 KB/s), capped at 30 s (about 25.7 s). */
        val LARGE_DEADLINE_MS: Long =
            minOf(30_000L, LanProtocol.READ_TIMEOUT_MS + LanProtocol.MAX_OFFER_BYTES.toLong() * 1000 / (100 * 1024))

        /** Same device, or a private / link-local LAN address: never a routed public peer. */
        fun isLocalPeer(address: InetAddress): Boolean {
            val a = (address as? Inet6Address)?.let { v6 ->
                // IPv4-mapped (::ffff:a.b.c.d) is judged as IPv4.
                val b = v6.address
                if (b.take(10).all { it == 0.toByte() } && b[10] == 0xFF.toByte() && b[11] == 0xFF.toByte()) {
                    InetAddress.getByAddress(b.copyOfRange(12, 16))
                } else {
                    v6
                }
            } ?: address
            if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress) return true
            // IPv6 unique local addresses fc00::/7.
            return a is Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC
        }
    }
}
