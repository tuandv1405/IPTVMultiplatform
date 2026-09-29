package tss.t.tsiptv.core.stremio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceMatcherTest {
    private val cinemeta = StremioFixtures.cinemeta
    private val sampler = StremioFixtures.sampler
    private val legacy = StremioFixtures.manifest("legacy-manifest.json")

    // --- AC-S9 ------------------------------------------------------------------------------

    @Test
    fun cinemetaBoardRows() {
        val rows = ResourceMatcher.boardCatalogs(cinemeta)
        assertEquals(
            listOf(
                CatalogRequest("movie", "top"),
                CatalogRequest("series", "top"),
                CatalogRequest("movie", "year", listOf("genre" to "2026")),
                CatalogRequest("movie", "imdbRating"),
            ),
            rows,
        )
        assertTrue(rows.none { it.catalogId == "last-videos" })
    }

    @Test
    fun cinemetaSearchFanOut() {
        assertEquals(
            listOf(
                CatalogRequest("movie", "top", listOf("search" to "big buck bunny")),
                CatalogRequest("series", "top", listOf("search" to "big buck bunny")),
            ),
            ResourceMatcher.searchCatalogs(cinemeta, "  big buck bunny "),
        )
        assertEquals(emptyList(), ResourceMatcher.searchCatalogs(cinemeta, "   "))
    }

    @Test
    fun searchFillsOtherRequiredExtrasWithTheFirstOption() {
        assertEquals(
            listOf(CatalogRequest("movie", "legacy", listOf("genre" to "Drama", "search" to "x"))),
            ResourceMatcher.searchCatalogs(legacy, "x"),
        )
    }

    @Test
    fun searchOnlyCatalogIsNotOnTheBoardButIsSearched() {
        val m = StremioManifestParser.parseOrNull(
            """{"id":"s","name":"S","version":"1","types":["movie"],"resources":[],
               "catalogs":[{"type":"movie","id":"find","extra":[{"name":"search","isRequired":true}]}]}"""
        )!!
        assertEquals(emptyList(), ResourceMatcher.boardCatalogs(m))
        assertEquals(listOf(CatalogRequest("movie", "find", listOf("search" to "abc"))), ResourceMatcher.searchCatalogs(m, "abc"))
    }

    @Test
    fun objectResourceWithoutTypesMatchesNothing() {
        // legacy: { "name": "stream" } has no types.
        assertFalse(ResourceMatcher.supportsStream(legacy, "movie", "anything"))
        assertFalse(ResourceMatcher.declaresResource(legacy, "stream"))
        // Cinemeta has no stream resource at all.
        assertFalse(ResourceMatcher.supportsStream(cinemeta, "movie", "tt0111161"))
        assertFalse(ResourceMatcher.declaresResource(cinemeta, "stream"))
    }

    @Test
    fun shortFormUsesManifestTypesAndPrefixes() {
        assertTrue(ResourceMatcher.supportsMeta(cinemeta, "movie", "tt0111161"))
        assertTrue(ResourceMatcher.supportsMeta(cinemeta, "series", "tt0108778"))
        assertFalse(ResourceMatcher.supportsMeta(cinemeta, "tv", "tt0108778"))
        assertFalse(ResourceMatcher.supportsMeta(cinemeta, "movie", "kitsu:1"))
        assertTrue(ResourceMatcher.supportsStream(sampler, "series", "tspd_superman_s1e1"))
        assertFalse(ResourceMatcher.supportsStream(sampler, "series", "tt0108778:1:1"))
    }

    @Test
    fun objectFormUsesItsOwnTypesAndPrefixesWithoutFallback() {
        assertTrue(ResourceMatcher.supportsMeta(legacy, "movie", "lg_1"))
        assertFalse(ResourceMatcher.supportsMeta(legacy, "series", "lg_1"))
        assertFalse(ResourceMatcher.supportsMeta(legacy, "movie", "tt1"))
        // subtitles object form: types given, idPrefixes absent → any id.
        assertTrue(ResourceMatcher.supportsSubtitles(legacy, "series", "whatever:1:1"))
    }

    @Test
    fun emptyIdPrefixesMatchAnyId() {
        val m = StremioManifestParser.parseOrNull(
            """{"id":"x","name":"X","version":"1","types":["movie"],"idPrefixes":[],"resources":["stream",{"name":"meta","types":["movie"],"idPrefixes":[]}]}"""
        )!!
        assertTrue(ResourceMatcher.supportsStream(m, "movie", "anything"))
        assertTrue(ResourceMatcher.supportsMeta(m, "movie", "anything"))
        assertFalse(ResourceMatcher.hasCatalogs(m))
        assertFalse(ResourceMatcher.declaresResource(m, "catalog"))
    }

    @Test
    fun catalogMatching() {
        assertTrue(ResourceMatcher.supportsCatalog(cinemeta, "movie", "top"))
        assertTrue(ResourceMatcher.supportsCatalog(cinemeta, "movie", "top", listOf("genre" to "Comedy", "skip" to "100")))
        assertFalse(ResourceMatcher.supportsCatalog(cinemeta, "movie", "top", listOf("lastVideosIds" to "x")))
        assertFalse(ResourceMatcher.supportsCatalog(cinemeta, "movie", "year"))
        assertTrue(ResourceMatcher.supportsCatalog(cinemeta, "movie", "year", listOf("genre" to "2025")))
        assertFalse(ResourceMatcher.supportsCatalog(cinemeta, "tv", "top"))
        // Catalogs are not gated by resources: the sampler lists "catalog", but a manifest without it still works.
        val noResource = StremioManifestParser.parseOrNull(
            """{"id":"x","name":"X","version":"1","types":["movie"],"resources":["stream"],"catalogs":[{"type":"movie","id":"c"}]}"""
        )!!
        assertTrue(ResourceMatcher.supportsCatalog(noResource, "movie", "c"))
        assertTrue(ResourceMatcher.declaresResource(noResource, "catalog"))
        assertTrue(ResourceMatcher.supportsAddonCatalog(cinemeta, "all", "official"))
        assertFalse(ResourceMatcher.supportsAddonCatalog(cinemeta, "movie", "official"))
    }

    // --- AC-S11 extras ---------------------------------------------------------------------

    @Test
    fun buildExtraOrdersByDeclarationAndLimitsValues() {
        val top = ResourceMatcher.findCatalog(cinemeta, "movie", "top")!!
        // Declared order genre, search, skip, whatever order the caller used.
        assertEquals(
            listOf("genre" to "Comedy", "search" to "his", "skip" to "200"),
            ResourceMatcher.buildExtra(top, mapOf("search" to listOf("his"), "genre" to listOf("Comedy", "Drama")), skip = 200),
        )
        // optionsLimit 1 keeps only the first; undeclared names dropped; skip 0 omitted.
        assertEquals(
            listOf("genre" to "Comedy"),
            ResourceMatcher.buildExtra(top, mapOf("genre" to listOf("Comedy", "Drama"), "foo" to listOf("bar")), skip = 0),
        )
        val featured = ResourceMatcher.findCatalog(cinemeta, "movie", "imdbRating")!!
        assertEquals(
            listOf("genre" to "Action", "genre" to "Comedy"),
            ResourceMatcher.buildExtra(featured, mapOf("genre" to listOf("Action", "Comedy", "Drama"))),
        )
        assertEquals(
            "https://meta.example.org/catalog/movie/imdbRating/genre=Action&genre=Comedy.json",
            CatalogRequest("movie", "imdbRating", listOf("genre" to "Action", "genre" to "Comedy"))
                .url(StremioFixtures.transport("https://meta.example.org/manifest.json")),
        )
    }

    @Test
    fun skipIsAlwaysLastWhateverItsDeclarationPosition() {
        val catalog = ManifestCatalog(
            "movie", "x",
            extras = listOf(CatalogExtra("skip"), CatalogExtra("search"), CatalogExtra("genre", options = listOf("A"))),
        )
        assertEquals(
            listOf("search" to "q", "genre" to "A", "skip" to "100"),
            ResourceMatcher.buildExtra(catalog, mapOf("genre" to listOf("A"), "search" to listOf("q")), skip = 100),
        )
    }

    @Test
    fun buildExtraFailsWithoutARequiredValueAndIgnoresSkipWhenUndeclared() {
        val year = ResourceMatcher.findCatalog(cinemeta, "movie", "year")!!
        assertNull(ResourceMatcher.buildExtra(year))
        val samplerMovie = ResourceMatcher.findCatalog(sampler, "movie", "tspd-movie")!!
        assertEquals(emptyList(), ResourceMatcher.buildExtra(samplerMovie, skip = 100))
        assertNull(ResourceMatcher.defaultRequiredSelections(ResourceMatcher.findCatalog(cinemeta, "series", "last-videos")!!))
        assertEquals(mapOf("genre" to listOf("2026")), ResourceMatcher.defaultRequiredSelections(year))
    }

    @Test
    fun typeTabsOrder() {
        assertEquals(
            listOf("movie", "series", "tv", "channel", "anime", "other"),
            ResourceMatcher.orderTypes(listOf("other", "tv", "anime", "series", "channel", "movie", "tv")),
        )
        val types = ResourceMatcher.boardCatalogs(sampler).map { it.type }
        assertEquals(listOf("movie", "series", "tv"), ResourceMatcher.orderTypes(types))
    }
}
