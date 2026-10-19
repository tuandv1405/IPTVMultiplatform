package tss.t.tsiptv.feature.lan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIDevice
import platform.posix.memcpy
import tss.t.tsiptv.feature.account.DeviceNameProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The phone side of the LAN link on iOS (cast / send to TV, docs/prd-tv-cast-and-sync.md). The
 * platform work is in Swift (`iosApp/iosApp/LanBridge.swift`: Bonjour, Network.framework, CryptoKit)
 * and registered at app start through [IosLan]. An iPhone is never a receiver: [LanServer] stays
 * [UnsupportedLanServer].
 *
 * Exported to Swift as protocols. All callbacks may come on any thread.
 */
interface IosLanBridge {
    /** Browses `_tsiptv._tcp`; [listener] gets the full list on each change until [IosLanTask.cancel]. */
    fun startBrowsing(listener: IosLanBrowseListener): IosLanTask

    /** IPv4 Wi-Fi / Ethernet networks of this device, for the address scan. */
    fun localNetworks(): List<LanNetwork>

    /** One request line, one answer line (without the `\n`); [completion] gets exactly one call. */
    fun exchange(
        host: String,
        port: Int,
        line: String,
        maxAnswerBytes: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        completion: IosLanCompletion,
    ): IosLanTask

    /** A fresh P-256 key pair; null when the platform cannot do ECDH. */
    fun createKeyAgreement(): IosLanKeyAgreement?
}

interface IosLanTask {
    fun cancel()
}

interface IosLanBrowseListener {
    fun onDevices(devices: List<LanDevice>)
}

interface IosLanCompletion {
    /** [answer] null means failure; [error] is for debug logs only. */
    fun onResult(answer: String?, error: String?)
}

interface IosLanKeyAgreement {
    /** X.509 SubjectPublicKeyInfo DER, like `java.security` encodes it. */
    val publicKeyDer: NSData

    /** The raw ECDH secret with [peerPublicKeyDer]; null on a malformed key. */
    fun agree(peerPublicKeyDer: NSData): NSData?
}

/** Set from Swift in `iOSApp.init()`: `IosLan.shared.bridge = LanBridge()`. */
object IosLan {
    var bridge: IosLanBridge? = null
}

class IosLanDiscovery : LanDiscovery {
    override val isSupported: Boolean get() = IosLan.bridge != null

    override fun localNetworks(): List<LanNetwork> = IosLan.bridge?.localNetworks().orEmpty()

    override fun discover(): Flow<List<LanDevice>> = callbackFlow {
        trySend(emptyList())
        val task = IosLan.bridge?.startBrowsing(object : IosLanBrowseListener {
            override fun onDevices(devices: List<LanDevice>) {
                trySend(devices)
            }
        })
        awaitClose { task?.cancel() }
    }
}

object IosLanTransport : LanTransport {
    override suspend fun exchange(host: String, port: Int, line: String, maxAnswerBytes: Int): String =
        exchange(host, port, line, maxAnswerBytes, LanProtocol.CONNECT_TIMEOUT_MS, LanProtocol.READ_TIMEOUT_MS)

    override suspend fun probe(host: String, port: Int, line: String): String =
        exchange(host, port, line, LanProtocol.MAX_REQUEST_BYTES, LanProtocol.PROBE_CONNECT_TIMEOUT_MS, LanProtocol.PROBE_READ_TIMEOUT_MS)

    private suspend fun exchange(host: String, port: Int, line: String, maxAnswerBytes: Int, connectTimeoutMs: Int, readTimeoutMs: Int): String {
        val bridge = IosLan.bridge ?: throw UnsupportedOperationException("LAN not supported on this platform")
        return suspendCancellableCoroutine { cont ->
            val task = bridge.exchange(host, port, line, maxAnswerBytes, connectTimeoutMs, readTimeoutMs, object : IosLanCompletion {
                override fun onResult(answer: String?, error: String?) {
                    if (!cont.isActive) return
                    if (answer != null) cont.resume(answer) else cont.resumeWithException(IllegalStateException(error ?: "No answer"))
                }
            })
            cont.invokeOnCancellation { task.cancel() }
        }
    }
}

object IosLanKeyAgreementFactory : LanKeyAgreementFactory {
    override fun create(): LanKeyAgreement? {
        val agreement = IosLan.bridge?.createKeyAgreement() ?: return null
        return object : LanKeyAgreement {
            override val publicKey: ByteArray = agreement.publicKeyDer.toByteArray()

            override fun agree(peerPublicKey: ByteArray): ByteArray =
                agreement.agree(peerPublicKey.toNSData())?.toByteArray() ?: throw IllegalArgumentException("Not a P-256 key")
        }
    }
}

/** The name in Settings > General > About (iOS 16+ gives "iPhone" without a special entitlement). */
object IosDeviceNameProvider : DeviceNameProvider {
    override fun deviceName(): String = UIDevice.currentDevice.name.ifBlank { "iPhone" }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { out -> out.usePinned { memcpy(it.addressOf(0), bytes, length) } }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }
