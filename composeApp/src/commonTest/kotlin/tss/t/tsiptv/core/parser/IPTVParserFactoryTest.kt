package tss.t.tsiptv.core.parser

import okio.Buffer
import okio.GzipSink
import okio.buffer
import tss.t.tsiptv.TestAssets
import tss.t.tsiptv.core.parser.model.IPTVFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/** Format detection table of docs/prd-kodi-m3u-compat.md §1. */
class IPTVParserFactoryTest {

    private fun detect(file: String) = IPTVParserFactory.detectFormat(TestAssets.read("kodi/$file"))

    @Test
    fun htmlPage() {
        assertEquals(IPTVFormat.HTML, detect("page.html"))
        assertEquals(IPTVFormat.HTML, IPTVParserFactory.detectFormat("<HTML><body></body></HTML>"))
    }

    @Test
    fun hlsManifestIsNotAChannelList() {
        assertEquals(IPTVFormat.HLS_MANIFEST, detect("hls-master.m3u8"))
        assertEquals(
            IPTVFormat.HLS_MANIFEST,
            IPTVParserFactory.detectFormat("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg0.ts")
        )
    }

    @Test
    fun m3uVariants() {
        assertEquals(IPTVFormat.M3U, detect("reference.m3u"))
        assertEquals(IPTVFormat.M3U, IPTVParserFactory.detectFormat("﻿#EXTM3U\n#EXTINF:-1,A\nhttp://a"))
        assertEquals(IPTVFormat.M3U, IPTVParserFactory.detectFormat("  \n#EXTINF:-1,A\nhttp://a"))
    }

    // AC-K23: arrays used to be sent to the M3U parser.
    @Test
    fun jsonArrayAndIptvOrg() {
        assertEquals(IPTVFormat.JSON, detect("array.json"))
        assertEquals(IPTVFormat.JSON_IPTV_ORG, detect("iptv-org-streams.json"))
        assertEquals(IPTVFormat.JSON, IPTVParserFactory.detectFormat("{\"channels\":[]}"))
    }

    @Test
    fun tsiptvSourceIsRecognised() {
        assertEquals(
            IPTVFormat.TSIPTV_SOURCE,
            IPTVParserFactory.detectFormat("{\n  \"format\" : \"tsiptv-source\",\n  \"version\": 1\n}")
        )
    }

    @Test
    fun strmAndPlainLists() {
        assertEquals(IPTVFormat.STRM, detect("channel.strm"))
        assertEquals(IPTVFormat.STRM, detect("addon.strm"))
        assertEquals(IPTVFormat.M3U_PLAIN, detect("plain-list.txt"))
    }

    @Test
    fun xmlFormatsKeepTheirRules() {
        assertEquals(IPTVFormat.XML, IPTVParserFactory.detectFormat("<tv><channel id=\"a\"/></tv>"))
        assertEquals(
            IPTVFormat.XSPF,
            IPTVParserFactory.detectFormat("<?xml version=\"1.0\"?><playlist xmlns=\"http://xspf.org/ns/0/\"></playlist>")
        )
        assertEquals(
            IPTVFormat.XSPF,
            IPTVParserFactory.detectFormat("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\"></playlist>")
        )
        assertEquals(IPTVFormat.UNKNOWN, IPTVParserFactory.detectFormat("<playlist><entry/></playlist>"))
    }

    @Test
    fun anythingElseIsUnknown() {
        assertEquals(IPTVFormat.UNKNOWN, IPTVParserFactory.detectFormat("Hello, this is not a playlist."))
        assertEquals(IPTVFormat.UNKNOWN, IPTVParserFactory.detectFormat(""))
        assertEquals(IPTVFormat.UNKNOWN, IPTVParserFactory.detectFormat("https://cdn.example.com/a.m3u8\nnot a url"))
    }

    // AC-K24: gzip and BOM.
    @Test
    fun decodeGunzipsAndDropsTheByteOrderMark() {
        val text = "﻿#EXTM3U\n#EXTINF:-1,A\nhttps://cdn.example.com/a.m3u8\n"
        val gz = Buffer().also { sink -> GzipSink(sink).buffer().use { it.writeUtf8(text) } }.readByteArray()
        assertEquals(text.removePrefix("﻿"), IPTVParserFactory.decode(gz))
        assertEquals(text.removePrefix("﻿"), IPTVParserFactory.decode(text.encodeToByteArray()))
    }
}
