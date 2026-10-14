package tss.t.tsiptv.feature.clip

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.ui.screens.player.formatClipTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlsClipTest {

    @Test
    fun relativeUrisResolveAgainstThePlaylist() {
        val base = "https://cdn.example.com/live/ch1/index.m3u8?token=abc"
        assertEquals("https://cdn.example.com/live/ch1/seg1.ts", resolveUrl(base, "seg1.ts"))
        assertEquals("https://cdn.example.com/live/hd/seg1.ts", resolveUrl(base, "../hd/seg1.ts"))
        assertEquals("https://cdn.example.com/k/key", resolveUrl(base, "/k/key"))
        assertEquals("https://other.example.com/a.ts", resolveUrl(base, "//other.example.com/a.ts"))
        assertEquals("http://x.example.com/a.ts", resolveUrl(base, "http://x.example.com/a.ts"))
        assertEquals("https://cdn.example.com/live/ch1/index.m3u8?t=2", resolveUrl(base, "?t=2"))
    }

    @Test
    fun multivariantPlaylistListsVariantsAndAudio() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",URI="audio/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,AUDIO="aac"
            hd/index.m3u8
        """.trimIndent()
        val playlist = assertIs<HlsMultivariant>(HlsParser.parse(text, "https://h.example.com/master.m3u8"))
        assertEquals(2, playlist.variants.size)
        assertEquals(HlsVariant("https://h.example.com/low/index.m3u8", 800_000, 360, null), playlist.variants[0])
        assertEquals("aac", playlist.variants[1].audioGroup)
        assertEquals(HlsRendition("AUDIO", "aac", "https://h.example.com/audio/en.m3u8"), playlist.audios.single())
        // The 1080p variant needs the separate audio playlist: the muxed one is recorded.
        assertEquals(playlist.variants[0], HlsClipDownloader.chooseVariant(playlist, preferred = null))
    }

    @Test
    fun theBestVariantUpTo1080pIsRecorded() {
        val playlist = HlsMultivariant(
            variants = listOf(
                HlsVariant("a", 1_000_000, 480, null),
                HlsVariant("b", 4_000_000, 1080, null),
                HlsVariant("c", 12_000_000, 2160, null),
            ),
            audios = emptyList(),
        )
        assertEquals("b", HlsClipDownloader.chooseVariant(playlist, preferred = null)?.url)
        assertEquals("c", HlsClipDownloader.chooseVariant(playlist, preferred = "c")?.url)
        assertNull(
            HlsClipDownloader.chooseVariant(
                HlsMultivariant(listOf(HlsVariant("v", 1, 720, "g")), listOf(HlsRendition("AUDIO", "g", "a.m3u8"))),
                preferred = null,
            )
        )
    }

    @Test
    fun mediaPlaylistKeepsKeysRangesAndInitSections() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:1000@720
            media.mp4
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x1
            #EXTINF:5.5,
            #EXT-X-BYTERANGE:2000
            media.mp4
            #EXT-X-DISCONTINUITY
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4,
            next.mp4
            #EXT-X-ENDLIST
        """.trimIndent()
        val playlist = assertIs<HlsMediaPlaylist>(HlsParser.parse(text, "https://v.example.com/a/b.m3u8"))
        assertEquals(6_000, playlist.targetDurationMs)
        assertTrue(playlist.ended)
        val (first, second, third) = playlist.segments
        assertEquals(listOf(100L, 101L, 102L), playlist.segments.map { it.sequence })
        assertEquals(HlsInit("https://v.example.com/a/init.mp4", HlsByteRange(0, 720), key = null), first.init)
        assertEquals(HlsByteRange(720, 1000), first.range)
        // No offset: right after the previous range of the same file.
        assertEquals(HlsByteRange(1720, 2000), second.range)
        assertEquals(5_500, second.durationMs)
        assertEquals("https://v.example.com/a/key.bin", second.key?.url)
        assertContentEquals(ByteArray(15) + byteArrayOf(1), second.key?.iv)
        assertTrue(third.discontinuity)
        assertNull(third.key)
    }

    @Test
    fun notAPlaylist() {
        assertNull(HlsParser.parse("<html></html>", "https://x.example.com/"))
        assertEquals(ClipContainer.TS, ClipContainer.sniff(ByteArray(376) { if (it % 188 == 0) 0x47 else 0 }))
        assertEquals(ClipContainer.FMP4, ClipContainer.sniff(byteArrayOf(0, 0, 0, 24) + "ftypisom".encodeToByteArray()))
        assertEquals(ClipContainer.OTHER, ClipContainer.sniff("FLV\u0001".encodeToByteArray()))
    }

    @Test
    fun theIvDefaultsToTheMediaSequence() {
        assertContentEquals(ByteArray(14) + byteArrayOf(1, 2), HlsClipDownloader.sequenceIv(0x0102))
    }

    @Test
    fun vodClipStartsAtTheSegmentHoldingThePosition() = runTest {
        val http = FakeHttp()
        http.text("https://v.example.com/vod.m3u8", vod(count = 10, seconds = 4))
        repeat(10) { http.bytes("https://v.example.com/s$it.ts", ts(it)) }
        val sink = MemorySink()
        val session = ClipSession(lengthMs = 10_000)

        val recording = HlsClipDownloader(http, aes = null, headers = mapOf("Referer" to "r"))
            .record("https://v.example.com/vod.m3u8", live = false, fromMs = 9_000, session = session, sink = sink)

        // 9 s is in the third segment (8..12 s); 10 s asked for: three segments.
        assertEquals(listOf(2, 3, 4), sink.segments())
        assertEquals(12_000, recording.recordedMs)
        assertEquals(ClipContainer.TS, recording.container)
        assertEquals(10_000, session.recordedMs.value)
        assertTrue(http.requests.all { it.second == mapOf("Referer" to "r") }, "the stream's headers on every request")
    }

    @Test
    fun liveClipFollowsThePlaylistFromThePlayhead() = runTest {
        val http = FakeHttp()
        var newest = 5
        http.dynamic("https://l.example.com/live.m3u8") { live(first = newest - 3, last = newest, seconds = 2) }
        repeat(30) { http.bytes("https://l.example.com/s$it.ts", ts(it)) }
        val session = ClipSession(lengthMs = 8_000)
        val sink = MemorySink()
        // The playlist grows by a segment every 2 s.
        val grower = launch {
            while (true) {
                delay(2_000)
                newest++
            }
        }
        val recording = HlsClipDownloader(http, aes = null, headers = emptyMap()).record(
            "https://l.example.com/live.m3u8", live = true, fromMs = 0, session = session, sink = sink,
            start = HlsStart(variantUrl = null, sequence = 4),
        )
        grower.cancel()
        assertEquals(listOf(4, 5, 6, 7), sink.segments())
        assertEquals(8_000, recording.recordedMs)
    }

    @Test
    fun liveClipWithoutAPlayheadStartsAtTheNewestSegment() = runTest {
        val http = FakeHttp()
        http.text("https://l.example.com/live.m3u8", live(first = 10, last = 14, seconds = 2))
        repeat(20) { http.bytes("https://l.example.com/s$it.ts", ts(it)) }
        val session = ClipSession(lengthMs = 10_000)
        val sink = MemorySink()
        val job = launch {
            HlsClipDownloader(http, aes = null, headers = emptyMap())
                .record("https://l.example.com/live.m3u8", live = true, fromMs = 0, session = session, sink = sink)
        }
        delay(3_000)
        session.stop()
        job.join()
        assertEquals(listOf(14), sink.segments(), "stopped while waiting for the next segment")
    }

    @Test
    fun encryptedSegmentsAreDecryptedWithTheirKey() = runTest {
        val http = FakeHttp()
        http.text(
            "https://e.example.com/p.m3u8",
            """
                #EXTM3U
                #EXT-X-TARGETDURATION:4
                #EXT-X-MEDIA-SEQUENCE:7
                #EXT-X-KEY:METHOD=AES-128,URI="k"
                #EXTINF:4,
                s0.ts
                #EXTINF:4,
                s1.ts
                #EXT-X-ENDLIST
            """.trimIndent(),
        )
        http.bytes("https://e.example.com/k", ByteArray(16) { 9 })
        http.bytes("https://e.example.com/s0.ts", byteArrayOf(1))
        http.bytes("https://e.example.com/s1.ts", byteArrayOf(2))
        val seen = ArrayList<Pair<Byte, Byte>>()
        // XOR stands in for AES: the test checks the key, the IV and the order.
        val aes = Aes128Cbc { key, iv, data ->
            seen += key[0] to iv[15]
            ts(data[0].toInt())
        }
        val sink = MemorySink()
        HlsClipDownloader(http, aes, emptyMap())
            .record("https://e.example.com/p.m3u8", live = false, fromMs = 0, session = ClipSession(60_000), sink = sink)
        assertEquals(listOf(9.toByte() to 7.toByte(), 9.toByte() to 8.toByte()), seen, "IV from the media sequence")
        assertEquals(listOf(1, 2), sink.segments())
        assertEquals(1, http.requests.count { it.first == "https://e.example.com/k" }, "the key is fetched once")
    }

    @Test
    fun sampleAesIsNotRecorded() = runTest {
        val http = FakeHttp()
        http.text(
            "https://e.example.com/p.m3u8",
            "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\"\n#EXTINF:4,\ns0.ts\n#EXT-X-ENDLIST",
        )
        val failure = assertFailsWith<ClipException> {
            HlsClipDownloader(http, Aes128Cbc { _, _, d -> d }, emptyMap())
                .record("https://e.example.com/p.m3u8", live = false, fromMs = 0, session = ClipSession(10_000), sink = MemorySink())
        }
        assertEquals(ClipFailure.UNSUPPORTED, failure.failure)
    }

    @Test
    fun theClipEndsBeforeADiscontinuity() = runTest {
        val http = FakeHttp()
        http.text(
            "https://d.example.com/p.m3u8",
            "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\ns0.ts\n#EXTINF:4,\ns1.ts\n#EXT-X-DISCONTINUITY\n#EXTINF:4,\ns2.ts\n#EXT-X-ENDLIST",
        )
        repeat(3) { http.bytes("https://d.example.com/s$it.ts", ts(it)) }
        val sink = MemorySink()
        val recording = HlsClipDownloader(http, null, emptyMap())
            .record("https://d.example.com/p.m3u8", live = false, fromMs = 0, session = ClipSession(60_000), sink = sink)
        assertEquals(listOf(0, 1), sink.segments())
        assertEquals(8_000, recording.recordedMs)
    }

    @Test
    fun aStreamThatAnswersWithAPlaylistIsRecordedAsHls() = runTest {
        val http = FakeHttp()
        http.text("https://p.example.com/live/123", "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\na.ts")
        val result = recordLiveStream(http, "https://p.example.com/live/123", emptyMap(), ClipSession(10_000), MemorySink())
        assertEquals(StreamRecording.IsPlaylist, result)
    }

    @Test
    fun clipsOfProtectedOrRadioItemsAreRefused() {
        val item = MediaItem(id = "a", uri = "https://x.example.com/a.m3u8")
        assertNull(Clips.check(item))
        assertEquals(ClipFailure.PROTECTED, Clips.check(item.copy(drm = DrmSpec(system = DrmSystem.WIDEVINE))))
        assertEquals(ClipFailure.UNSUPPORTED, Clips.check(item.copy(isRadio = true)))
        assertTrue(looksLikeHls("https://x.example.com/a.M3U8?t=1", null))
        assertTrue(looksLikeHls("https://x.example.com/live/1", "application/x-mpegURL"))
        assertTrue(looksLikeDash("https://x.example.com/m.mpd", null))
    }

    @Test
    fun clipNamesAreSafeForFileSystems() {
        val name = Clips.fileName("VTV1: Thời sự / 19h?", 0L)
        assertTrue(name.startsWith("VTV1 Thời sự 19h "), name)
        assertTrue(name.endsWith(".mp4"))
        assertTrue(Clips.fileName("   ", 0L).startsWith("TS IPTV "))
        assertEquals("0:07", formatClipTime(7_400))
        assertEquals("1:00", formatClipTime(60_000))
        assertEquals("1:02:03", formatClipTime(3_723_000))
    }

    private fun vod(count: Int, seconds: Int) = buildString {
        append("#EXTM3U\n#EXT-X-TARGETDURATION:$seconds\n#EXT-X-MEDIA-SEQUENCE:0\n")
        repeat(count) { append("#EXTINF:$seconds,\ns$it.ts\n") }
        append("#EXT-X-ENDLIST\n")
    }

    private fun live(first: Int, last: Int, seconds: Int) = buildString {
        append("#EXTM3U\n#EXT-X-TARGETDURATION:$seconds\n#EXT-X-MEDIA-SEQUENCE:$first\n")
        for (i in first..last) append("#EXTINF:$seconds,\ns$i.ts\n")
    }

    /** A segment: TS sync bytes, its number in the second byte. */
    private fun ts(number: Int) = ByteArray(188) { if (it == 0) 0x47 else if (it == 1) number.toByte() else 0 }

    private class MemorySink : ClipSink {
        val chunks = ArrayList<ByteArray>()
        override suspend fun write(bytes: ByteArray, length: Int) {
            chunks += bytes.copyOf(length)
        }

        fun segments() = chunks.map { it[1].toInt() }
    }

    private class FakeHttp : ClipHttp {
        private val bodies = HashMap<String, () -> ByteArray>()
        val requests = ArrayList<Pair<String, Map<String, String>>>()

        fun text(url: String, text: String) = bytes(url, text.encodeToByteArray())
        fun bytes(url: String, bytes: ByteArray) {
            bodies[url] = { bytes }
        }

        fun dynamic(url: String, text: () -> String) {
            bodies[url] = { text().encodeToByteArray() }
        }

        override suspend fun fetch(url: String, headers: Map<String, String>, range: HlsByteRange?): ClipHttpBody {
            requests += url to headers
            val body = bodies[url] ?: throw ClipException(ClipFailure.NETWORK, "404")
            return ClipHttpBody(body(), url)
        }

        override suspend fun stream(url: String, headers: Map<String, String>, onChunk: suspend (ByteArray, Int) -> Boolean) {
            val body = fetch(url, headers).bytes
            onChunk(body, body.size)
        }
    }
}
