package tss.t.tsiptv.core.stremio

import kotlinx.serialization.json.jsonObject
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamClassifierTest {

    private fun streams(file: String): List<StremioStream> {
        val root = StremioJson.parseToJsonElement(StremioFixtures.read(file)).jsonObject
        return root["streams"].decodeEach { StremioJson.decodeFromJsonElement(StremioStream.serializer(), it) }
    }

    @Test
    fun acceptanceS16MixedKindsShowTwoRowsByDefault() {
        val classified = StreamClassifier.classifyAll(streams("mixed-streams.json"))
        val visible = StreamClassifier.visible(classified)
        assertEquals(listOf("1080p", "Web"), visible.map { it.name })
        assertIs<StreamKind.Playable>(visible[0].kind)
        assertEquals("watch.example.org", assertIs<StreamKind.ExternalBrowser>(visible[1].kind).host)

        val all = StreamClassifier.visible(classified, showUnsupported = true)
        assertEquals(4, all.size)
        val unsupported = all.filterNot { it.isSelectable }
        assertEquals(listOf("P2P", "Trailer"), unsupported.map { it.stream.name })
        // No addon text that could act as a kind label on unsupported rows.
        assertEquals(listOf("", ""), unsupported.map { it.name })
        assertEquals(listOf(null, null), unsupported.map { it.description })
        assertEquals(listOf("infoHash", "ytId"), unsupported.map { (it.kind as StreamKind.Unsupported).rawKind })
    }

    @Test
    fun playableStreamCarriesHeadersAndHints() {
        val c = StreamClassifier.classifyAll(streams("mixed-streams.json"))[0]
        val kind = assertIs<StreamKind.Playable>(c.kind)
        assertEquals("https://media.example.org/film/1080.mp4", kind.url)
        assertEquals("Fixture/1.0", kind.headers["User-Agent"])
        assertEquals("Direct MP4\nline two", c.description)
        assertEquals("fixture-1080p", c.bingeGroup)
        assertTrue(c.isRegionLimited)
        assertTrue(c.mayNotPlayOnIos) // filename is .mkv
        assertNull(kind.mimeType)     // progressive: left to the player
    }

    @Test
    fun edgeCases() {
        val c = StreamClassifier.classifyAll(streams("edge-streams.json"))
        val byName = c.associateBy { it.stream.name.orEmpty() }
        assertEquals(12, c.size) // the string entry is dropped

        val hls = assertIs<StreamKind.Playable>(byName.getValue("HLS").kind)
        assertEquals(StreamMimeTypes.HLS, hls.mimeType)
        assertFalse(byName.getValue("HLS").mayNotPlayOnIos)
        val dash = assertIs<StreamKind.Playable>(byName.getValue("DASH").kind)
        assertEquals(StreamMimeTypes.DASH, dash.mimeType)
        assertTrue(byName.getValue("DASH").mayNotPlayOnIos)

        fun raw(name: String) = assertIs<StreamKind.Unsupported>(byName.getValue(name).kind).rawKind
        assertEquals("url:rtmp", raw("RTMP"))
        assertEquals("url:localServer", raw("Local server"))
        assertEquals("nzb", raw("Usenet"))
        assertEquals("archive", raw("Archive"))
        assertEquals("playerFrame", raw("Frame"))
        assertEquals("external:invalid", raw("Mailto"))
        assertEquals("unknown", raw("Nothing"))

        val internal = assertIs<StreamKind.Internal>(byName.getValue("Deep link").kind)
        assertEquals(StremioDeepLink.Detail("movie", "tt0000001", null, false), internal.link)
        assertTrue(byName.getValue("Deep link").isSelectable)

        // url wins over infoHash; an empty url falls through to externalUrl.
        assertIs<StreamKind.Playable>(byName.getValue("Both url and infoHash").kind)
        assertIs<StreamKind.ExternalBrowser>(byName.getValue("Empty url falls through").kind)

        assertEquals(
            listOf("HLS", "DASH", "Deep link", "Both url and infoHash", "Empty url falls through"),
            StreamClassifier.visible(c).map { it.name },
        )
    }

    @Test
    fun unroutableDeepLinkIsUnsupported() {
        val board = StreamClassifier.classify(StremioStream(name = "Board", externalUrl = "stremio:///board"))
        assertEquals(StreamKind.Unsupported("internal:unrouted"), board)
        val search = StreamClassifier.classify(StremioStream(externalUrl = "stremio:///search?search=his"))
        assertEquals(StremioDeepLink.Search("his"), assertIs<StreamKind.Internal>(search).link)
    }

    @Test
    fun toStringNeverContainsUrlsOrHeaderValues() {
        val stream = StremioStream(
            name = "n",
            url = "https://cdn.example.org/secret-token/a.m3u8",
            behaviorHints = StreamBehaviorHints(requestHeaders = mapOf("Cookie" to "session=secret-cookie")),
        )
        val c = StreamClassifier.classifyAll(listOf(stream)).single()
        val text = c.toString() + c.kind.toString()
        assertTrue("cdn.example.org" in text)
        assertFalse("secret-token" in text)
        assertFalse("secret-cookie" in text)
        val ext = StreamClassifier.classify(StremioStream(externalUrl = "https://watch.example.org/secret-path"))
        assertFalse("secret-path" in ext.toString())
        val discover = StremioDeepLink.parse("stremio:///discover/https%3A%2F%2Fa.example.org%2Fsecret-cfg%2Fmanifest.json/movie/top")!!
        assertFalse("secret-cfg" in discover.toString())

        // Internal, directly and through ClassifiedStream.
        val internalStream = StremioStream(
            name = "i",
            externalUrl = "stremio:///discover/https%3A%2F%2Fa.example.org%2Fsecret-cfg%2Fmanifest.json/movie/top",
        )
        val internal = StreamClassifier.classifyAll(listOf(internalStream)).single()
        assertIs<StreamKind.Internal>(internal.kind)
        assertFalse("secret-cfg" in internal.toString())
        assertFalse("secret-cfg" in internal.kind.toString())
        assertTrue("a.example.org" in internal.kind.toString())

        // Raw DTOs and responses.
        assertFalse("secret-token" in stream.toString())
        assertFalse("secret-cookie" in stream.toString())
        assertTrue("Cookie" in stream.toString())
        val sub = StremioSubtitle(id = "1", url = "https://subs.example.org/secret-sig/a.srt", lang = "eng")
        assertFalse("secret-sig" in sub.toString())
        val response = StremioHttpResponse(200, mapOf("set-cookie" to "secret-cookie"), """{"streams":[{"url":"https://x/secret-token"}]}""")
        assertFalse("secret" in response.toString())
    }

    @Test
    fun unsupportedRowsExposeNoAddonProvidedHints() {
        val torrent = StremioStream(
            name = "Torrent 1080p",
            description = "P2P",
            infoHash = "abc",
            behaviorHints = StreamBehaviorHints(bingeGroup = "p2p-1080p", countryWhitelist = listOf("usa")),
        )
        val row = StreamClassifier.classifyAll(listOf(torrent)).single()
        assertFalse(row.isSelectable)
        assertEquals("", row.name)
        assertNull(row.description)
        assertNull(row.bingeGroup)
        assertFalse(row.isRegionLimited)
        assertFalse(row.mayNotPlayOnIos)
    }

    @Test
    fun mimeTypeHints() {
        assertEquals(StreamMimeTypes.HLS, StreamClassifier.mimeTypeFor(null, "https://a/b/playlist.M3U8?x=1#f"))
        assertEquals(StreamMimeTypes.DASH, StreamClassifier.mimeTypeFor("movie.mpd", "https://a/b/play"))
        assertEquals(StreamMimeTypes.MPEG_TS, StreamClassifier.mimeTypeFor(null, "https://a/live.ts"))
        assertNull(StreamClassifier.mimeTypeFor(null, "https://a/movie.mp4"))
        assertNull(StreamClassifier.mimeTypeFor(null, "https://a/list.m3u"))
        assertNull(StreamClassifier.mimeTypeFor(null, "https://a.example.org/"))
        assertEquals(
            StreamMimeTypes.HLS,
            StreamClassifier.mimeTypeFor(null, "https://a/play", mapOf("content-type" to "application/vnd.apple.mpegurl; charset=utf-8")),
        )
    }

    @Test
    fun samplerTvStreamMapsToMediaItemWithHeaders() {
        val c = StreamClassifier.classifyAll(streams("sampler/stream__tv__tspd_test_hls.json")).single()
        val item = assertNotNull(c.toMediaItem(id = "stremio:org.tsiptv.publicdomain.static:tspd_test_hls", title = "HLS Test", artworkUri = "p"))
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", item.uri)
        assertEquals(mapOf("User-Agent" to "TSIPTV-Example/1.0"), item.headers)
        assertEquals(StreamMimeTypes.HLS, item.mimeType)
        assertEquals("HLS Test", item.title)
        assertEquals("p", item.artworkUri)

        val movie = StreamClassifier.classifyAll(streams("sampler/stream__movie__tspd_his_girl_friday.json")).single()
        assertEquals("MP4 · 512kb", movie.description)
        val web = StreamClassifier.classifyAll(streams("mixed-streams.json"))[3]
        assertNull(web.toMediaItem("x", "y"))
    }
}
