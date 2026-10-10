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
import java.net.Inet4Address
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
 * The TV's receiver: a TCP server on a random port plus its NSD registration. At most
 * [MAX_CONNECTIONS] requests at once; every request is read with a size cap and a timeout.
 */
class NsdSocketLanServer(private val context: Context) : LanServer {
    override val isSupported: Boolean = true

    private var scope: CoroutineScope? = null
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private val active = AtomicInteger(0)

    override suspend fun start(serviceName: String, deviceId: String, handler: suspend (String) -> String): Int =
        withContext(Dispatchers.IO) {
            stop()
            val socket = ServerSocket(0)
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
                    if (active.incrementAndGet() > MAX_CONNECTIONS) {
                        active.decrementAndGet()
                        runCatching { client.close() }
                        continue
                    }
                    launch {
                        try {
                            serve(client, handler)
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            }
            register(serviceName, deviceId, socket.localPort)
            socket.localPort
        }

    private suspend fun serve(client: Socket, handler: suspend (String) -> String) {
        client.use { s ->
            runCatching {
                s.soTimeout = LanProtocol.READ_TIMEOUT_MS
                val line = s.getInputStream().readLineCapped(LanProtocol.MAX_OFFER_BYTES)
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
        const val MAX_CONNECTIONS = 4
    }
}
