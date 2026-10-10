package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/** Shown full-screen on the TV while a phone pairs. */
data class PairingPrompt(val code: String, val senderName: String, val expiresAt: Long) {
    override fun toString(): String = "PairingPrompt(senderName=$senderName)"
}

sealed interface LanReceiverEvent {
    data class Paired(val senderName: String) : LanReceiverEvent
    data class CastReceived(val stream: CastStream, val senderName: String) : LanReceiverEvent
    data class PlaylistOffered(val playlist: SharedPlaylist, val senderName: String, val receivedAt: Long) : LanReceiverEvent
}

/**
 * The TV side of protocol v1, without any I/O: one request line in, one answer line out.
 * The platform server ([LanServer]) only moves bytes; every decision is made (and tested) here.
 */
class LanReceiverEngine(
    private val identity: suspend () -> Pair<String, String>,
    private val store: PairingStore,
    private val keyAgreement: LanKeyAgreementFactory,
    private val clock: () -> Long,
    private val codeGenerator: () -> String = LanCrypto::randomCode,
) {
    private class Session(
        val id: String,
        val senderId: String,
        val senderName: String,
        val key: ByteArray,
        val code: String,
        val expiresAt: Long,
        var wrongCodes: Int = 0,
    )

    private val mutex = Mutex()
    private var session: Session? = null
    private val failedSessions = ArrayDeque<Long>()
    private var lockedUntil = 0L
    private val nonces = LinkedHashMap<String, Long>()

    private val _prompt = MutableStateFlow<PairingPrompt?>(null)
    val pairingPrompt: StateFlow<PairingPrompt?> = _prompt

    private val _events = MutableSharedFlow<LanReceiverEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<LanReceiverEvent> = _events

    /** Off outside Home-like screens; offers are refused with NOT_ACCEPTING then. */
    var acceptingOffers: Boolean = true

    suspend fun handle(line: String): String = encode(handleRequest(line))

    private fun encode(response: LanResponse) = LanProtocol.json.encodeToString(LanResponse.serializer(), response)

    private suspend fun handleRequest(line: String): LanResponse {
        val size = line.encodeToByteArray().size
        if (size > LanProtocol.MAX_OFFER_BYTES) return LanResponse.error(LanErrorCode.TOO_LARGE)
        val request = try {
            LanProtocol.json.decodeFromString(LanRequest.serializer(), line)
        } catch (_: SerializationException) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        } catch (_: IllegalArgumentException) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        }
        if (request !is SignedRequest && size > LanProtocol.MAX_REQUEST_BYTES) return LanResponse.error(LanErrorCode.TOO_LARGE)
        return when (request) {
            is HelloRequest -> identity().let { (id, name) -> LanResponse(ok = true, id = id, name = name, v = LanProtocol.VERSION) }
            is PairStartRequest -> pairStart(request)
            is PairConfirmRequest -> pairConfirm(request)
            is SignedRequest -> signed(request, size)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pairing
    // ---------------------------------------------------------------------------------------------

    private suspend fun pairStart(request: PairStartRequest): LanResponse = mutex.withLock {
        if (request.v != LanProtocol.VERSION) return LanResponse.error(LanErrorCode.UNSUPPORTED_VERSION)
        if (request.senderId.isBlank() || request.senderId.length > 64 || request.senderName.isBlank()) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        }
        val now = clock()
        expireSession(now)
        if (now < lockedUntil) return LanResponse.error(LanErrorCode.LOCKED)
        if (session != null) return LanResponse.error(LanErrorCode.BUSY)
        val agreement = keyAgreement.create() ?: return LanResponse.error(LanErrorCode.NOT_ACCEPTING)
        val senderPub = LanCrypto.unb64(request.pub) ?: return LanResponse.error(LanErrorCode.BAD_REQUEST)
        val secret = try {
            agreement.agree(senderPub)
        } catch (_: Exception) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        }
        val key = LanCrypto.pairKey(secret, senderPub, agreement.publicKey)
        val name = request.senderName.take(LanValidation.MAX_NAME)
        val s = Session(
            id = LanCrypto.newNonce(),
            senderId = request.senderId,
            senderName = name,
            key = key,
            code = codeGenerator(),
            expiresAt = now + LanProtocol.PAIR_CODE_TTL_MS,
        )
        session = s
        _prompt.value = PairingPrompt(s.code, name, s.expiresAt)
        val (id, tvName) = identity()
        LanResponse(ok = true, id = id, name = tvName, v = LanProtocol.VERSION, sessionId = s.id, pub = LanCrypto.b64(agreement.publicKey))
    }

    private suspend fun pairConfirm(request: PairConfirmRequest): LanResponse {
        val paired: Session
        mutex.withLock {
            val now = clock()
            expireSession(now)
            val s = session
            if (s == null || s.id != request.sessionId) return LanResponse.error(LanErrorCode.EXPIRED)
            val expected = LanCrypto.unb64(LanCrypto.pairProof(s.key, s.code))!!
            val given = LanCrypto.unb64(request.proof)
            if (given == null || !LanCrypto.constantTimeEquals(expected, given)) {
                s.wrongCodes++
                if (s.wrongCodes >= LanProtocol.PAIR_MAX_WRONG_CODES) endSession(failed = true, now = now)
                return LanResponse.error(LanErrorCode.WRONG_CODE)
            }
            paired = s
            endSession(failed = false, now = now)
        }
        store.put(paired.senderId, paired.senderName, paired.key, clock())
        _events.emit(LanReceiverEvent.Paired(paired.senderName))
        return LanResponse.ok()
    }

    /** The TV user closed the code screen. */
    suspend fun cancelPairing() = mutex.withLock {
        if (session != null) endSession(failed = true, now = clock())
    }

    private fun expireSession(now: Long) {
        val s = session ?: return
        if (now >= s.expiresAt) endSession(failed = true, now = now)
    }

    private fun endSession(failed: Boolean, now: Long) {
        session = null
        _prompt.value = null
        if (!failed) return
        failedSessions.addLast(now)
        while (failedSessions.isNotEmpty() && now - failedSessions.first() > LanProtocol.PAIR_LOCK_WINDOW_MS) failedSessions.removeFirst()
        if (failedSessions.size >= LanProtocol.PAIR_LOCK_AFTER_FAILED_SESSIONS) {
            lockedUntil = now + LanProtocol.PAIR_LOCK_MS
            failedSessions.clear()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Signed commands
    // ---------------------------------------------------------------------------------------------

    private suspend fun signed(request: SignedRequest, size: Int): LanResponse {
        if (request.v != LanProtocol.VERSION) return LanResponse.error(LanErrorCode.UNSUPPORTED_VERSION)
        val key = store.keyFor(request.senderId) ?: return LanResponse.error(LanErrorCode.UNPAIRED)
        val now = clock()
        if (request.ts < now - LanProtocol.TIMESTAMP_WINDOW_MS || request.ts > now + LanProtocol.TIMESTAMP_WINDOW_MS) {
            return LanResponse.error(LanErrorCode.EXPIRED)
        }
        val expected = LanCrypto.unb64(LanCrypto.sign(key, request.senderId, request.ts, request.nonce, request.body))!!
        val given = LanCrypto.unb64(request.mac)
        if (given == null || !LanCrypto.constantTimeEquals(expected, given)) return LanResponse.error(LanErrorCode.BAD_SIGNATURE)
        // Only authentic requests reach the nonce cache, so nobody can fill it for another sender.
        mutex.withLock {
            nonces.entries.removeAll { now - it.value > LanProtocol.TIMESTAMP_WINDOW_MS * 2 }
            val nonceKey = request.senderId + "|" + request.nonce
            if (nonceKey in nonces) return LanResponse.error(LanErrorCode.REPLAY)
            nonces[nonceKey] = now
            while (nonces.size > MAX_NONCES) nonces.remove(nonces.keys.first())
        }
        val command = try {
            LanProtocol.bodyJson.decodeFromString(LanCommand.serializer(), request.body)
        } catch (_: SerializationException) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        } catch (_: IllegalArgumentException) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        }
        if (command !is PlaylistCommand && size > LanProtocol.MAX_REQUEST_BYTES) return LanResponse.error(LanErrorCode.TOO_LARGE)
        val senderName = store.nameOf(request.senderId) ?: ""
        return when (command) {
            PingCommand -> LanResponse.ok()
            is CastCommand -> {
                LanValidation.checkCast(command.stream)?.let { return LanResponse.error(it) }
                _events.emit(LanReceiverEvent.CastReceived(command.stream, senderName))
                LanResponse.ok()
            }
            is PlaylistCommand -> {
                if (!acceptingOffers) return LanResponse.error(LanErrorCode.NOT_ACCEPTING)
                LanValidation.checkPlaylist(command.playlist)?.let { return LanResponse.error(it) }
                _events.emit(LanReceiverEvent.PlaylistOffered(command.playlist, senderName, now))
                LanResponse.ok()
            }
        }
    }

    companion object {
        const val MAX_NONCES = 512
    }
}
