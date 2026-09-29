package tss.t.tsiptv.core.firebase

import tss.t.tsiptv.core.firebase.analystics.AnalyticsConstants
import tss.t.tsiptv.core.firebase.analystics.AnalyticsLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsLinksTest {

    @Test
    fun dropsCredentialsQueryFragmentAndHeaderSuffix() {
        assertEquals(
            "http://iptv.example.com:8080/get.php",
            AnalyticsLinks.sanitized("http://john:secret@IPTV.Example.com:8080/get.php?username=john&password=secret&type=m3u#x|User-Agent=VLC"),
        )
    }

    @Test
    fun masksSecretLookingPathSegments() {
        assertEquals(
            "https://cdn.example.org/playlist/*/list.m3u",
            AnalyticsLinks.sanitized("https://cdn.example.org/playlist/a8F3kLm29QxZ7pR4tY6w/list.m3u"),
        )
        assertEquals("https://h.example/*/m.json", AnalyticsLinks.sanitized("https://h.example/key=abc/m.json"))
        assertFalse(AnalyticsLinks.looksSecret("playlist.m3u"))
        assertFalse(AnalyticsLinks.looksSecret("get.php"))
        assertTrue(AnalyticsLinks.looksSecret("0123456789abcdef"))
    }

    @Test
    fun hostAndSchemeOnlyForHttpLinks() {
        assertEquals("iptv.example.com", AnalyticsLinks.host("http://u:p@IPTV.example.com:8080/x"))
        assertEquals("https", AnalyticsLinks.scheme("HTTPS://a.example/"))
        assertNull(AnalyticsLinks.sanitized("file:my list.m3u"))
        assertNull(AnalyticsLinks.host("rtmp://a.example/live"))
    }

    @Test
    fun capsLength() {
        val long = "https://a.example/" + List(30) { "segment" }.joinToString("/")
        assertEquals(AnalyticsLinks.MAX_VALUE_LENGTH, AnalyticsLinks.sanitized(long)!!.length)
    }

    @Test
    fun addSourceParamsForFileHasNoLink() {
        val p = AnalyticsConstants.addSourceParams(rawLink = null, hasEpg = false)
        assertEquals("file", p[AnalyticsConstants.PARAMS_SOURCE_KIND])
        assertFalse(AnalyticsConstants.PARAMS_LINK in p)
        assertFalse(AnalyticsConstants.PARAMS_LINK_HOST in p)
    }

    @Test
    fun addSourceParamsForLink() {
        val p = AnalyticsConstants.addSourceParams("https://u:p@a.example/list.m3u?token=x", hasEpg = true, includeCount = 2)
        assertEquals("link", p[AnalyticsConstants.PARAMS_SOURCE_KIND])
        assertEquals("https", p[AnalyticsConstants.PARAMS_URL_SCHEME])
        assertEquals("a.example", p[AnalyticsConstants.PARAMS_LINK_HOST])
        assertEquals("https://a.example/list.m3u", p[AnalyticsConstants.PARAMS_LINK])
        assertEquals("true", p[AnalyticsConstants.PARAMS_HAS_EPG])
        assertEquals(2, p[AnalyticsConstants.PARAMS_INCLUDE_COUNT])
    }
}
