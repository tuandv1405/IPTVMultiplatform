package tss.t.tsiptv.core.stremio

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StremioModelsTest {

    // --- Manifest validation (PRD §1, AC-S4) -------------------------------------------------

    private val minimal = """{"id":"a","name":"A","version":"1.0.0","types":[],"resources":[]}"""

    private fun invalidField(json: String): String? =
        assertIs<ManifestParseResult.Invalid>(StremioManifestParser.parse(json)).field

    @Test
    fun minimalManifestIsValidAndCatalogsDefaultToEmpty() {
        val m = assertIs<ManifestParseResult.Valid>(StremioManifestParser.parse(minimal)).manifest
        assertEquals(emptyList(), m.catalogs)
        assertEquals(emptyList(), m.addonCatalogs)
        assertFalse(m.hints.isAdult)
        assertNull(m.logoUrl)
    }

    @Test
    fun missingRequiredFieldsAreNamed() {
        assertEquals("resources", invalidField("""{"id":"a","name":"A","version":"1","types":[]}"""))
        assertEquals("types", invalidField("""{"id":"a","name":"A","version":"1","resources":[]}"""))
        assertEquals("types", invalidField("""{"id":"a","name":"A","version":"1","types":"movie","resources":[]}"""))
        assertEquals("id", invalidField("""{"id":"","name":"A","version":"1","types":[],"resources":[]}"""))
        assertEquals("name", invalidField("""{"id":"a","version":"1","types":[],"resources":[]}"""))
        assertEquals("version", invalidField("""{"id":"a","name":"A","types":[],"resources":[]}"""))
        assertEquals("id", invalidField("""{"id":{"x":1},"name":"A","version":"1","types":[],"resources":[]}"""))
        assertNull(invalidField("<html>not json</html>"))
        assertNull(invalidField("[1,2]"))
    }

    @Test
    fun versionIsNotParsedAsSemver() {
        val m = StremioManifestParser.parseOrNull("""{"id":"a","name":"A","version":"banana","types":[],"resources":[]}""")
        assertEquals("banana", m?.versionText)
        assertEquals("2", StremioFixtures.manifest("legacy-manifest.json").versionText)
    }

    @Test
    fun samplerManifest() {
        val m = StremioFixtures.sampler
        assertEquals("org.tsiptv.publicdomain.static", m.id)
        assertEquals(listOf("movie", "series", "tv"), m.typeList)
        assertEquals(listOf("tspd_"), m.idPrefixes)
        assertEquals(3, m.catalogList.size)
        assertEquals(listOf("catalog", "meta", "stream"), m.resourceList.map { it.name })
        assertTrue(m.resourceList.all { it.isShortForm })
        val movie = m.catalogList.first()
        assertEquals("Public Domain Movies", movie.displayName)
        assertEquals(listOf(CatalogExtra("genre", options = listOf("Comedy"))), movie.extras)
        assertFalse(m.hints.isP2p)
    }

    @Test
    fun cinemetaManifestExtrasAndDedupe() {
        val m = StremioFixtures.cinemeta
        // Duplicate movie/top and the catalog without a type are dropped.
        assertEquals(
            listOf("movie/top", "series/top", "movie/year", "movie/imdbRating", "series/last-videos"),
            m.catalogList.map { "${it.type}/${it.id}" },
        )
        val top = m.catalogList[0]
        assertEquals("Popular", top.name)
        // `extra` wins over `extraSupported`/`genres`.
        assertEquals(listOf("Action", "Comedy", "Drama", "Science Fiction"), top.extra("genre")?.options)
        assertTrue(top.supportsSearch)
        assertTrue(top.supportsSkip)
        // skip is normalised: never required, no options.
        val year = m.catalogList[2]
        assertEquals(CatalogExtra("skip"), year.extra("skip"))
        assertEquals(listOf("genre"), year.requiredExtras.map { it.name })
        assertEquals(100, m.catalogList[4].extra("lastVideosIds")?.optionsLimit)
        assertEquals(2, m.catalogList[3].extra("genre")?.optionsLimit)
        assertEquals(listOf("official", "community"), m.addonCatalogList.map { it.id })
        assertEquals("https://example.org/cinemeta-logo.png", m.logoUrl)
    }

    @Test
    fun legacyCatalogFormAndResourceForms() {
        val m = StremioFixtures.manifest("legacy-manifest.json")
        val legacy = m.catalogList[0]
        assertEquals(
            listOf(
                CatalogExtra("genre", isRequired = true, options = listOf("Drama", "Horror")),
                CatalogExtra("search"),
                CatalogExtra("skip"),
            ),
            legacy.extras,
        )
        assertEquals("noname", m.catalogList[1].displayName)
        assertEquals(listOf(CatalogExtra("skip")), m.catalogList[2].extras)
        // 42 and the object without a name are dropped.
        assertEquals(listOf("catalog", "meta", "stream", "subtitles"), m.resourceList.map { it.name })
        val meta = m.resourceList[1]
        assertFalse(meta.isShortForm)
        assertEquals(listOf("movie"), meta.types)
        assertEquals(listOf("lg_"), meta.idPrefixes)
        assertNull(m.resourceList[2].types)
        // Lenient booleans.
        assertTrue(m.hints.isAdult)
        assertTrue(m.hints.isP2p)
        assertTrue(m.hints.isConfigurable)
        assertFalse(m.hints.isConfigurationRequired)
    }

    @Test
    fun malformedBehaviorHintsDoNotBreakTheManifest() {
        val m = StremioManifestParser.parseOrNull(
            """{"id":"a","name":"A","version":"1","types":["movie"],"resources":["stream"],"behaviorHints":"yes","catalogs":"nope"}"""
        )
        assertNotNull(m)
        assertNull(m.behaviorHints)
        assertEquals(emptyList(), m.catalogList)
    }

    @Test
    fun manifestRoundTripsThroughJson() {
        val m = StremioFixtures.cinemeta
        val encoded = StremioJson.encodeToString(StremioManifest.serializer(), m)
        assertEquals(m, StremioManifestParser.parseOrNull(encoded))
        val legacy = StremioFixtures.manifest("legacy-manifest.json")
        assertEquals(legacy, StremioManifestParser.parseOrNull(StremioJson.encodeToString(StremioManifest.serializer(), legacy)))
    }

    // --- Meta ---------------------------------------------------------------------------------

    private fun meta(file: String): StremioMeta {
        val root = StremioJson.parseToJsonElement(StremioFixtures.read(file)).jsonObject
        return StremioJson.decodeFromJsonElement(StremioMeta.serializer(), root["meta"]!!)
    }

    @Test
    fun cinemetaSeriesMetaIsParsedTolerantly() {
        val m = meta("cinemeta-series-meta.json")
        assertEquals("tt0108778", m.id)
        assertEquals("8.9", m.imdbRating)
        assertEquals(listOf("Jennifer Aniston"), m.cast)
        assertNull(m.director)
        assertNull(m.behaviorHints?.defaultVideoId)
        assertEquals(true, m.behaviorHints?.hasScheduledVideos)
        val videos = m.videos!!
        assertEquals(6, videos.size) // the string entry is dropped
        val special = videos[0]
        assertEquals(0, special.season)
        assertEquals("Friends: The Stuff You've Never Seen", special.displayTitle)
        assertEquals(1, special.episodeNumber)
        assertEquals(1, videos[2].season) // "1"
        assertEquals(2, videos[2].episodeNumber) // legacy "number"
        assertEquals("The One with Ross's New Girlfriend", videos[3].displayTitle) // title wins over name
        assertNull(videos[5].season) // "one"
        assertEquals("Pilot episode.", videos[1].synopsis)
        assertFalse(videos[1].hasInlineStreams)
        assertEquals(listOf(false, true, false), m.links!!.map { it.isInternal })
    }

    @Test
    fun samplerMetas() {
        val movie = meta("sampler/meta__movie__tspd_his_girl_friday.json")
        assertEquals("92 min", movie.runtime)
        assertNull(movie.videos)
        assertEquals(listOf("tspd_his_girl_friday"), movie.effectiveVideos.map { it.id })
        assertFalse(movie.isLive)

        val series = meta("sampler/meta__series__tspd_superman.json")
        assertEquals("The Mechanical Monsters", series.effectiveVideos.single().displayTitle)

        val tv = meta("sampler/meta__tv__tspd_test_hls.json")
        assertTrue(tv.isLive)
        assertEquals("square", tv.resolvedPosterShape)
        assertEquals(listOf("tspd_test_hls"), tv.effectiveVideos.map { it.id })
    }

    @Test
    fun messyCatalogItems() {
        val root = StremioJson.parseToJsonElement(StremioFixtures.read("messy-catalog.json")).jsonObject
        val items = root["metas"].decodeEach {
            StremioJson.decodeFromJsonElement(StremioMeta.serializer(), it).takeIf { m -> m.isValid }
        }
        assertEquals(listOf("ex_1", "ex_2", "ex_3", "ex_4"), items.map { it.id })
        assertEquals("2008", items[0].year)
        assertEquals("6.4", items[0].imdbRating)
        assertEquals(listOf("Animation"), items[0].genres)
        assertNull(items[0].behaviorHints)
        assertEquals(listOf("poster", "landscape", "square", "poster"), items.map { it.resolvedPosterShape })
        assertEquals("2010", items[1].releaseInfo)
        assertEquals("ex_4", items[3].displayName)
        assertTrue(items[2].isLive)
    }

    @Test
    fun fillMissingFromMergesOnlyAbsentFields() {
        val first = StremioMeta(id = "tt1", type = "movie", name = "First", genres = emptyList())
        val second = StremioMeta(id = "tt1", type = "movie", name = "Second", poster = "p", genres = listOf("Drama"))
        val merged = first.fillMissingFrom(second)
        assertEquals("First", merged.name)
        assertEquals("p", merged.poster)
        assertEquals(listOf("Drama"), merged.genres)
    }

    // --- Streams -------------------------------------------------------------------------------

    @Test
    fun streamBehaviorHints() {
        val root = StremioJson.parseToJsonElement(StremioFixtures.read("mixed-streams.json")).jsonObject
        val streams = root["streams"].decodeEach { StremioJson.decodeFromJsonElement(StremioStream.serializer(), it) }
        assertEquals(4, streams.size)
        val hints = streams[0].hints
        assertTrue(hints.notWebReady)
        assertEquals("fixture-1080p", hints.bingeGroup)
        assertEquals(listOf("usa", "gbr"), hints.countryWhitelist)
        // Invalid header names and CR/LF values are dropped; numbers become strings.
        assertEquals(
            mapOf("User-Agent" to "Fixture/1.0", "Referer" to "https://example.org/", "X-Num" to "5"),
            hints.requestHeaders,
        )
        assertEquals(mapOf("Content-Type" to "video/mp4"), hints.responseHeaders)
        assertEquals(123456L, hints.videoSize)
        assertEquals(setOf("someFutureHint"), hints.other.keys)
        assertEquals(JsonObject(mapOf("a" to JsonPrimitive(1))), hints.other["someFutureHint"])
        assertEquals("Direct MP4\nline two", streams[0].displayDescription)
        assertEquals("legacy title field", streams[1].displayDescription)
        assertEquals(1, streams[1].fileIdx)

        // Encoding keeps unknown hints and proxy headers.
        val again = StremioJson.decodeFromString(
            StremioStream.serializer(),
            StremioJson.encodeToString(StremioStream.serializer(), streams[0]),
        )
        assertEquals(streams[0], again)
    }

    @Test
    fun requestHeaderFilter() {
        val hints = StremioJson.decodeFromString(
            StreamBehaviorHints.serializer(),
            """{"proxyHeaders":{"request":{"Host":"evil","content-length":"1","Transfer-Encoding":"chunked",
               "Connection":"close","Range":"bytes=0-","Referer":"https://a/","X-Tab":"a\tb","X-Utf":"café",
               "X-Del":"a\u007f","X-Nul":"a\u0000"},
               "response":{"Content-Length":"5"}}}""",
        )
        assertEquals(mapOf("Referer" to "https://a/", "X-Tab" to "a\tb"), hints.requestHeaders)
        // The request-only denylist does not apply to response hints.
        assertEquals(mapOf("Content-Length" to "5"), hints.responseHeaders)
    }

    @Test
    fun videosWithoutIdAreDroppedAndEmptyInlineStreamsAreNotExclusive() {
        val m = StremioJson.decodeFromString(
            StremioMeta.serializer(),
            """{"id":"s","type":"series","videos":[{"title":"no id"},{"id":"","title":"blank"},
               {"id":"v1","streams":[]},{"id":"v2","streams":[{"url":"https://a/b.mp4"}]}]}""",
        )
        assertEquals(listOf("v1", "v2"), m.videos!!.map { it.id })
        assertFalse(m.videos!![0].hasInlineStreams)
        assertTrue(m.videos!![1].hasInlineStreams)
    }

    @Test
    fun leadingBomIsIgnored() {
        assertIs<ManifestParseResult.Valid>(StremioManifestParser.parse("\uFEFF" + minimal))
    }

    @Test
    fun samplerTvStreamHeaders() {
        val root = StremioJson.parseToJsonElement(StremioFixtures.read("sampler/stream__tv__tspd_test_hls.json")).jsonObject
        val stream = root["streams"].decodeEach { StremioJson.decodeFromJsonElement(StremioStream.serializer(), it) }.single()
        assertEquals(mapOf("User-Agent" to "TSIPTV-Example/1.0"), stream.hints.requestHeaders)
    }
}
