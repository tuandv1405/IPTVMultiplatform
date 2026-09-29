package tss.t.tsiptv.core.parser

import tss.t.tsiptv.TestAssets
import tss.t.tsiptv.core.parser.iptv.m3u.KodiDrmParser
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.model.playback.ClearKey
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.clearKeyJwks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DRM syntaxes of inputstream.adaptive: AC-K11 … AC-K15. */
class KodiDrmParserTest {

    private val playlist by lazy { M3UParser().parse(TestAssets.read("kodi/drm.m3u")) }
    private fun drm(id: String): DrmSpec? = playlist.channels.first { it.id == id }.drm

    private val expectedKeys = listOf(
        ClearKey("000102030405060708090a0b0c0d0e0f", "00112233445566778899aabbccddeeff")
    )

    // AC-K11
    @Test
    fun widevineLicenseKey() {
        assertEquals(
            DrmSpec(
                system = DrmSystem.WIDEVINE,
                licenseUrl = "https://lic.example.com/wv",
                licenseHeaders = mapOf("User-Agent" to "A", "Content-Type" to "application/octet-stream"),
            ),
            drm("wv")
        )
    }

    // AC-K12
    @Test
    fun clearKeyDrmLegacyAndJwks() {
        val spec = assertNotNull(drm("ck-legacy"))
        assertEquals(DrmSystem.CLEARKEY, spec.system)
        assertEquals(expectedKeys, spec.clearKeys)
        assertEquals(
            """{"keys":[{"kty":"oct","kid":"AAECAwQFBgcICQoLDA0ODw","k":"ABEiM0RVZneImaq7zN3u_w"}],"type":"temporary"}""",
            spec.clearKeyJwks()
        )
    }

    // AC-K13
    @Test
    fun lenientClearKeyLicenseKeyStripsUuidDashes() {
        val spec = assertNotNull(drm("ck-lenient"))
        assertEquals(DrmSystem.CLEARKEY, spec.system)
        assertEquals(expectedKeys, spec.clearKeys)
        assertNull(spec.unsupportedReason)
    }

    // AC-K14
    @Test
    fun wrappersAndUnknownSystemsAreUnsupported() {
        for (id in listOf("wrapped-body", "wrapped-response", "unwrapper", "wiseplay")) {
            val spec = assertNotNull(drm(id), id)
            assertNotNull(spec.unsupportedReason, "$id should be unsupported")
        }
        assertNull(drm("wiseplay")?.system)
    }

    // AC-K15
    @Test
    fun drmJsonWinsOverLicenseType() {
        assertEquals(
            DrmSpec(
                system = DrmSystem.WIDEVINE,
                licenseUrl = "https://lic.example.com/wv",
                licenseHeaders = mapOf("X-A" to "1"),
            ),
            drm("json-wins")
        )
    }

    @Test
    fun streamWithDrmInItsUrlHasNoDrm() {
        assertNull(drm("drm-in-url"))
    }

    @Test
    fun drmJsonHonoursPriorityAndSkipsUnsupportedSystems() {
        val spec = KodiDrmParser.parse(
            mapOf(
                KodiDrmParser.PROP_DRM to """
                    {"com.huawei.wiseplay":{"priority":1,"license":{"server_url":"https://lic.example.com/wp"}},
                     "com.widevine.alpha":{"priority":3,"license":{"server_url":"https://lic.example.com/wv"}},
                     "com.microsoft.playready":{"priority":2,"license":{"server_url":"https://lic.example.com/pr"}}}
                """.trimIndent()
            )
        )
        assertEquals(DrmSystem.PLAYREADY, spec?.system)
        assertEquals("https://lic.example.com/pr", spec?.licenseUrl)
    }

    @Test
    fun drmJsonClearKeyKeyIds() {
        val spec = KodiDrmParser.parse(
            mapOf(
                KodiDrmParser.PROP_DRM to
                        """{"org.w3.clearkey":{"license":{"keyids":{"000102030405060708090a0b0c0d0e0f":"00112233445566778899aabbccddeeff"}}}}"""
            )
        )
        assertEquals(expectedKeys, spec?.clearKeys)
    }

    @Test
    fun drmJsonWithOnlyNoneDeclaresNoDrm() {
        assertNull(KodiDrmParser.parse(mapOf(KodiDrmParser.PROP_DRM to """{"none":{}}""")))
    }

    @Test
    fun getStyleLicenceUrlIsUnsupported() {
        val spec = KodiDrmParser.parse(
            mapOf(
                KodiDrmParser.PROP_LICENSE_TYPE to "com.widevine.alpha",
                KodiDrmParser.PROP_LICENSE_KEY to "https://lic.example.com/wv?c=B{SSM}||R{SSM}|",
            )
        )
        assertNotNull(spec?.unsupportedReason)
    }

    @Test
    fun drmLegacyWithUrlAndHeaders() {
        val spec = KodiDrmParser.parse(
            mapOf(KodiDrmParser.PROP_DRM_LEGACY to "com.widevine.alpha|https://lic.example.com/wv|User-Agent=Mozilla%2F5.0")
        )
        assertEquals(
            DrmSpec(DrmSystem.WIDEVINE, "https://lic.example.com/wv", mapOf("User-Agent" to "Mozilla/5.0")),
            spec
        )
        val tooMany = KodiDrmParser.parse(mapOf(KodiDrmParser.PROP_DRM_LEGACY to "widevine|a|b|c"))
        assertNotNull(tooMany?.unsupportedReason)
    }

    @Test
    fun clearKeyDataUriJwks() {
        // base64 of {"keys":[{"kty":"oct","kid":"AAECAwQFBgcICQoLDA0ODw","k":"ABEiM0RVZneImaq7zN3u_w"}]}
        val jwks = "eyJrZXlzIjpbeyJrdHkiOiJvY3QiLCJraWQiOiJBQUVDQXdRRkJnY0lDUW9MREEwT0R3IiwiayI6IkFCRWlNMFJWWm5lSW1hcTd6TjN1X3cifV19"
        val spec = KodiDrmParser.parse(
            mapOf(KodiDrmParser.PROP_DRM_LEGACY to "org.w3.clearkey|data:application/json;base64,$jwks")
        )
        assertEquals(expectedKeys, spec?.clearKeys)
    }

    @Test
    fun keyNormalisation() {
        assertEquals("000102030405060708090a0b0c0d0e0f", KodiDrmParser.normalizeKey("00010203-0405-0607-0809-0A0B0C0D0E0F"))
        // base64url, 16 bytes
        assertEquals("000102030405060708090a0b0c0d0e0f", KodiDrmParser.normalizeKey("AAECAwQFBgcICQoLDA0ODw"))
        assertNull(KodiDrmParser.normalizeKey("tooshort"))
        assertTrue(KodiDrmParser.keySystem("WIDEVINE") == DrmSystem.WIDEVINE)
    }
}
