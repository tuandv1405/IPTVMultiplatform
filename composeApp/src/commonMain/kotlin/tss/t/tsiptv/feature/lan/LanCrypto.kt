package tss.t.tsiptv.feature.lan

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Ephemeral ECDH (P-256) for pairing. Implemented on the JVM (Android, desktop tests) with
 * `java.security`; platforms without it get [LanKeyAgreementFactory.Unsupported].
 */
interface LanKeyAgreement {
    /** X.509-encoded public key. */
    val publicKey: ByteArray

    /** The raw shared secret with [peerPublicKey]; throws on a malformed key. */
    fun agree(peerPublicKey: ByteArray): ByteArray
}

fun interface LanKeyAgreementFactory {
    /** null when this platform cannot pair (no ECDH). */
    fun create(): LanKeyAgreement?

    companion object {
        val Unsupported = LanKeyAgreementFactory { null }
    }
}

/** HMAC, hashing, random and base64 helpers for the LAN protocol. Pure common code (okio). */
@OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class)
object LanCrypto {

    fun hmac(key: ByteArray, message: String): ByteArray =
        message.encodeUtf8().hmacSha256(key.toByteString()).toByteArray()

    fun sha256(bytes: ByteArray): ByteArray = bytes.toByteString().sha256().toByteArray()

    fun b64(bytes: ByteArray): String = Base64.encode(bytes)

    /** null for anything that is not base64. */
    fun unb64(text: String): ByteArray? = try {
        Base64.decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Cryptographically secure random bytes (Uuid.random uses the platform's secure RNG). */
    fun randomBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val chunk = Uuid.random().toByteArray()
            // Version/variant nibbles are fixed in a v4 UUID; use the 15 fully random bytes
            // around them only (bytes 6 and 8 carry the fixed bits).
            for (i in chunk.indices) {
                if (i == 6 || i == 8) continue
                if (filled == count) break
                out[filled++] = chunk[i]
            }
        }
        return out
    }

    /** A random 6-digit code, "000000"–"999999", without modulo bias. */
    fun randomCode(): String {
        while (true) {
            val b = randomBytes(4)
            val value = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
                ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
            // 4_294_000_000 is the largest multiple of 1_000_000 below 2^32.
            if (value < 4_294_000_000L) return (value % 1_000_000L).toString().padStart(6, '0')
        }
    }

    fun newNonce(): String = b64(randomBytes(16))

    /** Constant-time comparison: the time taken does not depend on where the arrays differ. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    /** The pair key: HMAC(ECDH secret, label ‖ phonePub ‖ tvPub). Both sides compute the same. */
    fun pairKey(sharedSecret: ByteArray, senderPub: ByteArray, receiverPub: ByteArray): ByteArray =
        hmac(sharedSecret, "tsiptv-pair-v1|" + b64(senderPub) + "|" + b64(receiverPub))

    fun pairProof(pairKey: ByteArray, code: String): String = b64(hmac(pairKey, "confirm|$code"))

    /** The exact text a signed request's MAC covers. */
    fun signingInput(senderId: String, ts: Long, nonce: String, body: String): String =
        "tsiptv-v1\n$senderId\n$ts\n$nonce\n$body"

    fun sign(key: ByteArray, senderId: String, ts: Long, nonce: String, body: String): String =
        b64(hmac(key, signingInput(senderId, ts, nonce, body)))
}
