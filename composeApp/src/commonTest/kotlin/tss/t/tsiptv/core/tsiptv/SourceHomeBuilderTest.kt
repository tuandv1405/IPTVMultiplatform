package tss.t.tsiptv.core.tsiptv

import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSectionType
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSourceDocument
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.core.stremio.MediaSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec §7: layout sections, queries, hero targets, the default layout and local search. */
class SourceHomeBuilderTest {

    private val pl = "tsiptv:qa"

    private fun vodPools(doc: TsiptvSourceDocument, history: List<MediaHistoryRecord> = emptyList()) = SourcePools(
        playlistId = pl,
        channels = emptyList(),
        movies = doc.movies.mapIndexed { i, m -> TsiptvVodMapping.item(TsiptvVodMapping.movieRecord(pl, m.id, null, i, m)) },
        series = doc.series.mapIndexed { i, s -> TsiptvVodMapping.item(TsiptvVodMapping.seriesRecord(pl, s.id, null, i, s)) },
        includes = emptyMap(),
        continueMedia = history,
    )

    private fun channel(id: String, name: String, group: String? = null, number: Int? = null, radio: Boolean = false, origin: String? = null) =
        Channel(
            id = TsiptvSourceIds.channelId(pl, id), name = name, url = "https://cdn.example.com/$id.m3u8", playlistId = pl,
            categoryId = group, number = number, isRadio = radio, originIncludePath = origin,
        )

    @Test
    fun vodExampleLayoutBuildsHeroRowsAndGrid() {
        val (doc, _) = TsiptvFixtures.success(TsiptvFixtures.example("vod-catalog.tsiptv.json"))
        val pools = vodPools(doc)
        val sections = SourceHomeBuilder.build(SourceHomeBuilder.layout(doc.layout, pools), pools, "en")
        val keys = sections.map { it.key }
        // No history yet: the continue row is left out.
        assertEquals(listOf("s:featured", "s:movies", "s:series", "s:comedy"), keys)

        val hero = sections.first()
        assertEquals(2, hero.banners.size)
        hero.banners.forEach { assertIs<SourceItem.Vod>(it.target) }

        val movies = sections.first { it.key == "s:movies" }.items.map { (it as SourceItem.Vod).item }
        val years = movies.mapNotNull { it.year }
        assertEquals(years.sortedDescending(), years)

        val comedy = sections.first { it.key == "s:comedy" }
        assertEquals(TsiptvSectionType.GRID, comedy.section.type)
        val comedyItems = comedy.items.map { (it as SourceItem.Vod).item }
        assertTrue(comedyItems.all { m -> m.genres.any { it.equals("Comedy", ignoreCase = true) } })
        assertEquals(comedyItems.map { TextFold.fold(it.name.resolve("en")) }.sorted(), comedyItems.map { TextFold.fold(it.name.resolve("en")) })
    }

    @Test
    fun continueWatchingRowAppearsWithHistoryOfThisSource() {
        val (doc, _) = TsiptvFixtures.success(TsiptvFixtures.example("vod-catalog.tsiptv.json"))
        val movie = doc.movies.first()
        val record = MediaHistoryRecord(
            sourceKind = MediaSourceKind.TSIPTV, sourceId = pl, itemType = "movie", itemId = movie.id, videoId = movie.id,
            title = "T", subtitle = null, posterUrl = null, season = null, episode = null, lastAddonId = null,
            lastBingeGroup = null, positionMs = 10, durationMs = 100, finished = false, updatedAt = 5,
        )
        val pools = vodPools(doc, listOf(record))
        val sections = SourceHomeBuilder.build(SourceHomeBuilder.layout(doc.layout, pools), pools, "en")
        val cont = sections.first { it.key == "s:continue" }
        val item = assertIs<SourceItem.Continue>(cont.items.single())
        assertEquals(movie.id, item.item?.itemId)
    }

    @Test
    fun defaultLayoutForAChannelOnlySourceListsTheChannels() {
        val pools = SourcePools(pl, listOf(channel("a", "Alpha", "News"), channel("b", "Beta", "Sport")), emptyList(), emptyList(), emptyMap())
        val sections = SourceHomeBuilder.build(SourceHomeBuilder.layout(null, pools), pools, "en")
        val channelNames = sections.flatMap { it.items }.filterIsInstance<SourceItem.ChannelItem>().map { it.channel.name }.toSet()
        assertEquals(setOf("Alpha", "Beta"), channelNames)
    }

    @Test
    fun queryIdsKeepTheirOrderAndSkipUnknownIds() {
        val json = TsiptvFixtures.doc(
            """"channels":[${TsiptvFixtures.channel("a")},${TsiptvFixtures.channel("b")},${TsiptvFixtures.channel("c")}]""",
            """"layout":{"home":[{"type":"row","id":"pick","query":{"from":"channels","ids":["c","missing","a"]}}]}""",
        )
        val (doc, _) = TsiptvFixtures.success(json)
        val pools = SourcePools(pl, listOf(channel("a", "A"), channel("b", "B"), channel("c", "C")), emptyList(), emptyList(), emptyMap())
        val row = SourceHomeBuilder.build(doc.layout!!, pools, "en").single()
        assertEquals(listOf("C", "A"), row.items.map { (it as SourceItem.ChannelItem).channel.name })
    }

    @Test
    fun includeRestrictionSelectsThatIncludeAndItsNestedOnes() {
        val json = TsiptvFixtures.doc(
            """"includes":[{"id":"live","type":"m3u","url":"https://lists.example.com/l.m3u"}]""",
            """"layout":{"home":[{"type":"row","id":"inc","query":{"from":"channels","include":"live"}}]}""",
        )
        val (doc, _) = TsiptvFixtures.success(json)
        val pools = SourcePools(
            pl,
            listOf(channel("own", "Own"), channel("live:x", "X", origin = "live"), channel("live:p:y", "Y", origin = "live:p"), channel("other:z", "Z", origin = "other")),
            emptyList(), emptyList(), emptyMap(),
        )
        val row = SourceHomeBuilder.build(doc.layout!!, pools, "en").single()
        assertEquals(listOf("X", "Y"), row.items.map { (it as SourceItem.ChannelItem).channel.name })
    }

    @Test
    fun searchIsCaseAndAccentInsensitive() {
        val pools = SourcePools(pl, listOf(channel("vtv", "Thời Sự VTV1"), channel("b", "Bóng đá")), emptyList(), emptyList(), emptyMap())
        assertEquals(listOf("Thời Sự VTV1"), SourceHomeBuilder.search(pools, "thoi su", "vi").map { SourceHomeBuilder.displayName(it, "vi") })
        assertEquals(listOf("Bóng đá"), SourceHomeBuilder.search(pools, "BONG DA", "vi").map { SourceHomeBuilder.displayName(it, "vi") })
        assertTrue(SourceHomeBuilder.search(pools, "   ", "vi").isEmpty())
    }

    @Test
    fun heroTargetsResolveToChannelsVodAndEpisodes() {
        val pools = SourcePools(pl, listOf(channel("news", "News")), emptyList(), emptyList(), emptyMap())
        assertIs<SourceItem.ChannelItem>(SourceHomeBuilder.resolveRef("news", pools))
        assertNull(SourceHomeBuilder.resolveRef("nothing", pools))
    }
}
