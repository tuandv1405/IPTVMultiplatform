package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.stremio.FakeCipher
import tss.t.tsiptv.feature.account.LocalDevice
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Pairing with the real JCA ECDH used on Android (AC-A3). */
class JvmLanPairingTest {

    @Test
    fun bothSidesDeriveTheSameSecret() {
        val a = JvmLanKeyAgreement()
        val b = JvmLanKeyAgreement()
        assertContentEquals(a.agree(b.publicKey), b.agree(a.publicKey))
    }

    @Test
    fun otherCurvesAndGarbageAreRejected() {
        val p384 = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair()
        assertFailsWith<IllegalArgumentException> { JvmLanKeyAgreement().agree(p384.public.encoded) }
        assertFailsWith<Exception> { JvmLanKeyAgreement().agree(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun pairAndCastOverTheEngine() = runBlocking<Unit> {
        val factory = LanKeyAgreementFactory { JvmLanKeyAgreement() }
        val tvStore = PairingStore(InMemoryKeyValueStorage(), FakeCipher(), PairingStore.Role.RECEIVER)
        val engine = LanReceiverEngine({ "tv" to "TV" }, tvStore, factory, { 5_000L }, { "654321" }).apply { pairingOpen = true }
        val transport = object : LanTransport {
            override suspend fun exchange(host: String, port: Int, line: String, maxAnswerBytes: Int) = engine.handle(line)
        }
        val phone = LanSender(
            transport,
            PairingStore(InMemoryKeyValueStorage(), FakeCipher(), PairingStore.Role.SENDER),
            LocalDevice(InMemoryKeyValueStorage(), { "Phone" }),
            factory,
        ) { 5_000L }
        val tv = LanDevice("tv", "TV", "10.0.0.2", 1)
        val started = phone.startPairing(tv)
        assertIs<PairStartResult.Started>(started)
        assertEquals(LanResult.Refused(LanErrorCode.WRONG_CODE), phone.confirmPairing(started.handle, "123456"))
        assertEquals(LanResult.Ok, phone.confirmPairing(started.handle, "654321"))
        assertEquals(LanResult.Ok, phone.send(tv, CastCommand(CastStream(url = "https://a.example/x.m3u8"))))
    }
}
