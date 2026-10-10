package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import tss.t.tsiptv.feature.account.LocalDevice

/** Outcome of a phone → TV operation, for the UI. */
sealed interface LanResult {
    data object Ok : LanResult

    /** The TV does not know this phone (never paired, or it forgot it): pair again. */
    data object Unpaired : LanResult

    /** The TV could not be reached (closed, other network, firewall). */
    data object Unreachable : LanResult

    /** The TV answered with an error [code] ([LanErrorCode]). */
    data class Refused(val code: String) : LanResult
}

/** A pairing in progress, between `pair_start` and the code the user types. */
class PairingHandle internal constructor(
    val device: LanDevice,
    internal val sessionId: String,
    internal val key: ByteArray,
) {
    override fun toString(): String = "PairingHandle(device=$device)"
}

sealed interface PairStartResult {
    data class Started(val handle: PairingHandle) : PairStartResult
    data class Failed(val result: LanResult) : PairStartResult
}

/**
 * The phone side of protocol v1. Stateless apart from [PairingStore]; the transport is injected so
 * tests can wire it straight to a [LanReceiverEngine].
 */
class LanSender(
    private val transport: LanTransport,
    private val store: PairingStore,
    private val local: LocalDevice,
    private val keyAgreement: LanKeyAgreementFactory,
    private val clock: () -> Long,
) {
    val isSupported: Boolean by lazy { keyAgreement.create() != null }

    private suspend fun call(device: LanDevice, request: LanRequest, maxBytes: Int = LanProtocol.MAX_REQUEST_BYTES): LanResponse? {
        val line = LanProtocol.json.encodeToString(LanRequest.serializer(), request)
        if (line.encodeToByteArray().size > maxBytes) return LanResponse.error(LanErrorCode.TOO_LARGE)
        return try {
            val answer = transport.exchange(device.host, device.port, line)
            LanProtocol.json.decodeFromString(LanResponse.serializer(), answer)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SerializationException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Connect by IP: asks the TV who it is. null when nothing TS IPTV answers there. */
    suspend fun hello(host: String, port: Int): LanDevice? {
        val probe = LanDevice(id = "", name = host, host = host, port = port)
        val answer = call(probe, HelloRequest()) ?: return null
        if (!answer.ok || answer.id.isNullOrBlank()) return null
        return probe.copy(id = answer.id, name = answer.name?.let(LanValidation::cleanName)?.ifBlank { null } ?: host, version = answer.v ?: 1)
    }

    suspend fun isPaired(device: LanDevice): Boolean = device.id.isNotEmpty() && store.keyFor(device.id) != null

    suspend fun startPairing(device: LanDevice): PairStartResult {
        val agreement = keyAgreement.create() ?: return PairStartResult.Failed(LanResult.Refused(LanErrorCode.NOT_ACCEPTING))
        val answer = call(
            device,
            PairStartRequest(senderId = local.installationId(), senderName = local.name(), pub = LanCrypto.b64(agreement.publicKey)),
        ) ?: return PairStartResult.Failed(LanResult.Unreachable)
        if (!answer.ok) return PairStartResult.Failed(LanResult.Refused(answer.code ?: LanErrorCode.BAD_REQUEST))
        val tvPub = answer.pub?.let(LanCrypto::unb64) ?: return PairStartResult.Failed(LanResult.Refused(LanErrorCode.BAD_REQUEST))
        val sessionId = answer.sessionId ?: return PairStartResult.Failed(LanResult.Refused(LanErrorCode.BAD_REQUEST))
        val tvId = answer.id ?: return PairStartResult.Failed(LanResult.Refused(LanErrorCode.BAD_REQUEST))
        // A device found by mDNS must be the one that answers.
        if (device.id.isNotEmpty() && device.id != tvId) return PairStartResult.Failed(LanResult.Refused(LanErrorCode.BAD_REQUEST))
        val secret = try {
            agreement.agree(tvPub)
        } catch (_: Exception) {
            return PairStartResult.Failed(LanResult.Refused(LanErrorCode.BAD_REQUEST))
        }
        val key = LanCrypto.pairKey(secret, agreement.publicKey, tvPub)
        val named = device.copy(id = tvId, name = answer.name?.let(LanValidation::cleanName)?.ifBlank { null } ?: device.name)
        return PairStartResult.Started(PairingHandle(named, sessionId, key))
    }

    /** Sends the code the user typed. [LanResult.Ok] stores the pair key. */
    suspend fun confirmPairing(handle: PairingHandle, code: String): LanResult {
        val answer = call(handle.device, PairConfirmRequest(handle.sessionId, LanCrypto.pairProof(handle.key, code.trim())))
            ?: return LanResult.Unreachable
        if (!answer.ok) return LanResult.Refused(answer.code ?: LanErrorCode.BAD_REQUEST)
        store.put(handle.device.id, handle.device.name, handle.key, clock())
        return LanResult.Ok
    }

    suspend fun send(device: LanDevice, command: LanCommand): LanResult {
        val key = store.keyFor(device.id) ?: return LanResult.Unpaired
        val body = LanProtocol.bodyJson.encodeToString(LanCommand.serializer(), command)
        val senderId = local.installationId()
        val ts = clock()
        val nonce = LanCrypto.newNonce()
        val request = SignedRequest(senderId = senderId, ts = ts, nonce = nonce, body = body, mac = LanCrypto.sign(key, senderId, ts, nonce, body))
        val max = if (command is PlaylistCommand) LanProtocol.MAX_OFFER_BYTES else LanProtocol.MAX_REQUEST_BYTES
        val answer = call(device, request, max) ?: return LanResult.Unreachable
        return when {
            answer.ok -> LanResult.Ok
            answer.code == LanErrorCode.UNPAIRED -> {
                store.remove(device.id)
                LanResult.Unpaired
            }
            else -> LanResult.Refused(answer.code ?: LanErrorCode.BAD_REQUEST)
        }
    }

    suspend fun forget(deviceId: String) = store.remove(deviceId)
}
