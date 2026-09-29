package tss.t.tsiptv.core.parser.tsiptv

import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_URL
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_FIELD
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_LIMIT
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_QUERY_REF
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_UNKNOWN_TYPE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §7 layout, sections, queries and hero items. */
class TsiptvLayoutTest {

    private val includes = """"includes":[
        {"id":"live","type":"m3u","url":"https://example.com/live.m3u"},
        {"id":"guide","type":"xmltv","url":"https://example.com/guide.xml"},
        {"id":"addon","type":"stremio","url":"https://addon.example.com/manifest.json"}
    ]"""

    private fun layout(vararg sections: String): Pair<TsiptvLayout?, TsiptvValidationReport> {
        val (doc, report) = success(
            docWithChannel(includes, """"epg":["https://example.com/epg.xml"]""", """"layout":{"home":[${sections.joinToString(",")}]}""")
        )
        return doc.layout to report
    }

    private fun section(json: String): Pair<TsiptvSection?, TsiptvValidationReport> {
        val (layout, report) = layout(json)
        return layout?.home?.singleOrNull() to report
    }

    @Test
    fun rowDefaults() {
        val (row, report) = section("""{"type":"row","id":"r","title":"News","subtitle":{"en":"Sub"},"query":{"from":"channels","groups":["News"],"include":"live"}}""")
        assertTrue(report.issues.isEmpty())
        val s = assertNotNull(row)
        assertEquals("r", s.id)
        assertTrue(s.seeAll)
        assertFalse(s.groupChips)
        assertEquals(TsiptvQuerySort.SOURCE, s.query?.sort)
        assertEquals(20, s.effectiveLimit)
        assertEquals(listOf("News"), s.query?.groups)
        assertEquals("live", s.query?.include)
        assertEquals("Sub", s.subtitle?.resolve("de"))
    }

    @Test
    fun limits() {
        assertEquals(100, section("""{"type":"row","query":{"from":"movies","limit":500}}""").first?.effectiveLimit)
        assertEquals(50, section("""{"type":"row","query":{"from":"movies","limit":50}}""").first?.effectiveLimit)
        assertNull(section("""{"type":"grid","query":{"from":"movies"}}""").first?.effectiveLimit)
        assertEquals(300, section("""{"type":"grid","query":{"from":"movies","limit":300}}""").first?.effectiveLimit)
        assertEquals(10, section("""{"type":"hero","query":{"from":"movies","limit":50}}""").first?.effectiveLimit)
        for (bad in listOf("0", "501", "2.5", "\"10\"")) {
            val (s, report) = section("""{"type":"row","query":{"from":"movies","limit":$bad}}""")
            assertEquals(listOf(W_FIELD to "layout.home[0].query.limit"), report.codes(), bad)
            assertEquals(20, s?.effectiveLimit)
        }
    }

    @Test
    fun seeAllAndGroupChipsApplyOnlyWhereTheSpecSays() {
        val (layout, _) = layout(
            """{"type":"row","query":{"from":"movies"},"seeAll":false}""",
            """{"type":"grid","query":{"from":"movies"},"seeAll":true,"groupChips":true}""",
            """{"type":"grid","query":{"from":"radio"},"groupChips":true}""",
            """{"type":"row","query":{"from":"channels"},"groupChips":true}""",
        )
        val home = assertNotNull(layout).home
        assertEquals(listOf(false, false, false, true), home.map { it.seeAll })
        assertEquals(listOf(false, false, true, false), home.map { it.groupChips })
    }

    @Test
    fun sorts() {
        fun sortOf(from: String, sort: String?) =
            section("""{"type":"row","query":{"from":"$from"${if (sort != null) ",\"sort\":$sort" else ""}}}""")
        assertEquals(TsiptvQuerySort.RECENT, sortOf("continueWatching", null).first?.query?.sort)
        assertEquals(TsiptvQuerySort.NUMBER, sortOf("channels", "\"number\"").first?.query?.sort)
        assertEquals(TsiptvQuerySort.YEAR, sortOf("movies", "\"year\"").first?.query?.sort)
        val (unknown, unknownReport) = sortOf("movies", "\"popularity\"")
        assertEquals(TsiptvQuerySort.SOURCE, unknown?.query?.sort)
        assertEquals(listOf(W_UNKNOWN_TYPE to "layout.home[0].query.sort"), unknownReport.codes())
        val (recent, recentReport) = sortOf("movies", "\"recent\"")
        assertEquals(TsiptvQuerySort.SOURCE, recent?.query?.sort)
        assertEquals(listOf(W_FIELD to "layout.home[0].query.sort"), recentReport.codes())
    }

    @Test
    fun queryReferences() {
        for ((query, path) in listOf(
            """{"from":"channels","include":"nope"}""" to "layout.home[0].query.include",
            """{"from":"channels","include":"guide"}""" to "layout.home[0].query.include",
            """{"from":"channels","include":"epg-0"}""" to "layout.home[0].query.include",
            """{"from":"catalog","include":"live","catalog":{"type":"movie","id":"top"}}""" to "layout.home[0].query.include",
            """{"from":"channels","include":7}""" to "layout.home[0].query.include",
        )) {
            val (s, report) = section("""{"type":"row","query":$query}""")
            assertNull(s, query)
            assertEquals(listOf(W_QUERY_REF to path), report.codes(), query)
        }
    }

    @Test
    fun catalogQueries() {
        val (s, report) = section("""{"type":"row","query":{"from":"catalog","include":"addon","catalog":{"type":"movie","id":"top","genre":"Comedy"},"genres":["x"]}}""")
        assertTrue(report.issues.isEmpty())
        assertEquals(TsiptvCatalogRef("movie", "top", "Comedy"), s?.query?.catalog)

        for ((query, path) in listOf(
            """{"from":"catalog","catalog":{"type":"movie","id":"top"}}""" to "layout.home[0].query.include",
            """{"from":"catalog","include":"addon"}""" to "layout.home[0].query.catalog",
            """{"from":"catalog","include":"addon","catalog":{"type":"movie"}}""" to "layout.home[0].query.catalog",
        )) {
            val (skipped, r) = section("""{"type":"row","query":$query}""")
            assertNull(skipped, query)
            assertEquals(listOf(W_FIELD to path), r.codes(), query)
        }
        val (movies, r2) = section("""{"type":"row","query":{"from":"movies","catalog":{"type":"movie","id":"top"}}}""")
        assertNull(movies?.query?.catalog)
        assertEquals(listOf(W_FIELD to "layout.home[0].query.catalog"), r2.codes())
    }

    @Test
    fun idsAndFilters() {
        val (s, report) = section(
            """{"type":"row","query":{"from":"movies","ids":["a","live:x","a:b:c:d","a:b:c:d:e","bad id",""],"genres":[],"tags":["t"]}}"""
        )
        assertEquals(
            listOf(
                W_FIELD to "layout.home[0].query.ids[3]",
                W_FIELD to "layout.home[0].query.ids[4]",
                W_FIELD to "layout.home[0].query.ids[5]",
                W_FIELD to "layout.home[0].query.genres",
            ),
            report.codes()
        )
        assertEquals(listOf("a", "live:x", "a:b:c:d"), s?.query?.ids)
        assertNull(s?.query?.genres)
        assertEquals(listOf("t"), s?.query?.tags)

        val (none, r2) = section("""{"type":"row","query":{"from":"movies","ids":["::"]}}""")
        assertNull(none?.query?.ids)
        assertEquals(listOf(W_FIELD to "layout.home[0].query.ids[0]", W_FIELD to "layout.home[0].query.ids"), r2.codes())
    }

    @Test
    fun heroSections() {
        val (hero, report) = section(
            """{"type":"hero","card":{"style":"list"},"items":[
                {"image":"https://img.example.com/1.jpg","title":"One","target":"m1","autoplay":true},
                {"image":"https://img.example.com/2.jpg","target":"bad target"},
                {"title":"no image"},
                "not an object"
            ]}"""
        )
        assertEquals(
            listOf(
                W_FIELD to "layout.home[0].items[1].target",
                E_URL to "layout.home[0].items[2].image",
                W_FIELD to "layout.home[0].items[3]",
            ),
            report.codes()
        )
        val s = assertNotNull(hero)
        assertNull(s.query)
        assertNull(s.card, "hero ignores card")
        assertEquals(2, s.items?.size)
        assertTrue(s.items!!.first().autoplay)
        assertNull(s.items!![1].target)

        val (both, bothReport) = section("""{"type":"hero","query":{"from":"movies"},"items":[{"image":"https://img.example.com/1.jpg"}]}""")
        assertNull(both?.query)
        assertEquals(1, both?.items?.size)
        assertEquals(listOf(W_FIELD to "layout.home[0].query"), bothReport.codes())

        val eleven = (1..11).joinToString(",") { """{"image":"https://img.example.com/$it.jpg"}""" }
        val (many, manyReport) = section("""{"type":"hero","items":[$eleven]}""")
        assertEquals(10, many?.items?.size)
        assertEquals(listOf(W_LIMIT to "layout.home[0].items"), manyReport.codes())

        assertEquals(W_FIELD to "layout.home[0].query", section("""{"type":"hero"}""").second.codes().single())
        assertEquals(W_FIELD to "layout.home[0].items", section("""{"type":"hero","items":[{"title":"x","image":"ftp://x"}]}""").second.codes().last())
    }

    @Test
    fun rowsAndGridsNeedAQueryAndNoItems() {
        val (none, report) = section("""{"type":"row","title":"No query"}""")
        assertNull(none)
        assertEquals(listOf(W_FIELD to "layout.home[0].query"), report.codes())
        val (row, r2) = section("""{"type":"row","query":{"from":"movies"},"items":[{"image":"https://img.example.com/1.jpg"}]}""")
        assertNull(row?.items)
        assertEquals(listOf(W_FIELD to "layout.home[0].items"), r2.codes())
    }

    @Test
    fun malformedSectionsAreSkipped() {
        val (layout, report) = layout(
            """"row"""",
            """{"title":"no type","query":{"from":"movies"}}""",
            """{"type":"row","query":"movies"}""",
            """{"type":"row","query":{"sort":"name"}}""",
            """{"type":"ROW","id":"bad id","query":{"from":"movies"}}""",
        )
        assertEquals(
            listOf(
                W_FIELD to "layout.home[0]",
                W_FIELD to "layout.home[1].type",
                W_FIELD to "layout.home[2].query",
                W_FIELD to "layout.home[3].query.from",
                W_FIELD to "layout.home[4].id",
            ),
            report.codes()
        )
        val kept = assertNotNull(layout).home.single()
        assertEquals(TsiptvSectionType.ROW, kept.type)
        assertNull(kept.id)
    }

    @Test
    fun everySectionSkippedMeansTheDefaultLayout() {
        val (layout, report) = layout("""{"type":"carousel","query":{"from":"movies"}}""", """{"type":"row","query":{"from":"trending"}}""")
        assertNull(layout)
        assertEquals(2, report.count(W_UNKNOWN_TYPE))
    }

    @Test
    fun layoutShape() {
        for ((layout, path) in listOf(
            "[]" to "layout",
            "{}" to "layout.home",
            """{"home":[]}""" to "layout.home",
            """{"home":{}}""" to "layout.home",
        )) {
            val (doc, report) = success(docWithChannel(""""layout":$layout"""))
            assertNull(doc.layout, layout)
            assertEquals(listOf(W_FIELD to path), report.codes(), layout)
        }
        val many = (1..31).joinToString(",") { """{"type":"row","id":"s$it","query":{"from":"movies"}}""" }
        val (layout, report) = layout(many)
        assertEquals(30, layout?.home?.size)
        assertEquals("s30", layout?.home?.last()?.id)
        assertEquals(listOf(W_LIMIT to "layout.home"), report.codes())
    }

    @Test
    fun sectionCardOverridesAreKeptPerField() {
        val (s, report) = section("""{"type":"grid","query":{"from":"movies"},"card":{"corner":"small","showTitles":false}}""")
        assertTrue(report.issues.isEmpty())
        assertEquals(TsiptvCardStyle(style = null, corner = TsiptvCorner.SMALL, showTitles = false), s?.card)
        val (_, r2) = section("""{"type":"grid","query":{"from":"movies"},"card":{"showTitles":"no"}}""")
        assertEquals(listOf(W_FIELD to "layout.home[0].card.showTitles"), r2.codes())
    }
}
