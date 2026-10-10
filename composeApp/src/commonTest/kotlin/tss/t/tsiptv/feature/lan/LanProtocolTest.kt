package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.stremio.FakeCipher
import tss.t.tsiptv.feature.account.LocalDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Protocol v1 end to end without sockets: a [LanSender] talking to a [LanReceiverEngine] through an
 * in-memory transport (AC-A3, AC-A5, AC-A6, AC-B2).
 */
class LanProtocolTest {

    /** Symmetric stand-in for ECDH (protocol logic only; the real one is tested on the JVM). */
    private class FakeAgreement : LanKeyAgreement {
        override val publicKey: ByteArray = LanCrypto.randomBytes(32)
        override fun agree(peerPublicKey: ByteArray): ByteArray {
            val (a, b) = listOf(publicKey, peerPublicKey).sortedBy { LanCrypto.b64(it) }
            return LanCrypto.sha256(a + b)
        }
    }

    private class Harness(var now: Long = 1_000_000L, code: String = "123456") {
        val factory = LanKeyAgreementFactory { FakeAgreement() }
        val tvStore = PairingStore(InMemoryKeyValueStorage(), FakeCipher(), PairingStore.Role.RECEIVER)
        val phoneStore = PairingStore(InMemoryKeyValueStorage(), FakeCipher(), PairingStore.Role.SENDER)
        val engine = LanReceiverEngine({ "tv-1" to "Living room" }, tvStore, factory, { now }, { code })
        var lastRequest: String? = null
        val transport = object : LanTransport {
            override suspend fun exchange(host: String, port: Int, line: String, maxAnswerBytes: Int): String {
                lastRequest = line
                return engine.handle(line)
            }
        }
        val phone = LanSender(transport, phoneStore, LocalDevice(InMemoryKeyValueStorage(), { "Pixel" }), factory) { now }
        val tv = LanDevice(id = "tv-1", name = "Living room", host = "192.168.1.20", port = 4000)
    }

    private val stream = CastStream(
        url = "https://cdn.example/live.m3u8",
        title = "News",
        headers = mapOf("User-Agent" to "TS", "Referer" to "https://example/"),
        drm = DrmSpec(DrmSystem.WIDEVINE, licenseUrl = "https://lic.example/wv"),
        isLive = false,
        positionMs = 61_000,
    )

    private suspend fun Harness.pair(code: String = "123456"): LanResult {
        val started = phone.startPairing(tv)
        assertIs<PairStartResult.Started>(started)
        assertEquals("123456", engine.pairingPrompt.value?.code)
        assertEquals("Pixel", engine.pairingPrompt.value?.senderName)
        return phone.confirmPairing(started.handle, code)
    }

    @Test
    fun helloIdentifiesTheTv() = runBlocking<Unit> {
        val h = Harness()
        val device = h.phone.hello("192.168.1.20", 4000)
        assertEquals("tv-1", device?.id)
        assertEquals("Living room", device?.name)
    }

    @Test
    fun pairThenCastDeliversTheStreamWithHeadersAndPosition() = runBlocking<Unit> {
        val h = Harness()
        assertEquals(LanResult.Unpaired, h.phone.send(h.tv, CastCommand(stream)))
        assertEquals(LanResult.Ok, h.pair())
        assertNull(h.engine.pairingPrompt.value)
        assertTrue(h.phone.isPaired(h.tv))
        assertEquals(listOf("Pixel"), h.tvStore.load().map { it.name })

        val event = async(start = CoroutineStart.UNDISPATCHED) { h.engine.events.first { it is LanReceiverEvent.CastReceived } }
        assertEquals(LanResult.Ok, h.phone.send(h.tv, CastCommand(stream)))
        val received = event.await() as LanReceiverEvent.CastReceived
        assertEquals(stream, received.stream)
        assertEquals("Pixel", received.senderName)
        // The request line itself never shows the code.
        assertFalse("123456" in h.lastRequest!!)
    }

    @Test
    fun wrongCodeThreeTimesEndsTheSession() = runBlocking<Unit> {
        val h = Harness()
        val started = h.phone.startPairing(h.tv) as PairStartResult.Started
        repeat(2) { assertEquals(LanResult.Refused(LanErrorCode.WRONG_CODE), h.phone.confirmPairing(started.handle, "000000")) }
        assertNotNull(h.engine.pairingPrompt.value)
        assertEquals(LanResult.Refused(LanErrorCode.WRONG_CODE), h.phone.confirmPairing(started.handle, "000001"))
        assertNull(h.engine.pairingPrompt.value)
        // Even the right code is now refused: the session is gone.
        assertEquals(LanResult.Refused(LanErrorCode.EXPIRED), h.phone.confirmPairing(started.handle, "123456"))
        assertFalse(h.phone.isPaired(h.tv))
    }

    @Test
    fun secondPairingWhileOneIsOpenIsBusyAndCodesExpire() = runBlocking<Unit> {
        val h = Harness()
        val first = h.phone.startPairing(h.tv) as PairStartResult.Started
        assertEquals(PairStartResult.Failed(LanResult.Refused(LanErrorCode.BUSY)), h.phone.startPairing(h.tv))
        h.now += LanProtocol.PAIR_CODE_TTL_MS
        assertEquals(LanResult.Refused(LanErrorCode.EXPIRED), h.phone.confirmPairing(first.handle, "123456"))
    }

    @Test
    fun failedSessionsLockPairing() = runBlocking<Unit> {
        val h = Harness()
        repeat(LanProtocol.PAIR_LOCK_AFTER_FAILED_SESSIONS) {
            h.phone.startPairing(h.tv)
            h.engine.cancelPairing()
            h.now += LanReceiverEngine.PAIR_COOLDOWN_MS
        }
        assertEquals(PairStartResult.Failed(LanResult.Refused(LanErrorCode.LOCKED)), h.phone.startPairing(h.tv))
        h.now += LanProtocol.PAIR_LOCK_MS
        assertIs<PairStartResult.Started>(h.phone.startPairing(h.tv))
    }

    @Test
    fun aCancelledCodeScreenCannotBeReopenedAtOnce() = runBlocking<Unit> {
        val h = Harness()
        assertIs<PairStartResult.Started>(h.phone.startPairing(h.tv))
        h.engine.cancelPairing()
        // QC: a LAN client must not keep popping the full-screen code over playback.
        assertEquals(PairStartResult.Failed(LanResult.Refused(LanErrorCode.BUSY)), h.phone.startPairing(h.tv))
        h.now += LanReceiverEngine.PAIR_COOLDOWN_MS - 1
        assertEquals(PairStartResult.Failed(LanResult.Refused(LanErrorCode.BUSY)), h.phone.startPairing(h.tv))
        h.now += 1
        assertIs<PairStartResult.Started>(h.phone.startPairing(h.tv))
        // Stopping the receiver (app paused) drops the screen without a cooldown.
        h.engine.abortPairing()
        assertIs<PairStartResult.Started>(h.phone.startPairing(h.tv))
    }

    @Test
    fun senderNamesAreCleaned() = runBlocking<Unit> {
        val h = Harness()
        val evil = "Living room\nremote \u202Eevil\u200B" + "x".repeat(100)
        val line = encode(PairStartRequest(senderId = "atk", senderName = evil, pub = LanCrypto.b64(FakeAgreement().publicKey)))
        assertEquals(true, response(h, line).ok)
        val shown = h.engine.pairingPrompt.value!!.senderName
        assertEquals(LanValidation.MAX_DEVICE_NAME, shown.length)
        assertTrue(shown.startsWith("Living room remote evil"))
        assertTrue(shown.none { it == '\n' || it == '\u202E' || it == '\u200B' })
        h.engine.abortPairing()
        // Nothing printable left: refused.
        val blank = encode(PairStartRequest(senderId = "atk2", senderName = "\u202E\n\t", pub = LanCrypto.b64(FakeAgreement().publicKey)))
        assertEquals(LanErrorCode.BAD_REQUEST, response(h, blank).code)
    }

    @Test
    fun onlyPairedSendersMayExceedTheRequestCap() = runBlocking<Unit> {
        val h = Harness()
        val prefix = { id: String -> "{\"type\":\"signed\",\"v\":1,\"senderId\":\"$id\",\"ts\":1,\"nonce\":\"n\",\"body\":\"" }
        assertFalse(h.engine.allowsLargeRequest(prefix("pixel")))
        assertEquals(LanResult.Ok, h.pair())
        val phoneId = h.tvStore.load().single().id
        assertTrue(h.engine.allowsLargeRequest(prefix(phoneId)))
        assertFalse(h.engine.allowsLargeRequest(prefix(phoneId).replace("signed", "hello")))
        assertFalse(h.engine.allowsLargeRequest("{\"type\":\"pair_start\",\"senderId\":\"$phoneId\""))
        assertFalse(h.engine.allowsLargeRequest("x".repeat(1000)))
    }

    @Test
    fun replayedTamperedAndStaleRequestsAreRefused() = runBlocking<Unit> {
        val h = Harness()
        h.pair()
        assertEquals(LanResult.Ok, h.phone.send(h.tv, PingCommand))
        val captured = h.lastRequest!!
        // Replay of the exact same line.
        assertEquals(LanErrorCode.REPLAY, response(h, captured).code)
        // Tampered body (MAC no longer matches).
        val signed = LanProtocol.json.decodeFromString(LanRequest.serializer(), captured) as SignedRequest
        val tampered = signed.copy(nonce = LanCrypto.newNonce(), body = LanProtocol.bodyJson.encodeToString(LanCommand.serializer(), CastCommand(stream)))
        assertEquals(LanErrorCode.BAD_SIGNATURE, response(h, encode(tampered)).code)
        // Unknown sender.
        assertEquals(LanErrorCode.UNPAIRED, response(h, encode(signed.copy(senderId = "stranger"))).code)
        // A phone clock 3 minutes behind: a valid MAC, a fresh nonce, but outside the window.
        val key = h.tvStore.keyFor(signed.senderId)!!
        val ts = h.now - 3 * 60_000
        val nonce = LanCrypto.newNonce()
        val stale = signed.copy(ts = ts, nonce = nonce, mac = LanCrypto.sign(key, signed.senderId, ts, nonce, signed.body))
        assertEquals(LanErrorCode.EXPIRED, response(h, encode(stale)).code)
        // The same request signed for "now" is accepted.
        val fresh = signed.copy(ts = h.now, nonce = nonce, mac = LanCrypto.sign(key, signed.senderId, h.now, nonce, signed.body))
        assertTrue(response(h, encode(fresh)).ok)
    }
    @Test
    fun unpairOnTheTvMakesThePhonePairAgain() = runBlocking<Unit> {
        val h = Harness()
        h.pair()
        h.tvStore.load().forEach { h.tvStore.remove(it.id) }
        assertEquals(LanResult.Unpaired, h.phone.send(h.tv, PingCommand))
        assertFalse(h.phone.isPaired(h.tv))
    }

    @Test
    fun badInputIsRefused() = runBlocking<Unit> {
        val h = Harness()
        assertEquals(LanErrorCode.BAD_REQUEST, response(h, "not json").code)
        assertEquals(LanErrorCode.BAD_REQUEST, response(h, """{"type":"nope"}""").code)
        assertEquals(LanErrorCode.TOO_LARGE, response(h, """{"type":"hello","pad":"${"x".repeat(LanProtocol.MAX_REQUEST_BYTES)}"}""").code)
        h.pair()
        assertEquals(LanResult.Refused(LanErrorCode.BAD_URL), h.phone.send(h.tv, CastCommand(stream.copy(url = "file:///sdcard/x"))))
        assertEquals(LanResult.Refused(LanErrorCode.BAD_URL), h.phone.send(h.tv, CastCommand(stream.copy(drm = DrmSpec(DrmSystem.WIDEVINE, licenseUrl = "ftp://x")))))
        assertEquals(LanResult.Refused(LanErrorCode.BAD_REQUEST), h.phone.send(h.tv, CastCommand(stream.copy(headers = mapOf("X" to "a\r\nInjected: 1")))))
    }

    @Test
    fun playlistOfferWithUrlOrFile() = runBlocking<Unit> {
        val h = Harness()
        h.pair()
        val offer = async(start = CoroutineStart.UNDISPATCHED) { h.engine.events.first { it is LanReceiverEvent.PlaylistOffered } }
        val shared = SharedPlaylist(name = "VN", url = "https://lists.example/vn.m3u", epgUrls = listOf("https://epg.example/a.xml"))
        assertEquals(LanResult.Ok, h.phone.send(h.tv, PlaylistCommand(shared)))
        assertEquals(shared, (offer.await() as LanReceiverEvent.PlaylistOffered).playlist)

        val file = SharedPlaylist(name = "Mine", fileName = "mine.m3u", content = "#EXTM3U\n#EXTINF:-1,A\nhttp://a/1\n")
        assertEquals(LanResult.Ok, h.phone.send(h.tv, PlaylistCommand(file)))
        // Above the file cap the phone refuses before sending anything.
        val huge = file.copy(content = "#".repeat(LanProtocol.MAX_FILE_CONTENT_BYTES + 1))
        assertEquals(LanResult.Refused(LanErrorCode.TOO_LARGE), h.phone.send(h.tv, PlaylistCommand(huge.copy(content = huge.content + "x".repeat(LanProtocol.MAX_OFFER_BYTES)))))
        assertEquals(LanResult.Refused(LanErrorCode.TOO_LARGE), h.phone.send(h.tv, PlaylistCommand(huge)))
        assertEquals(LanResult.Refused(LanErrorCode.BAD_REQUEST), h.phone.send(h.tv, PlaylistCommand(shared.copy(content = "x", fileName = "a.m3u"))))

        h.engine.acceptingOffers = false
        assertEquals(LanResult.Refused(LanErrorCode.NOT_ACCEPTING), h.phone.send(h.tv, PlaylistCommand(shared)))
        yield()
    }

    private suspend fun response(h: Harness, line: String): LanResponse =
        LanProtocol.json.decodeFromString(LanResponse.serializer(), h.engine.handle(line))

    private fun encode(request: LanRequest) = LanProtocol.json.encodeToString(LanRequest.serializer(), request)
}

class LanValidationTest {
    @Test
    fun urls() {
        assertTrue(LanValidation.isHttpUrl("https://a.example/x.m3u?token=1"))
        assertTrue(LanValidation.isHttpUrl("http://user:pass@10.0.0.2:8080/x"))
        assertFalse(LanValidation.isHttpUrl("rtmp://a/x"))
        assertFalse(LanValidation.isHttpUrl("javascript:alert(1)"))
        assertFalse(LanValidation.isHttpUrl("https:///nohost"))
        assertFalse(LanValidation.isHttpUrl("https://a.example/x y"))
        assertFalse(LanValidation.isHttpUrl("https://a/" + "x".repeat(LanValidation.MAX_URL_LENGTH)))
        assertFalse(LanValidation.isHttpUrl(null))
    }

    @Test
    fun headers() {
        assertTrue(LanValidation.headersOk(mapOf("User-Agent" to "VLC/3", "X-Token" to "abc")))
        assertFalse(LanValidation.headersOk(mapOf("Bad Name" to "x")))
        assertFalse(LanValidation.headersOk(mapOf("X" to "a\nb")))
        assertFalse(LanValidation.headersOk((0..LanValidation.MAX_HEADERS).associate { "H$it" to "v" }))
    }

    @Test
    fun hostPortAndNetworkId() {
        assertEquals("192.168.1.20" to 47123, LanValidation.parseHostPort(" 192.168.1.20:47123 "))
        assertNull(LanValidation.parseHostPort("192.168.1.20"))
        assertNull(LanValidation.parseHostPort("192.168.1.20:70000"))
        assertNull(LanValidation.parseHostPort("a b:80"))
        val id = LanValidation.networkId("192.168.1.20")
        assertEquals(id, LanValidation.networkId("192.168.1.99"))
        assertTrue(id != LanValidation.networkId("192.168.2.20"))
        assertEquals(12, id!!.length)
        assertNull(LanValidation.networkId("fe80::1"))
    }

    @Test
    fun codesAndConstantTimeCompare() {
        repeat(50) { assertTrue(Regex("^\\d{6}$").matches(LanCrypto.randomCode())) }
        assertTrue(LanCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(LanCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 3)))
        assertFalse(LanCrypto.constantTimeEquals(byteArrayOf(1), byteArrayOf(1, 2)))
        assertEquals(32, LanCrypto.randomBytes(32).size)
        // Known HMAC-SHA256 vector (RFC 4231 case 2).
        val mac = LanCrypto.hmac("Jefe".encodeToByteArray(), "what do ya want for nothing?")
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", mac.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
    }

    @Test
    fun namesAreCleaned() {
        assertEquals("Phone evil line2", LanValidation.cleanName("Phone \u202Eevil\nline2"))
        assertEquals("a b", LanValidation.cleanName("  a \t\r\n b  "))
        assertEquals("", LanValidation.cleanName("\u200B\u2066\u0000"))
        assertEquals(40, LanValidation.cleanName("y".repeat(200)).length)
        // A surrogate pair is never split at the cap.
        val emoji = "\uD83D\uDCFA"
        assertEquals("x".repeat(39), LanValidation.cleanName("x".repeat(39) + emoji))
    }

    @Test
    fun namesFromLinksDropTokensAndCredentials() {
        assertEquals("tv.m3u", LanValidation.nameFromUrl("https://user:secret@host.example:8080/list/tv.m3u?token=abc#x"))
        assertEquals("host.example", LanValidation.nameFromUrl("http://user:secret@host.example/?username=a&password=b"))
        assertEquals("get.php", LanValidation.nameFromUrl("http://host.example:8080/get.php?username=a&password=b&type=m3u"))
        assertEquals("my list.m3u", LanValidation.nameFromUrl("https://h.example/a/my%20list.m3u/"))
        assertFalse("secret" in LanValidation.nameFromUrl("https://u:secret@h.example"))
    }

    @Test
    fun toStringNeverShowsSecrets() {
        val s = CastStream(url = "https://secret.example/tok123", headers = mapOf("Authorization" to "Bearer abc"))
        assertFalse("tok123" in s.toString())
        assertFalse("Bearer" in s.toString())
        assertFalse("secret" in SharedPlaylist(name = "n", url = "https://secret.example").toString())
        assertFalse("777" in PairingPrompt("777777", "p", 0).toString())
    }
}
