package tss.t.tsiptv.core.tsiptv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okio.Buffer
import okio.GzipSink
import okio.buffer
import tss.t.tsiptv.core.database.InMemoryIPTVDatabase
import tss.t.tsiptv.core.parser.tsiptv.TsiptvStream
import tss.t.tsiptv.core.stremio.InMemoryStremioStores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** QC F3 round 1: guides per include, budget after gzip, concurrency, root failures, 304 refreshes, streams, EPG by name. */
class TsiptvRefreshRulesTest {

    private class Http : SourceHttpTransport {
        val responses = HashMap<String, (SourceFetchRequest) -> SourceFetchResult>()
        val requests = ArrayList<SourceFetchRequest>()
        var gateUrl: String? = null
        val gate = CompletableDeferred<Unit>()
        val gateReached = CompletableDeferred<Unit>()

        /** 200 with [etag], or 304 when the request carries that ETag. */
        fun serve(url: String, body: ByteArray, etag: String? = null) {
            responses[url] = { r -> if (etag != null && r.etag == etag) SourceFetchResult.NotModified else SourceFetchResult.Ok(body, etag, null) }
        }

        fun serve(url: String, body: String, etag: String? = null) = serve(url, body.encodeToByteArray(), etag)

        fun fail(url: String) {
            responses[url] = { SourceFetchResult.Failed("http_500", 500) }
        }

        override suspend fun fetch(request: SourceFetchRequest): SourceFetchResult {
            requests += request
            if (request.url == gateUrl) {
                gateReached.complete(Unit)
                gate.await()
            }
            val result = responses[request.url]?.invoke(request) ?: SourceFetchResult.Failed("http_404", 404)
            if (result is SourceFetchResult.Ok) {
                val cap = request.sniffCap?.invoke(result.bytes.copyOf(minOf(result.bytes.size, SourceFetchRequest.SNIFF_BYTES)))
                    ?: request.maxBytes
                if (result.bytes.size > cap) return SourceFetchResult.Failed("too_large")
            }
            return result
        }
    }

    private val noAddons = object : SourceAddonBridge {
        override suspend fun probe(manifestUrl: String): AddonProbe = AddonProbe.Failed("unreachable")
        override suspend fun install(probe: AddonProbe.Ready, ownerPlaylistId: String) = probe.addonId
        override suspend fun removeOwned(ownerPlaylistId: String, keep: Set<String>) = Unit
    }

    private val db = InMemoryIPTVDatabase()
    private val http = Http()
    private var now = 10_000_000L
    private val service = TsiptvSourceService(db, http, noAddons, InMemoryStremioStores(), nowMs = { now })
    private val root = "https://src.example.com/root.json"
    private val pl = TsiptvSourceIds.playlistIdFor("qa.root")

    private fun doc(members: String) =
        """{"format":"tsiptv-source","version":1,"id":"qa.root","meta":{"name":"Root"},$members}"""

    private fun guide(vararg channels: Pair<String, String>, title: String = "Show", names: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder("<?xml version=\"1.0\"?><tv>")
        for ((id, _) in channels) sb.append("<channel id=\"$id\"><display-name>${names[id] ?: id}</display-name></channel>")
        for ((id, t) in channels) {
            sb.append("<programme start=\"20000101000000 +0000\" stop=\"20991231000000 +0000\" channel=\"$id\"><title>${t.ifEmpty { title }}</title></programme>")
        }
        return sb.append("</tv>").toString()
    }

    private suspend fun import() {
        val preview = (service.fetchLink(root) as TsiptvSourceService.LinkFetch.Source).preview
        service.store(service.resolve(preview), adultConfirmed = false)
    }

    private suspend fun channel(poolId: String) = db.getChannelById(TsiptvSourceIds.channelId(pl, poolId))!!
    private suspend fun nowOn(guideId: String) = db.getCurrentProgramForChannel(guideId, 1_000_000_000_000L)?.title

    /** QC #2: an M3U's `x-tvg-url` guide survives refreshes where the M3U is 304 or failing. */
    @Test
    fun tvgGuidesSurviveWhenTheM3uIsNotReparsed() = runBlocking<Unit> {
        val m3u = "https://lists.example.com/l.m3u"
        val g = "https://epg.example.com/tvg.xml"
        http.serve(root, doc(""""includes":[{"id":"live","type":"m3u","url":"$m3u"}]"""), etag = "r1")
        http.serve(m3u, "#EXTM3U x-tvg-url=\"$g\"\n#EXTINF:-1 tvg-id=\"one\",One\nhttps://cdn.example.com/1.m3u8\n", etag = "m1")
        http.serve(g, guide("one" to "Morning"), etag = "g1")
        import()
        assertEquals("Morning", nowOn("one"))

        service.refresh(pl, force = true) // everything 304
        assertEquals("Morning", nowOn("one"))
        assertEquals("g1", http.requests.last { it.url == g }.etag)

        http.fail(m3u)
        service.refresh(pl, force = true)
        assertEquals("Morning", nowOn("one"))
        assertTrue(db.tsiptvStore.getIncludes(pl).any { it.includePath == "live:tvg-0" })
    }

    /** QC #3: a guide that fails keeps its programmes while another guide changes. */
    @Test
    fun aStaleGuideKeepsItsProgrammesWhenAnotherChanges() = runBlocking<Unit> {
        val g1 = "https://epg.example.com/1.xml"
        val g2 = "https://epg.example.com/2.xml"
        http.serve(
            root,
            doc(""""channels":[{"id":"a","name":"A","epgId":"ga","url":"https://c.example.com/a.m3u8"},
                {"id":"b","name":"B","epgId":"gb","url":"https://c.example.com/b.m3u8"}],"epg":["$g1","$g2"]"""),
        )
        http.serve(g1, guide("ga" to "A1"))
        http.serve(g2, guide("gb" to "B1"))
        import()
        assertEquals("A1", nowOn("ga"))
        assertEquals("B1", nowOn("gb"))

        http.fail(g1)
        http.serve(g2, guide("gb" to "B2"))
        service.refresh(pl, force = true)
        assertEquals("A1", nowOn("ga"))
        assertEquals("B2", nowOn("gb"))
    }

    /** QC #4: the 50 MiB budget counts documents after gzip. */
    @Test
    fun budgetCountsDecompressedBytes() = runBlocking<Unit> {
        val pad = "x".repeat(18 * 1024 * 1024)
        val urls = (1..3).map { "https://lists.example.com/$it.m3u" }
        http.serve(root, doc(""""includes":[${urls.mapIndexed { i, u -> """{"id":"l$i","type":"m3u","url":"$u"}""" }.joinToString(",")}]"""))
        urls.forEachIndexed { i, u ->
            http.serve(u, gzip("#EXTM3U\n#$pad\n#EXTINF:-1 tvg-id=\"c$i\",C$i\nhttps://cdn.example.com/$i.m3u8\n"))
        }
        val preview = (service.fetchLink(root) as TsiptvSourceService.LinkFetch.Source).preview
        val pending = service.resolve(preview)
        val rows = pending.resolved.includes.associateBy { it.includePath }
        assertEquals(TsiptvIncludeStatus.OK.name, rows.getValue("l0").status)
        assertEquals(TsiptvIncludeStatus.OK.name, rows.getValue("l1").status)
        // 3 × 18 MiB > 50 MiB once decompressed (the gzip bodies are tiny).
        assertEquals(TsiptvIncludeStatus.FAILED.name, rows.getValue("l2").status)
        assertEquals("budget", rows.getValue("l2").lastError)
    }

    /** QC #5: a source removed while a refresh is running is not brought back by that refresh. */
    @Test
    fun removeDuringRefreshDoesNotResurrectTheSource() = runBlocking<Unit> {
        val m3u = "https://lists.example.com/l.m3u"
        http.serve(root, doc(""""includes":[{"id":"live","type":"m3u","url":"$m3u"}]"""))
        http.serve(m3u, "#EXTM3U\n#EXTINF:-1 tvg-id=\"one\",One\nhttps://cdn.example.com/1.m3u8\n")
        import()

        http.gateUrl = m3u
        val refresh = async { service.refresh(pl, force = true) }
        http.gateReached.await()
        service.remove(pl)
        http.gate.complete(Unit)
        val result = refresh.await()

        assertIs<TsiptvRefreshResult.Failed>(result)
        assertNull(db.getPlaylistById(pl))
        assertNull(db.tsiptvStore.getSource(pl))
        assertTrue(db.getAllChannelsByPlayListId(pl).first().isEmpty())
    }

    /** QC #7: a failed root refresh keeps `fetchedAt`, is reported, and is recorded for About. */
    @Test
    fun failedRootRefreshIsRecordedAndKeepsTheTime() = runBlocking<Unit> {
        http.serve(root, doc(""""channels":[{"id":"a","name":"A","url":"https://c.example.com/a.m3u8"}]"""))
        import()
        val fetchedAt = db.tsiptvStore.getSource(pl)!!.fetchedAt

        now += 25 * 3_600_000L
        http.fail(root)
        val result = service.refresh(pl, force = true)

        assertIs<TsiptvRefreshResult.Failed>(result)
        val source = db.tsiptvStore.getSource(pl)!!
        assertEquals(fetchedAt, source.fetchedAt)
        assertEquals("http_500", source.lastErrorCode)
        assertEquals(now, source.lastErrorAt)
        assertTrue(!service.isRefreshDue(pl)) // retried after another 24 h, not on every open
        assertEquals(listOf("A"), db.getAllChannelsByPlayListId(pl).first().map { it.name })
    }

    /** QC #17 (AC-T11): all 304 → Unchanged; nothing is reparsed or rewritten. */
    @Test
    fun allNotModifiedIsUnchanged() = runBlocking<Unit> {
        val m3u = "https://lists.example.com/l.m3u"
        http.serve(root, doc(""""includes":[{"id":"live","type":"m3u","url":"$m3u"}]"""), etag = "r1")
        http.serve(m3u, "#EXTM3U\n#EXTINF:-1 tvg-id=\"one\",One\nhttps://cdn.example.com/1.m3u8\n", etag = "m1")
        import()
        val channel = db.getAllChannelsByPlayListId(pl).first().single()
        // A marker only a rewrite would undo.
        db.insertChannel(channel.copy(name = "Marker"))

        now += 1_000
        assertEquals(TsiptvRefreshResult.Unchanged, service.refresh(pl, force = true))
        assertEquals("Marker", db.getChannelById(channel.id)!!.name)
        assertEquals(now, db.tsiptvStore.getSource(pl)!!.fetchedAt)

        // A real change is stored.
        http.serve(m3u, "#EXTM3U\n#EXTINF:-1 tvg-id=\"two\",Two\nhttps://cdn.example.com/2.m3u8\n", etag = "m2")
        assertIs<TsiptvRefreshResult.Stored>(service.refresh(pl, force = true))
        assertEquals(listOf("Two"), db.getAllChannelsByPlayListId(pl).first().map { it.name })
    }

    /** Rejected deviation 3: channels keep all their streams; the first supported one plays. */
    @Test
    fun channelsKeepEveryStream() = runBlocking<Unit> {
        http.serve(
            root,
            doc(""""channels":[{"id":"m","name":"Multi","streams":[
                {"url":"https://c.example.com/drm.mpd","drm":{"system":"widevine","licenseUrl":"https://lic.example.com/"}},
                {"url":"https://c.example.com/hls.m3u8","name":"HLS"},
                {"url":"https://c.example.com/b.mp4"}]}]"""),
        )
        import()
        val ch = channel("m")
        val streams: List<TsiptvStream> = TsiptvChannelStreams.streams(ch)
        assertEquals(3, streams.size)
        assertEquals("https://c.example.com/drm.mpd", ch.url)
        val variants = TsiptvChannelStreams.variants(ch)
        // iOS / desktop: the DRM stream is skipped for the next one (spec §8.5).
        assertEquals(1, TsiptvChannelStreams.firstPlayable(variants) { it.drm == null })
        assertEquals(0, TsiptvChannelStreams.firstPlayable(variants) { true })
        assertEquals(listOf("#1", "HLS", "#3"), TsiptvChannelStreams.labels(ch, "en") { "#$it" })
    }

    /** Rejected deviation 4: without `epgId` (or tvg-id) a channel is matched to the guide by name. */
    @Test
    fun guideMatchesChannelsByNameWithoutAnId() = runBlocking<Unit> {
        val g = "https://epg.example.com/g.xml"
        val m3u = "https://lists.example.com/l.m3u"
        http.serve(
            root,
            doc(""""channels":[{"id":"n","name":{"en":"Nation TV","vi":"Truyền hình"},"url":"https://c.example.com/n.m3u8"}],
                "epg":["$g"],"includes":[{"id":"live","type":"m3u","url":"$m3u"}]"""),
        )
        http.serve(m3u, "#EXTM3U\n#EXTINF:-1,Sport One\nhttps://cdn.example.com/s.m3u8\n")
        http.serve(g, guide("x.nation" to "News at 9", "x.sport" to "Match", names = mapOf("x.nation" to "nation tv", "x.sport" to "Sport One")))
        import()
        assertEquals("x.nation", channel("n").guideId)
        assertEquals("News at 9", nowOn(channel("n").guideId))
        val sport = db.getAllChannelsByPlayListId(pl).first().first { it.name == "Sport One" }
        assertEquals("x.sport", sport.guideId)
        assertNotNull(nowOn(sport.guideId))
    }

    /** QC r2 #3: two guides covering one channel list each programme once. */
    @Test
    fun programmesFromTwoGuidesAreNotListedTwice() = runBlocking<Unit> {
        val g1 = "https://epg.example.com/1.xml"
        val g2 = "https://epg.example.com/2.xml"
        http.serve(root, doc(""""channels":[{"id":"a","name":"A","epgId":"ga","url":"https://c.example.com/a.m3u8"}],"epg":["$g1","$g2"]"""))
        http.serve(g1, guide("ga" to "Same"))
        http.serve(g2, guide("ga" to "Same"))
        import()
        assertEquals(listOf("Same"), db.getProgramsForChannel("ga").map { it.title })
        assertEquals(1, db.getCurrentAndUpcomingProgramsForChannel("ga", 0).size)
        // Programs tab (QC r3): counted once, and shown under the source channel's name.
        assertEquals(1, db.countValidPrograms(pl))
        val row = db.getChannelsWithValidProgramCounts(pl, 0).single()
        assertEquals("ga", row.channelId)
        assertEquals(1, row.programCount)
        assertEquals("A", row.name)
    }

    /** QC r2 nit: the name key drops every space ("BBCOne" == "BBC One"). */
    @Test
    fun nameMatchingIgnoresSpaces() = runBlocking<Unit> {
        assertEquals(guideNameKey("BBC One"), guideNameKey("BBCOne"))
        assertEquals(guideNameKey("Thời  Sự"), guideNameKey("thoisu"))
        val g = "https://epg.example.com/g.xml"
        http.serve(root, doc(""""channels":[{"id":"b","name":"BBCOne","url":"https://c.example.com/b.m3u8"}],"epg":["$g"]"""))
        http.serve(g, guide("bbc1.uk" to "News", names = mapOf("bbc1.uk" to "BBC One")))
        import()
        assertEquals("bbc1.uk", channel("b").guideId)
    }

    private fun gzip(text: String): ByteArray {
        val out = Buffer()
        GzipSink(out).buffer().use { it.writeUtf8(text) }
        return out.readByteArray()
    }
}
