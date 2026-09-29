package tss.t.tsiptv.core.stremio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StremioUrlTest {

    private fun ok(input: String) = assertIs<ManifestUrlResult.Ok>(ManifestUrlNormalizer.normalize(input)).transport
    private fun err(input: String) = assertIs<ManifestUrlResult.Error>(ManifestUrlNormalizer.normalize(input)).error

    // --- encodeURIComponent ---------------------------------------------------------------

    @Test
    fun encodeComponentMatchesJavaScript() {
        assertEquals("tt0108778%3A1%3A1", StremioUrlEncoding.encodeComponent("tt0108778:1:1"))
        assertEquals("yt_id%3AUCrDkAvwZum-UTjHmzDI2iIw", StremioUrlEncoding.encodeComponent("yt_id:UCrDkAvwZum-UTjHmzDI2iIw"))
        assertEquals("big%20buck%20bunny", StremioUrlEncoding.encodeComponent("big buck bunny"))
        assertEquals("-_.!~*'()", StremioUrlEncoding.encodeComponent("-_.!~*'()"))
        assertEquals("a%2Bb%26c%3Dd%2Fe%3Ff%23", StremioUrlEncoding.encodeComponent("a+b&c=d/e?f#"))
        assertEquals("%C3%A9%E6%97%A5%F0%9F%8E%AC", StremioUrlEncoding.encodeComponent("é日🎬"))
    }

    @Test
    fun decodeComponentRoundTripsAndKeepsInvalidEscapes() {
        val s = "é 日 🎬 a+b&c=d:%"
        assertEquals(s, StremioUrlEncoding.decodeComponent(StremioUrlEncoding.encodeComponent(s)))
        assertEquals("100%zz", StremioUrlEncoding.decodeComponent("100%zz"))
        assertEquals("a+b", StremioUrlEncoding.decodeComponent("a+b"))
        assertEquals("a b", StremioUrlEncoding.decodeComponent("a+b", plusAsSpace = true))
    }

    // --- AC-S8 resource URLs ---------------------------------------------------------------

    @Test
    fun researchExamplesAreReproducedExactly() {
        val t = ok("https://v3-cinemeta.strem.io/manifest.json")
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top/genre=Comedy&skip=100.json",
            t.resourceUrl("catalog", "movie", "top", listOf("genre" to "Comedy", "skip" to "100")),
        )
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top/search=big%20buck%20bunny.json",
            t.resourceUrl("catalog", "movie", "top", listOf("search" to "big buck bunny")),
        )
        assertEquals(
            "https://v3-cinemeta.strem.io/stream/series/tt0108778%3A1%3A1.json",
            t.resourceUrl("stream", "series", "tt0108778:1:1"),
        )
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top/search=game%20of%20thrones&skip=100.json",
            t.resourceUrl("catalog", "movie", "top", listOf("search" to "game of thrones", "skip" to "100")),
        )
    }

    @Test
    fun manifestQueryStringIsKeptAfterJson() {
        val t = ok("https://addon.example.org/cfg/manifest.json?key=abc&x=1")
        assertEquals("https://addon.example.org/cfg/meta/movie/tt1.json?key=abc&x=1", t.resourceUrl("meta", "movie", "tt1"))
        assertEquals("https://addon.example.org/cfg/manifest.json?key=abc&x=1", t.manifestUrl)
    }

    @Test
    fun basePathKeepsConfigSegmentVerbatimAndOnlyLastManifestIsRemoved() {
        val t = ok("https://host.example.org/%7B%22token%22%3A%22x%22%7D/manifest.json/manifest.json")
        assertEquals("/%7B%22token%22%3A%22x%22%7D/manifest.json", t.basePath)
        assertEquals(
            "https://host.example.org/%7B%22token%22%3A%22x%22%7D/manifest.json/stream/movie/a.json",
            t.resourceUrl("stream", "movie", "a"),
        )
    }

    @Test
    fun extraValuesAreEncodedAndRepeated() {
        val t = ok("https://a.example.org/manifest.json")
        assertEquals(
            "https://a.example.org/catalog/movie/x/genre=Action&genre=Science%20Fiction&search=a%26b%3Dc.json",
            t.resourceUrl("catalog", "movie", "x", listOf("genre" to "Action", "genre" to "Science Fiction", "search" to "a&b=c")),
        )
        assertEquals("https://a.example.org/catalog/movie/x.json", t.resourceUrl("catalog", "movie", "x", emptyList()))
    }

    @Test
    fun configureUrlAndDisplayHost() {
        val t = ok("https://user:pw@Host.Example.org:8443/secret-token/manifest.json?q=1")
        assertEquals("https://user:pw@host.example.org:8443/secret-token/configure", t.configureUrl)
        assertEquals("host.example.org:8443", t.displayHost)
        assertEquals("host.example.org", t.host)
        assertFalse("secret-token" in t.toString())
    }

    // --- Normalisation (PRD §1, AC-S2, AC-S3) ---------------------------------------------

    @Test
    fun stremioSchemeBecomesHttps() {
        val t = ok("  stremio://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json \n")
        assertEquals("https://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json", t.manifestUrl)
        assertTrue(t.isHttps)
    }

    @Test
    fun stremioSchemeOnLocalNetworkBecomesHttp() {
        assertEquals("http://127.0.0.1:7000/manifest.json", ok("stremio://127.0.0.1:7000/manifest.json").manifestUrl)
        assertEquals("http://10.0.2.2:7000/manifest.json", ok("stremio://10.0.2.2:7000/manifest.json").manifestUrl)
        assertEquals("http://192.168.1.5/manifest.json", ok("stremio://192.168.1.5/manifest.json").manifestUrl)
        assertEquals("http://172.20.0.1/manifest.json", ok("stremio://172.20.0.1/manifest.json").manifestUrl)
        assertEquals("http://localhost:7000/manifest.json", ok("STREMIO://localhost:7000/manifest.json").manifestUrl)
        assertEquals("https://172.32.0.1/manifest.json", ok("stremio://172.32.0.1/manifest.json").manifestUrl)
        assertEquals("https://10.example.org/manifest.json", ok("stremio://10.example.org/manifest.json").manifestUrl)
    }

    @Test
    fun httpIsAcceptedAndSchemeIsLowerCased() {
        val t = ok("HTTP://Example.org/a/manifest.json")
        assertEquals("http://example.org/a/manifest.json", t.manifestUrl)
        assertFalse(t.isHttps)
    }

    @Test
    fun acceptanceS3Errors() {
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("https://example.com/foo"))
        assertEquals(ManifestUrlError.LEGACY, err("https://opensubtitles.example.org/stremio/v1"))
        assertEquals(ManifestUrlError.LEGACY, err("https://legacy.example.org/stremio/v1/"))
        assertEquals(ManifestUrlError.LOCAL_SERVER, err("http://127.0.0.1:11470/local-addon/manifest.json"))
        assertEquals(ManifestUrlError.LOCAL_SERVER, err("http://localhost:11470/local-addon/manifest.json"))
    }

    @Test
    fun otherRejections() {
        assertEquals(ManifestUrlError.NOT_MANIFEST, err(""))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("example.org/manifest.json"))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("ipfs://bafy/manifest.json"))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("ipns://name/manifest.json"))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("ftp://example.org/manifest.json"))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("stremio:///detail/movie/tt1"))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("https://example.org/manifest.json.bak"))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("https:///manifest.json"))
        assertEquals(ManifestUrlError.NOT_MANIFEST, err("https://example.org:99999/manifest.json"))
        // A different port on loopback is a normal (local) addon.
        assertEquals("http://127.0.0.1:7000/manifest.json", ok("http://127.0.0.1:7000/manifest.json").manifestUrl)
    }

    @Test
    fun fragmentIsDroppedAndSpacesAreEscaped() {
        assertEquals("https://a.example.org/manifest.json", ok("https://a.example.org/manifest.json#top").manifestUrl)
        assertEquals("https://a.example.org/my%20addon/manifest.json", ok("https://a.example.org/my addon/manifest.json").manifestUrl)
    }

    @Test
    fun localNetworkHosts() {
        assertTrue(ManifestUrlNormalizer.isLocalNetworkHost("127.0.0.1"))
        assertTrue(ManifestUrlNormalizer.isLocalNetworkHost("172.16.0.1"))
        assertTrue(ManifestUrlNormalizer.isLocalNetworkHost("172.31.255.255"))
        assertFalse(ManifestUrlNormalizer.isLocalNetworkHost("172.15.0.1"))
        assertFalse(ManifestUrlNormalizer.isLocalNetworkHost("192.169.0.1"))
        assertFalse(ManifestUrlNormalizer.isLocalNetworkHost("1.2.3"))
        assertFalse(ManifestUrlNormalizer.isLocalNetworkHost("256.0.0.1"))
    }

    // --- stremio:/// page links -------------------------------------------------------------

    @Test
    fun deepLinks() {
        val discover = assertIs<StremioDeepLink.Discover>(
            StremioDeepLink.parse("stremio:///discover/https%3A%2F%2Fmeta.example.org%2Fmanifest.json/series/top?genre=Comedy")
        )
        assertEquals("https://meta.example.org/manifest.json", discover.transportUrl)
        assertEquals("series", discover.type)
        assertEquals("top", discover.catalogId)
        assertEquals(listOf("genre" to "Comedy"), discover.extra)

        assertEquals(StremioDeepLink.Search("big buck"), StremioDeepLink.parse("stremio:///search?search=big%20buck"))
        assertEquals(StremioDeepLink.Search("a b"), StremioDeepLink.parse("stremio:///search?search=a+b"))
        assertEquals(
            StremioDeepLink.Detail("series", "tt0108778", "tt0108778:1:1", autoPlay = true),
            StremioDeepLink.parse("stremio:///detail/series/tt0108778/tt0108778%3A1%3A1?autoPlay=true"),
        )
        assertEquals(StremioDeepLink.Detail("movie", "tt1", null, false), StremioDeepLink.parse("stremio:///detail/movie/tt1"))
        assertNull(StremioDeepLink.parse("stremio:///board"))
        assertNull(StremioDeepLink.parse("stremio:///library"))
        assertNull(StremioDeepLink.parse("stremio:///discover"))
        assertNull(StremioDeepLink.parse("stremio:///search"))
        assertNull(StremioDeepLink.parse("https://example.org/detail/movie/tt1"))
    }
}
