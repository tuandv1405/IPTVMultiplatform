package tss.t.tsiptv.core.parser

import tss.t.tsiptv.TestAssets
import tss.t.tsiptv.core.parser.model.exception.IPTVParserException
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import tss.t.tsiptv.core.parser.strm.StrmKodiAddonException
import tss.t.tsiptv.core.parser.strm.StrmParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `.strm` (AC-K26, parser part). */
class StrmParserTest {

    @Test
    fun strmBecomesOneChannelNamedAfterTheFile() {
        val playlist = StrmParser("channel").parse(TestAssets.read("kodi/channel.strm"))
        val channel = playlist.channels.single()
        assertEquals("channel", playlist.name)
        assertEquals("channel", channel.name)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", channel.url)
        assertEquals(mapOf("User-Agent" to "TSIPTV-QC/1.0"), channel.headers)
        assertEquals(StreamMimeTypes.HLS, channel.mimeType)
        assertEquals("inputstream.adaptive", channel.attributes["kodiprop:inputstream"])
    }

    @Test
    fun pluginUrlIsRefused() {
        assertFailsWith<StrmKodiAddonException> {
            StrmParser("addon").parse(TestAssets.read("kodi/addon.strm"))
        }
    }

    @Test
    fun moreThanOneUrlIsNotAStrm() {
        assertFailsWith<IPTVParserException> {
            StrmParser("two").parse("https://cdn.example.com/a.m3u8\nhttps://cdn.example.com/b.m3u8")
        }
    }
}
