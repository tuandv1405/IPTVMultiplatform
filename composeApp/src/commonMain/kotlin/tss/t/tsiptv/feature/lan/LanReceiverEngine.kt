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

    /** After the TV user cancelled (or ignored) a code screen, new pairings wait until then. */
    private var cooldownUntil = 0L

    /** Code screens that ran out in a row: each one doubles the next cooldown. */
    private var expiredInARow = 0

    /**
     * Pairing mode: true only while the TV shows its "TV & devices" screen. Outside it every
     * pair_start is refused with PAIRING_CLOSED, so no LAN client can put the full-screen code over
     * playback or Home. Paired phones are not affected (casts and offers are signed).
     */
    @kotlin.concurrent.Volatile
    var pairingOpen: Boolean = false
    private val nonces = LinkedHashMap<String, Long>()

    private val _prompt = MutableStateFlow<PairingPrompt?>(null)
    val pairingPrompt: StateFlow<PairingPrompt?> = _prompt

    private val _events = MutableSharedFlow<LanReceiverEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<LanReceiverEvent> = _events

    /**
     * Whether a playlist offer can be shown now. The TV host turns it off while it cannot take one
     * (an import running, the offer queue full); offers are then refused with NOT_ACCEPTING and the
     * phone says "TV is busy". Casts are not affected.
     */
    @kotlin.concurrent.Volatile
    var acceptingOffers: Boolean = true

    suspend fun handle(line: String): String = encode(handleRequest(line))

    /** Large-request grants per sender (time of each grant), for [allowsLargeRequest]'s rate limit. */
    private val largeGrants = HashMap<String, ArrayDeque<Long>>()

    /**
     * Whether a request line may be longer than [LanProtocol.MAX_REQUEST_BYTES]. [prefix] is the start
     * of the line as read so far (64 KiB). Protocol v1 puts the MAC after the body, so the header
     * cannot be authenticated before the body is read; the gate is therefore:
     * - the line starts with the exact envelope the apps write, `{"type":"signed","v":1,"senderId":
     *   "…","ts":…,"nonce":"…"` (no regex search anywhere in the body);
     * - that senderId is a phone paired with this TV;
     * - ts is within the request window (a captured old request cannot be reused for this);
     * - at most [LARGE_PER_SENDER] grants per sender per [LARGE_WINDOW_MS].
     * The full request, including the MAC, is still checked by [handle]. A header MAC is protocol v2.
     */
    suspend fun allowsLargeRequest(prefix: String): Boolean {
        val header = SIGNED_HEADER.find(prefix) ?: return false
        if (header.range.first != 0) return false
        val senderId = header.groupValues[1]
        val ts = header.groupValues[2].toLongOrNull() ?: return false
        val now = clock()
        if (ts < now - LanProtocol.TIMESTAMP_WINDOW_MS || ts > now + LanProtocol.TIMESTAMP_WINDOW_MS) return false
        if (store.nameOf(senderId) == null) return false
        return mutex.withLock {
            val grants = largeGrants.getOrPut(senderId) { ArrayDeque() }
            while (grants.isNotEmpty() && now - grants.first() >= LARGE_WINDOW_MS) grants.removeFirst()
            if (grants.size >= LARGE_PER_SENDER) return@withLock false
            grants.addLast(now)
            // Bounded: forget senders that have no grant in the window.
            if (largeGrants.size > PairingStore.MAX_PEERS * 2) largeGrants.entries.removeAll { it.value.isEmpty() }
            true
        }
    }

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
        val name = LanValidation.cleanName(request.senderName)
        if (request.senderId.isBlank() || request.senderId.length > 64 || name.isEmpty()) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        }
        val now = clock()
        expireSession(now)
        if (!pairingOpen) return LanResponse.error(LanErrorCode.PAIRING_CLOSED)
        if (now < lockedUntil) return LanResponse.error(LanErrorCode.LOCKED)
        // One code screen at a time, and none right after the TV user closed one: a LAN client cannot
        // keep a full-screen prompt over playback.
        if (session != null || now < cooldownUntil) return LanResponse.error(LanErrorCode.BUSY)
        val agreement = keyAgreement.create() ?: return LanResponse.error(LanErrorCode.NOT_ACCEPTING)
        val senderPub = LanCrypto.unb64(request.pub) ?: return LanResponse.error(LanErrorCode.BAD_REQUEST)
        val secret = try {
            agreement.agree(senderPub)
        } catch (_: Exception) {
            return LanResponse.error(LanErrorCode.BAD_REQUEST)
        }
        val key = LanCrypto.pairKey(secret, senderPub, agreement.publicKey)
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
            expiredInARow = 0
        }
        store.put(paired.senderId, paired.senderName, paired.key, clock())
        _events.emit(LanReceiverEvent.Paired(paired.senderName))
        return LanResponse.ok()
    }

    /** The TV user closed the code screen. */
    suspend fun cancelPairing() = mutex.withLock {
        if (session != null) {
            val now = clock()
            endSession(failed = true, now = now)
            cooldownUntil = now + PAIR_COOLDOWN_MS
        }
    }

    /** The receiver stopped (app paused): drop the code screen without a cooldown. */
    suspend fun abortPairing() = mutex.withLock {
        if (session != null) endSession(failed = true, now = clock())
    }

    private fun expireSession(now: Long) {
        val s = session ?: return
        if (now >= s.expiresAt) {
            endSession(failed = true, now = now)
            // Escalating: 30 s, 60 s, 2 min, ... up to 10 min while codes keep running out unused.
            val factor = 1L shl expiredInARow.coerceAtMost(5)
            cooldownUntil = now + minOf(PAIR_COOLDOWN_MS * factor, PAIR_MAX_COOLDOWN_MS)
            expiredInARow++
        }
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
                val shown = command.playlist.copy(name = LanValidation.cleanName(command.playlist.name, LanValidation.MAX_NAME))
                if (shown.name.isEmpty()) return LanResponse.error(LanErrorCode.BAD_REQUEST)
                _events.emit(LanReceiverEvent.PlaylistOffered(shown, senderName, now))
                LanResponse.ok()
            }
        }
    }

    companion object {
        const val MAX_NONCES = 512
        const val PAIR_COOLDOWN_MS = 30_000L
        const val PAIR_MAX_COOLDOWN_MS = 10 * 60_000L

        const val LARGE_PER_SENDER = 3
        const val LARGE_WINDOW_MS = 60_000L

        /** The exact start of a v1 signed request as the apps encode it (field order is fixed). */
        private val SIGNED_HEADER = Regex(
            """^\{"type":"signed","v":1,"senderId":"([A-Za-z0-9._:-]{1,64})","ts":(-?\d{1,19}),"nonce":"[A-Za-z0-9+/=_-]{1,64}","""
        )
    }
}
