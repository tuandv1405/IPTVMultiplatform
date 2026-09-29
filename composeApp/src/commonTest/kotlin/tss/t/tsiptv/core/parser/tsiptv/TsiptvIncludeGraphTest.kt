package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.asset
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_INCLUDE_CYCLE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_LIMIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §9.3 depth, cycles and tree-wide limits (AC-T10), id namespacing, URL keys. */
class TsiptvIncludeGraphTest {

    /** A tiny static "server": URL → document text. */
    private class Server(private val files: Map<String, String>) {
        val requested = mutableListOf<String>()
        fun load(include: TsiptvInclude): TsiptvFetchedDocument? {
            requested += include.url
            val text = files[include.url] ?: return null
            return TsiptvFetchedDocument(TsiptvSourceParser.parse(text, include.url), text.length.toLong())
        }
    }

    private fun sourceInclude(id: String, url: String) = """{"id":"$id","type":"tsiptv-source","url":"$url"}"""

    private fun nested(id: String, vararg includes: String) =
        doc(""""channels":[${channel("$id-ch")}]""", """"includes":[${includes.joinToString(",")}]""", id = id)

    // AC-T10: A includes B includes A.
    @Test
    fun cycleIsSkipped() = runBlocking {
        val aUrl = "https://qa.example.com/cycle-a.tsiptv.json"
        val bUrl = "https://qa.example.com/cycle-b.tsiptv.json"
        val server = Server(mapOf(aUrl to asset("cycle-a.tsiptv.json"), bUrl to asset("cycle-b.tsiptv.json")))
        val (root, _) = success(asset("cycle-a.tsiptv.json"), aUrl)
        val tree = TsiptvIncludeTreePlanner.plan(root, aUrl) { include, _ -> server.load(include) }

        assertEquals(listOf(bUrl), server.requested, "A is never fetched again")
        assertEquals(listOf(E_INCLUDE_CYCLE to "[b] includes[0].url"), tree.report.codes())
        val b = tree.nodes.single()
        assertEquals(TsiptvIncludeNode.Status.ACCEPTED, b.status)
        assertEquals(TsiptvIncludeNode.Status.REFUSED, b.children.single().status)
        assertNull(b.children.single().context)
        assertEquals(listOf("b:b1"), b.document?.movies?.map { b.context!!.namespaced(it.id) })
    }

    // AC-T10: depth 4 is refused.
    @Test
    fun depthFourIsRefused() = runBlocking {
        val server = Server(
            mapOf(
                "https://x.example.com/b.json" to nested("b", sourceInclude("c", "https://x.example.com/c.json")),
                "https://x.example.com/c.json" to nested(
                    "c",
                    sourceInclude("d", "https://x.example.com/d.json"),
                    """{"id":"m","type":"m3u","url":"https://x.example.com/d.m3u"}""",
                ),
                "https://x.example.com/d.json" to nested("d"),
            )
        )
        val (root, _) = success(nested("a", sourceInclude("b", "https://x.example.com/b.json")))
        val tree = TsiptvIncludeTreePlanner.plan(root, "https://x.example.com/a.json") { include, _ -> server.load(include) }

        assertEquals(listOf("https://x.example.com/b.json", "https://x.example.com/c.json"), server.requested)
        assertEquals(
            listOf(E_INCLUDE_CYCLE to "[b:c] includes[0]", E_INCLUDE_CYCLE to "[b:c] includes[1]"),
            tree.report.codes()
        )
        val c = tree.nodes.single().children.single()
        assertEquals(3, c.context?.depth)
        assertEquals("b:c", c.context?.includePath)
        assertEquals("b:c:c-ch", c.context?.namespaced(c.document!!.channels.single().id))
        assertTrue(c.children.all { it.status == TsiptvIncludeNode.Status.REFUSED })
        assertEquals(2, tree.flatten().count { it.status == TsiptvIncludeNode.Status.ACCEPTED })
    }

    // AC-T10: a 21st include in the tree is dropped with W_LIMIT.
    @Test
    fun twentyFirstIncludeInTheTreeIsDropped() = runBlocking {
        val nestedUrl = "https://x.example.com/nested.json"
        val rootIncludes = listOf(sourceInclude("n", nestedUrl)) +
                (1..19).map { """{"id":"m$it","type":"m3u","url":"https://x.example.com/$it.m3u"}""" }
        val server = Server(mapOf(nestedUrl to nested("n", """{"id":"inner","type":"xmltv","url":"https://x.example.com/g.xml"}""")))
        val (root, report) = success(doc(""""includes":[${rootIncludes.joinToString(",")}]"""))
        assertTrue(report.issues.isEmpty())
        val tree = TsiptvIncludeTreePlanner.plan(root, null) { include, _ -> server.load(include) }

        assertEquals(listOf(W_LIMIT to "includes[19]"), tree.report.codes())
        assertEquals(1, tree.report.skippedItemCount)
        val all = tree.flatten()
        assertEquals(21, all.size)
        assertEquals(20, all.count { it.status == TsiptvIncludeNode.Status.ACCEPTED })
        assertEquals("m19", all.last { it.status == TsiptvIncludeNode.Status.REFUSED }.include.id)
    }

    @Test
    fun aFailedOrInvalidNestedDocumentIsAFailedNodeAndDoesNotFailTheRoot() = runBlocking {
        val server = Server(
            mapOf(
                "https://x.example.com/v2.json" to asset("doc-version-2.tsiptv.json"),
                "https://x.example.com/partial.json" to doc(""""channels":[${channel("ok")}, ${channel("bad", url = "rtmp://x/y")}]""", id = "partial"),
            )
        )
        val (root, _) = success(
            doc(
                """"channels":[${channel("own")}]""",
                """"includes":[${sourceInclude("down", "https://x.example.com/404.json")},${sourceInclude("v2", "https://x.example.com/v2.json")},${sourceInclude("partial", "https://x.example.com/partial.json")}]""",
            )
        )
        val tree = TsiptvIncludeTreePlanner.plan(root, null) { include, _ -> server.load(include) }
        assertEquals(
            // 404 → fetch failed (keep stale); version 2 → rejected (drop).
            listOf(TsiptvIncludeNode.Status.FETCH_FAILED, TsiptvIncludeNode.Status.REJECTED, TsiptvIncludeNode.Status.ACCEPTED),
            tree.nodes.map { it.status }
        )
        // A rejected nested document is one E_INCLUDE on the root; valid nested reports are kept,
        // prefixed with the include path. Nothing is a document error of the tree.
        // The failed fetch is reported with the (placeholder) fetch-failure code.
        assertFalse(tree.report.isRejected)
        assertTrue(tree.report.issues.single { it.path == "includes[1]" }.message.contains("E_VERSION"))
        assertEquals(
            listOf(
                TsiptvIncludeGuard.FETCH_FAILED_CODE to "includes[0]",
                TsiptvIssueCode.E_INCLUDE to "includes[1]",
                TsiptvIssueCode.E_URL to "[partial] channels[1].url",
                TsiptvIssueCode.E_NO_STREAM to "[partial] channels[1]",
            ),
            tree.report.codes()
        )
    }

    @Test
    fun guardRulesDirectly() {
        val guard = TsiptvIncludeGuard("https://root.example.com/src.json")
        val root = guard.root
        assertEquals(1, root.depth)
        assertEquals("", root.includePath)
        assertEquals("item", root.namespaced("item"))

        val self = TsiptvInclude("self", TsiptvIncludeType.TSIPTV_SOURCE, "HTTP://ROOT.example.com:80/src.json#x")
        assertIs<TsiptvIncludeDecision.Refused>(guard.admit(root, self, 0))

        val m3u = TsiptvInclude("live", TsiptvIncludeType.M3U, "https://root.example.com/live.m3u")
        val accepted = assertIs<TsiptvIncludeDecision.Accepted>(guard.admit(root, m3u, 1))
        assertEquals(2, accepted.context.depth)
        assertEquals("live:ExampleTV.us_HD", accepted.context.namespaced(TsiptvIds.sanitizeForeignId("ExampleTV.us@HD")!!))
        assertEquals(1, guard.includeCount)
        assertEquals(listOf(E_INCLUDE_CYCLE to "includes[0].url"), guard.report.codes())
    }

    @Test
    fun mergedItemLimits() {
        val guard = TsiptvIncludeGuard(null)
        val child = TsiptvIncludeContext(2, emptyList(), listOf("big"))
        assertEquals(15_000, guard.admitItems(TsiptvPoolKind.CHANNEL, 15_000, guard.root))
        assertEquals(5_000, guard.admitItems(TsiptvPoolKind.CHANNEL, 10_000, child))
        assertEquals(0, guard.admitItems(TsiptvPoolKind.CHANNEL, 1, child))
        assertEquals(5_000, guard.admitItems(TsiptvPoolKind.MOVIE, 5_000, guard.root))
        assertEquals(1_000, guard.admitItems(TsiptvPoolKind.SERIES, 1_001, child))
        assertEquals(
            listOf(W_LIMIT to "[big]", W_LIMIT to "[big]", W_LIMIT to "[big]"),
            guard.report.codes()
        )
        assertEquals(5_000 + 1 + 1, guard.report.skippedItemCount)
    }

    @Test
    fun fetchBudget() {
        val guard = TsiptvIncludeGuard(null)
        val child = TsiptvIncludeContext(2, emptyList(), listOf("partner"))
        // The root counts (§9.3); a 304 counts 0.
        guard.recordRootFetched(4L * 1024 * 1024)
        assertTrue(guard.recordFetched(guard.root, TsiptvIncludeGuard.includeEntry(0), 36L * 1024 * 1024))
        assertTrue(guard.recordFetched(guard.root, TsiptvIncludeGuard.epgEntry(0), 0))
        assertTrue(guard.canFetch)
        assertEquals(10L * 1024 * 1024, guard.remainingBudget)
        assertTrue(guard.recordFetched(child, TsiptvIncludeGuard.includeEntry(1), 10L * 1024 * 1024))
        assertFalse(guard.canFetch)
        assertFalse(guard.recordFetched(child, TsiptvIncludeGuard.epgEntry(2), 1))
        guard.recordBudgetSpent(guard.root, TsiptvIncludeGuard.includeEntry(3))
        guard.recordFetchFailed(guard.root, TsiptvIncludeGuard.epgEntry(1))
        // Every fetch failure, budget included, is an E_INCLUDE on the declaring entry; nothing is dropped.
        assertEquals(
            listOf(TsiptvIssueCode.E_INCLUDE to "[partner] epg[2]", TsiptvIssueCode.E_INCLUDE to "includes[3]", TsiptvIssueCode.E_INCLUDE to "epg[1]"),
            guard.report.codes()
        )
        assertEquals(0, guard.report.skippedItemCount)
        assertFalse(guard.report.isRejected)
    }

    @Test
    fun urlKeys() {
        val key = TsiptvUrlKey.of("https://example.com/a.json")
        assertEquals(key, TsiptvUrlKey.of("HTTPS://EXAMPLE.com:443/a.json#frag"))
        assertEquals(key, TsiptvUrlKey.of("http://user:pw@example.com/a.json"))
        assertEquals(TsiptvUrlKey.of("https://example.com"), TsiptvUrlKey.of("https://example.com/"))
        assertEquals(TsiptvUrlKey.of("https://example.com?x=1"), TsiptvUrlKey.of("https://example.com/?x=1"))
        assertFalse(key == TsiptvUrlKey.of("https://example.com/A.json"))
        assertFalse(key == TsiptvUrlKey.of("https://example.com/a.json?v=2"))
        assertFalse(key == TsiptvUrlKey.of("https://example.com:8443/a.json"))
    }

    @Test
    fun ids() {
        assertEquals("ExampleTV.us_HD", TsiptvIds.sanitizeForeignId("ExampleTV.us@HD"))
        assertEquals("a_b_c", TsiptvIds.sanitizeForeignId("a b:c"))
        assertEquals("ch_1_HD", TsiptvIds.sanitizeForeignId("ch#1/HD"))
        assertNull(TsiptvIds.sanitizeForeignId("@@@"))
        assertNull(TsiptvIds.sanitizeForeignId("   "))
        assertEquals("archive:partner:item", TsiptvIds.namespaced(listOf("archive", "partner"), "item"))
        assertEquals("item", TsiptvIds.namespaced(emptyList(), "item"))
    }

    @Test
    fun hosts() {
        val (doc, _) = success(
            doc(
                """"includes":[
                    {"id":"a","type":"m3u","url":"https://Lists.Example.com:8080/a.m3u"},
                    {"id":"b","type":"stremio","url":"https://user@addon.example.com/manifest.json"},
                    {"id":"c","type":"m3u","url":"https://lists.example.com/c.m3u"}
                ]""",
                """"epg":["https://epg.example.com/g.xml"]""",
            )
        )
        assertEquals(
            listOf("root.example.com", "lists.example.com", "addon.example.com", "epg.example.com"),
            TsiptvHosts.contacted("https://root.example.com/s.json", doc)
        )
        assertEquals(listOf("lists.example.com", "addon.example.com", "epg.example.com"), TsiptvHosts.contacted(null, doc))
    }
}
