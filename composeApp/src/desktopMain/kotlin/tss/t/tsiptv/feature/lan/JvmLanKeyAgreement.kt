package tss.t.tsiptv.feature.lan

import javax.crypto.KeyAgreement
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Ephemeral ECDH P-256 (same as the Android one). Desktop does not send or receive yet (PRD §1); this
 * is here for the desktop sender follow-up and for the JVM tests of the pairing crypto.
 */
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
