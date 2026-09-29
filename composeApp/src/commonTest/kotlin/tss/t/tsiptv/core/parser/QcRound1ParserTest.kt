package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.iptv.m3u.HeaderSuffixParser
import tss.t.tsiptv.core.parser.iptv.m3u.KodiDrmParser
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.playback.ClearKey
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regressions found in F1 QC round 1 (docs/handoff-f1.md, "QC round 1 fixes"). */
class QcRound1ParserTest {

    private val keys = listOf(ClearKey("000102030405060708090a0b0c0d0e0f", "00112233445566778899aabbccddeeff"))

    // Item 9
    @Test
    fun licenseKeyFieldOneMustBeAUrl() {
        val spec = KodiDrmParser.parse(
            mapOf(
                KodiDrmParser.PROP_LICENSE_TYPE to "com.widevine.alpha",
                KodiDrmParser.PROP_LICENSE_KEY to "000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff",
            )
        )
        assertNotNull(spec?.unsupportedReason)
    }

    @Test
    fun clearKeyWithoutLicenseKeyIsUnsupported() {
        val spec = KodiDrmParser.parse(mapOf(KodiDrmParser.PROP_LICENSE_TYPE to "clearkey"))
        assertEquals(DrmSystem.CLEARKEY, spec?.system)
        assertNotNull(spec?.unsupportedReason)
    }

    @Test
    fun clearKeyPairsMayContainSpaces() {
        for (value in listOf(
            "000102030405060708090a0b0c0d0e0f : 00112233445566778899aabbccddeeff",
            " 000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff, " +
                    "000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff",
        )) {
            val spec = KodiDrmParser.parse(
                mapOf(KodiDrmParser.PROP_LICENSE_TYPE to "clearkey", KodiDrmParser.PROP_LICENSE_KEY to value)
            )
            assertNull(spec?.unsupportedReason, value)
            assertEquals(keys, spec?.clearKeys?.distinct(), value)
        }
    }

    @Test
    fun plainDataUriJwks() {
        val jwks = """{"keys":[{"kty":"oct","kid":"AAECAwQFBgcICQoLDA0ODw","k":"ABEiM0RVZneImaq7zN3u_w"}]}"""
        val spec = KodiDrmParser.parse(
            mapOf(KodiDrmParser.PROP_DRM_LEGACY to "org.w3.clearkey|data:application/json,$jwks")
        )
        assertEquals(keys, spec?.clearKeys)
    }

    // Item 10
    @Test
    fun nonPrimitivePriorityDoesNotAbort() {
        val spec = KodiDrmParser.parse(
            mapOf(
                KodiDrmParser.PROP_DRM to
                        """{"com.widevine.alpha":{"priority":{"x":1},"license":{"server_url":"https://lic.example.com/wv"}}}"""
            )
        )
        assertEquals("https://lic.example.com/wv", spec?.licenseUrl)
        // Garbage in a DRM property yields an unsupported spec, never an exception.
        assertNotNull(KodiDrmParser.parse(mapOf(KodiDrmParser.PROP_DRM to """{"com.widevine.alpha":[1,2]}""")))
    }

    // Item 12, 13 and the XHTML nit
    @Test
    fun headerlessKodiListsAreM3u() {
        val kodipropFirst = "#KODIPROP:mimetype=application/dash+xml\n#EXTINF:-1,A\nhttps://cdn.example.com/a/manifest"
        assertEquals(IPTVFormat.M3U, IPTVParserFactory.detectFormat(kodipropFirst))
        assertEquals(1, M3UParser().parse(kodipropFirst).channels.size)

        val commentFirst = "# my list\n#EXTM3U\n#EXTINF:-1,A\nhttps://cdn.example.com/a.m3u8"
        assertEquals(IPTVFormat.M3U, IPTVParserFactory.detectFormat(commentFirst))
        assertEquals(1, M3UParser().parse(commentFirst).channels.size)
    }

    @Test
    fun hlsTagsCountOnlyAtLineStart() {
        val list = "#EXTM3U\n#EXTINF:-1,Why #EXT-X-STREAM-INF matters\nhttps://cdn.example.com/a.m3u8"
        assertEquals(IPTVFormat.M3U, IPTVParserFactory.detectFormat(list))
    }

    @Test
    fun xhtmlIsAWebPage() {
        val page = "<?xml version=\"1.0\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"></html>"
        assertEquals(IPTVFormat.HTML, IPTVParserFactory.detectFormat(page))
    }

    // Item 16
    @Test
    fun unsendableHeadersAreDropped() {
        assertEquals(
            listOf("X-Ok" to "1"),
            HeaderSuffixParser.parseHeaderList("X-Ok=1&X-Crlf=a%0D%0AInjected:1&X-Utf=h%C3%A9&Bad Name=1")
        )
        val split = HeaderSuffixParser.split("https://cdn.example.com/a.m3u8|User-Agent=Caf%C3%A9")
        assertTrue(split.headers.isEmpty())
        // Escapes that are not valid UTF-8 keep the raw text instead of U+FFFD.
        assertEquals("a%FFb", HeaderSuffixParser.percentDecode("a%FFb"))
    }

    // Item 3: ids stay what they were before F1.
    @Test
    fun tvgNameStillGivesTheId() {
        val playlist = M3UParser().parse(
            "#EXTM3U\n#EXTINF:-1 tvg-name=\"Guide Name\",Shown Name\nhttps://cdn.example.com/a.m3u8\n" +
                    "#EXTINF:-1,News, HD\nhttps://cdn.example.com/b.m3u8"
        )
        assertEquals("guide_name", playlist.channels[0].id)
        assertEquals("Shown Name", playlist.channels[0].name)
        assertEquals("news,_hd", playlist.channels[1].id)
        assertEquals("hd", playlist.channels[1].legacyId)
    }

    // Item 19 nits
    @Test
    fun unknownChannelCatchupOverridesTheHeaderDefault() {
        val playlist = M3UParser().parse(
            "#EXTM3U catchup=\"shift\"\n#EXTINF:-1 catchup=\"disabled\",A\nhttps://cdn.example.com/a.m3u8"
        )
        assertNull(playlist.channels.single().catchup)
    }

    @Test
    fun groupsAreCaseInsensitive() {
        val playlist = M3UParser().parse(
            "#EXTM3U\n#EXTINF:-1 group-title=\"News;news\",A\nhttps://cdn.example.com/a.m3u8\n" +
                    "#EXTINF:-1 group-title=\"news\",B\nhttps://cdn.example.com/b.m3u8"
        )
        assertEquals(listOf("News"), playlist.channels[0].groups)
        assertEquals(1, playlist.groups.size)
    }
}
