package tss.t.tsiptv

import tss.t.tsiptv.core.parser.EPGParserFactory
import tss.t.tsiptv.core.parser.IPTVParserFactory
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVPlaylist
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.strm.StrmParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The examples published under web/public/examples/ are what the format guides tell users to
 * try, and QC fixtures at the same time: each must parse with the app's own detection and
 * parsers, with the channel counts the guides describe.
 */
class WebExamplesParseTest {

    private val examples: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "web/public/examples")
            if (candidate.isDirectory) return@lazy candidate
            dir = dir.parentFile
        }
        error("web/public/examples not found")
    }

    private fun read(name: String) = File(examples, name).readText()

    private fun parse(name: String, expected: IPTVFormat): IPTVPlaylist {
        val content = read(name)
        assertEquals(expected, IPTVParserFactory.detectFormat(content), name)
        return IPTVParserFactory.createParser(expected).parse(content)
    }

    @Test
    fun m3u() {
        val playlist = parse("playlist.m3u", IPTVFormat.M3U)
        assertEquals(6, playlist.channels.size)
        assertTrue(playlist.skipped.isEmpty())
        assertEquals(listOf("https://tsiptv-8bdd6.web.app/examples/guide.xml"), playlist.epgUrls)
        val byId = playlist.channels.associateBy { it.id }
        assertEquals(listOf("News", "HD"), byId.getValue("ExampleNews.us").groups)
        assertEquals("https://example.com/", byId.getValue("ExampleNews.us").headers["Referer"])
        assertEquals("https://cdn.example.com/sports/index.m3u8", byId.getValue("ExampleSports.us").url)
        assertTrue(byId.getValue("ExampleRadio.us").isRadio)
        assertEquals(DrmSystem.WIDEVINE, byId.getValue("ExampleProtected.us").drm?.system)
        assertEquals(3, byId.getValue("ExampleArchive.us").catchup?.days)
    }

    @Test
    fun xspf() {
        assertEquals(2, parse("playlist.xspf", IPTVFormat.XSPF).channels.size)
    }

    @Test
    fun json() {
        val playlist = parse("playlist.json", IPTVFormat.JSON)
        assertEquals(3, playlist.channels.size)
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            playlist.channels.first { it.id == "ExampleNews.us" }.headers["User-Agent"]
        )
    }

    @Test
    fun iptvOrgStreams() {
        val playlist = parse("iptv-org-streams.json", IPTVFormat.JSON_IPTV_ORG)
        assertEquals(2, playlist.channels.size)
        assertEquals("https://example.com/", playlist.channels[1].headers["Referer"])
    }

    @Test
    fun strm() {
        val content = read("channel.strm")
        assertEquals(IPTVFormat.STRM, IPTVParserFactory.detectFormat(content))
        val channel = StrmParser("channel").parse(content).channels.single()
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", channel.url)
    }

    @Test
    fun guideWithElementsTheAppDoesNotRead() {
        val content = read("guide.xml")
        val programmes = EPGParserFactory.createParserForContent(content).parse(content)
        assertEquals(4, programmes.size)
        val magazine = programmes.first { it.title == "Weekly Magazine" }
        assertEquals("ExampleNews.us", magazine.channelId)
        assertEquals(listOf("Documentary"), magazine.category)
        assertEquals("A dated example programme with an episode number and a rating.", magazine.description)
    }
}
