package tss.t.tsiptv

import tss.t.tsiptv.core.parser.IPTVParserFactory
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.model.IPTVFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The device-QC fixtures in qa/kodi/ (docs/handoff-f1.md). Not published: they carry a
 * `plugin://` entry and placeholder hosts on purpose. Checked here so QC never starts from
 * a fixture that no longer means what the hand-off says.
 */
class QaKodiFixturesTest {

    private val dir: File by lazy {
        var d: File? = File(".").absoluteFile
        while (d != null) {
            val candidate = File(d, "qa/kodi")
            if (candidate.isDirectory) return@lazy candidate
            d = d.parentFile
        }
        error("qa/kodi not found")
    }

    private fun read(name: String) = File(dir, name).readText()

    @Test
    fun qcPlaylistHasNineChannelsAndOneSkipPerReason() {
        val content = read("qc-playback.m3u")
        assertEquals(IPTVFormat.M3U, IPTVParserFactory.detectFormat(content))
        val playlist = M3UParser().parse(content)
        assertEquals(9, playlist.channels.size)
        assertEquals(3, playlist.skipped.map { it.reason }.toSet().size)
        assertEquals("TSIPTV-QC/1.0", playlist.channels.first().headers["User-Agent"])
        assertNull(playlist.channels.first { it.id == "QcDrmWord.example" }.drm)
    }

    @Test
    fun otherFixturesAreDetected() {
        assertEquals(IPTVFormat.STRM, IPTVParserFactory.detectFormat(read("qc-channel.strm")))
        assertEquals(IPTVFormat.STRM, IPTVParserFactory.detectFormat(read("qc-addon.strm")))
        assertEquals(IPTVFormat.JSON, IPTVParserFactory.detectFormat(read("qc-array.json")))
    }
}
