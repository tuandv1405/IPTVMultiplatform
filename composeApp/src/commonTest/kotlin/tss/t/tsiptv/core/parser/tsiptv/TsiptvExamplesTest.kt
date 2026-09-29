package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.parser.IPTVParserFactory
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AC-T1: the three published examples (read from `web/public/examples/`, the files the website
 * serves) parse with zero document errors, and in fact with no issue at all.
 */
class TsiptvExamplesTest {

    private fun parseExample(name: String): TsiptvSourceDocument {
        val text = TsiptvFixtures.example(name)
        assertEquals(IPTVFormat.TSIPTV_SOURCE, IPTVParserFactory.detectFormat(text), "detection of $name")
        assertTrue(TsiptvSourceParser.looksLikeSource(text))
        val result = TsiptvSourceParser.parse(TsiptvFixtures.exampleBytes(name), "https://tsiptv-8bdd6.web.app/examples/$name")
        assertIs<TsiptvParseResult.Success>(result, "$name: ${result.report.codes()}")
        assertTrue(result.report.documentErrors.isEmpty())
        assertEquals(emptyList(), result.report.codes(), "$name should have no issues")
        return result.document
    }

    @Test
    fun minimalLive() {
        val doc = parseExample("minimal-live.tsiptv.json")
        assertEquals("app.tsiptv.examples.minimal-live", doc.id)
        assertEquals(2, doc.tvChannels.size)
        assertEquals(1, doc.radioChannels.size)
        assertNull(doc.layout)
        assertNull(doc.appearance)

        val news = doc.channels.first { it.id == "example-news" }
        val stream = news.streams.single()
        assertEquals("https://cdn.example.com/news/index.m3u8", stream.url)
        assertEquals(
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Referer" to "https://example.com/"),
            stream.headers
        )
        assertEquals("ExampleNews.us", news.epgId)
        assertEquals(2, news.number)
        assertEquals(listOf("News"), news.groups)

        val radio = doc.radioChannels.single()
        assertEquals("audio/aac", radio.streams.single().mimeType)
        val mapped = radio.toIPTVChannel()
        assertTrue(mapped.isRadio)
        assertEquals("Example Radio (placeholder)", mapped.name)
        assertEquals("Radio", mapped.groupTitle)
    }

    @Test
    fun vodCatalog() {
        val doc = parseExample("vod-catalog.tsiptv.json")
        assertEquals(1, doc.revision)
        assertEquals(2, doc.movies.size)
        assertEquals(1, doc.series.size)
        assertEquals(1, doc.episodeCount)
        assertEquals("Rạp phim Phạm vi công cộng", doc.meta.name.resolve("vi"))
        assertEquals("Public Domain Cinema", doc.meta.name.resolve("de"))
        assertEquals("TS IPTV examples", doc.meta.author?.name)

        val appearance = assertNotNull(doc.appearance)
        assertEquals("#FFB300", appearance.accent)
        assertEquals(listOf("#101014", "#1C1C24"), appearance.background?.gradient)
        assertEquals(TsiptvCardKind.POSTER, appearance.card?.style)
        assertEquals(TsiptvCorner.LARGE, appearance.card?.corner)
        val (effective, contrast) = TsiptvAppearanceResolver.resolve(appearance)
        assertTrue(contrast.isEmpty())
        assertEquals("#FFB300", effective.accent)

        val sections = assertNotNull(doc.layout).home
        assertEquals(listOf("featured", "continue", "movies", "series", "comedy"), sections.map { it.id })
        val hero = sections[0]
        assertEquals(TsiptvSectionType.HERO, hero.type)
        assertEquals(listOf("his-girl-friday", "fleischer-superman"), hero.items?.map { it.target })
        assertEquals(TsiptvQuerySort.RECENT, sections[1].query?.sort)
        assertEquals(TsiptvQuerySort.YEAR_DESC, sections[2].query?.sort)
        assertEquals(20, sections[2].effectiveLimit)
        assertTrue(sections[2].seeAll)
        val comedy = sections[4]
        assertEquals(TsiptvSectionType.GRID, comedy.type)
        assertEquals(listOf("Comedy"), comedy.query?.genres)
        assertEquals(TsiptvQuerySort.NAME, comedy.query?.sort)
        assertEquals(TsiptvCardStyle(TsiptvCardKind.LANDSCAPE, TsiptvCorner.SMALL), comedy.card)
        assertFalse(comedy.groupChips)

        val bbb = doc.movies.first { it.id == "big-buck-bunny" }
        assertEquals(StreamMimeTypes.HLS, bbb.streams.single().mimeType)
        assertEquals(listOf("Animation", "Comedy"), bbb.genres)
        val hgf = doc.movies.first { it.id == "his-girl-friday" }
        assertEquals("SD", hgf.streams.single().quality)
        assertEquals("archive.org, MP4 512 kb/s", hgf.streams.single().name?.resolve("en"))
        assertEquals("1940-01-18", hgf.releaseDate)

        val superman = doc.series.single()
        assertEquals(1943, superman.endYear)
        val episode = superman.seasons.single().episodes.single()
        assertEquals("fleischer-superman-s1e1", episode.id)
        assertEquals("video/mp4", episode.streams.single().mimeType)
        assertFalse(episode.isUpcoming("2026-09-28"))
    }

    @Test
    fun composedIncludes() {
        val doc = parseExample("composed-includes.tsiptv.json")
        assertTrue(doc.channels.isEmpty() && doc.movies.isEmpty() && doc.series.isEmpty())
        assertEquals(
            listOf(
                "live" to TsiptvIncludeType.M3U,
                "guide" to TsiptvIncludeType.XMLTV,
                "sampler" to TsiptvIncludeType.STREMIO,
                "cinema" to TsiptvIncludeType.TSIPTV_SOURCE,
            ),
            doc.includes.map { it.id to it.type }
        )
        assertEquals(12, doc.includes.first { it.id == "guide" }.refreshHours)
        assertEquals(24, doc.includes.first { it.id == "sampler" }.refreshHours)

        val sections = assertNotNull(doc.layout).home
        assertEquals(6, sections.size)
        assertEquals(3, sections[0].effectiveLimit)
        val catalog = assertNotNull(sections[2].query)
        assertEquals(TsiptvQuerySource.CATALOG, catalog.from)
        assertEquals(TsiptvCatalogRef("movie", "tspd-movie"), catalog.catalog)
        val picked = sections[4]
        assertEquals(listOf("cinema:big-buck-bunny", "cinema:his-girl-friday"), picked.query?.ids)
        assertFalse(picked.seeAll)
        assertTrue(sections[5].groupChips)

        // AC-T22: only TS IPTV's own host is contacted.
        assertEquals(
            listOf("tsiptv-8bdd6.web.app"),
            TsiptvHosts.contacted("https://tsiptv-8bdd6.web.app/examples/composed-includes.tsiptv.json", doc)
        )
    }

    @Test
    fun composedIncludesTreeWithTheNestedExample() = runBlocking {
        val root = parseExample("composed-includes.tsiptv.json")
        val loads = mutableListOf<String>()
        val tree = TsiptvIncludeTreePlanner.plan(root, "https://tsiptv-8bdd6.web.app/examples/composed-includes.tsiptv.json") { include, _ ->
            loads += include.url
            val bytes = TsiptvFixtures.exampleBytes("vod-catalog.tsiptv.json")
            TsiptvFetchedDocument(TsiptvSourceParser.parse(bytes, include.url), bytes.size.toLong())
        }
        assertEquals(listOf("https://tsiptv-8bdd6.web.app/examples/vod-catalog.tsiptv.json"), loads)
        assertTrue(tree.report.issues.isEmpty(), tree.report.codes().toString())
        assertEquals(4, tree.nodes.size)
        assertTrue(tree.nodes.all { it.status == TsiptvIncludeNode.Status.ACCEPTED })
        val cinema = tree.nodes.first { it.include.id == "cinema" }
        val context = assertNotNull(cinema.context)
        assertEquals(2, context.depth)
        assertEquals(
            listOf("cinema:his-girl-friday", "cinema:big-buck-bunny"),
            cinema.document?.movies?.map { context.namespaced(it.id) }
        )
    }
}
