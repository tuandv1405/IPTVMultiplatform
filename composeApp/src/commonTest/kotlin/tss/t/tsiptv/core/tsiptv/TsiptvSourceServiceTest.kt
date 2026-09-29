package tss.t.tsiptv.core.tsiptv

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.database.InMemoryIPTVDatabase
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode
import tss.t.tsiptv.core.stremio.InMemoryStremioStores
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.core.stremio.MediaSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD F3 §1, §5: import, include runtime, stale copies, conditional GET, adult rules and removal. */
class TsiptvSourceServiceTest {

    private class FakeHttp : SourceHttpTransport {
        val responses = HashMap<String, (SourceFetchRequest) -> SourceFetchResult>()
        val requests = ArrayList<SourceFetchRequest>()

        fun ok(url: String, body: String, etag: String? = null) {
            responses[url] = { SourceFetchResult.Ok(body.encodeToByteArray(), etag, null) }
        }

        fun fail(url: String) {
            responses[url] = { SourceFetchResult.Failed("http_500", 500) }
        }

        override suspend fun fetch(request: SourceFetchRequest): SourceFetchResult {
            requests += request
            val result = responses[request.url]?.invoke(request) ?: SourceFetchResult.Failed("http_404", 404)
            // As the real transport: the first bytes may lower the cap (sources are capped at 5 MiB).
            if (result is SourceFetchResult.Ok) {
                val cap = request.sniffCap?.invoke(result.bytes.copyOf(minOf(result.bytes.size, SourceFetchRequest.SNIFF_BYTES)))
                    ?: request.maxBytes
                if (result.bytes.size > cap) return SourceFetchResult.Failed("too_large")
            }
            return result
        }
    }

    private class FakeAddons : SourceAddonBridge {
        val removed = ArrayList<String>()
        override suspend fun probe(manifestUrl: String): AddonProbe = AddonProbe.Failed("unreachable")
        override suspend fun install(probe: AddonProbe.Ready, ownerPlaylistId: String): String = probe.addonId
        override suspend fun removeOwned(ownerPlaylistId: String, keep: Set<String>) {
            removed += ownerPlaylistId
        }
    }

    private val db = InMemoryIPTVDatabase()
    private val http = FakeHttp()
    private val addons = FakeAddons()
    private val history = InMemoryStremioStores()
    private var now = 1_000_000L
    private val service = TsiptvSourceService(db, http, addons, history, nowMs = { now })

    private val root = "https://src.example.com/root.tsiptv.json"
    private val m3u = "https://lists.example.com/live.m3u"
    private val nested = "https://src.example.com/nested.tsiptv.json"

    private fun rootDoc(includes: String, channels: String = """{"id":"own","name":"Own","url":"https://cdn.example.com/own.m3u8"}""") =
        """{"format":"tsiptv-source","version":1,"id":"qa.root","meta":{"name":"Root"},
           "channels":[$channels],"includes":[$includes]}"""

    private val m3uBody = "#EXTM3U\n#EXTINF:-1,One\nhttps://cdn.example.com/one.m3u8\n#EXTINF:-1,Two\nhttps://cdn.example.com/two.m3u8\n"

    private fun nestedDoc(adult: Boolean, channelId: String = "n1") =
        """{"format":"tsiptv-source","version":1,"id":"qa.nested","meta":{"name":"Nested","adult":$adult},
           "channels":[{"id":"$channelId","name":"Nested $channelId","url":"https://cdn.example.com/$channelId.m3u8"}]}"""

    private suspend fun importFromLink(adultConfirmed: Boolean = false): TsiptvStoreResult {
        val fetched = service.fetchLink(root)
        assertIs<TsiptvSourceService.LinkFetch.Source>(fetched)
        val pending = service.resolve(fetched.preview)
        return service.store(pending, adultConfirmed)
    }

    private val playlistId = TsiptvSourceIds.playlistIdFor("qa.root")

    private suspend fun channelNames() = db.getAllChannelsByPlayListId(playlistId).first().map { it.name }.sorted()

    @Test
    fun importStoresRootAndIncludeChannelsInOnePlaylist() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"live","type":"m3u","url":"$m3u"}"""))
        http.ok(m3u, m3uBody, etag = "\"m1\"")

        val result = importFromLink()

        assertEquals(playlistId, result.playlist.id)
        assertEquals(listOf("One", "Own", "Two"), channelNames())
        val include = db.tsiptvStore.getIncludes(playlistId).single()
        assertEquals("live", include.includePath)
        assertEquals(TsiptvIncludeStatus.OK.name, include.status)
        assertEquals("\"m1\"", include.etag)
        assertNotNull(db.tsiptvStore.getSource(playlistId))
        assertEquals("TSIPTV_SOURCE", db.getPlaylistById(playlistId)!!.format)
    }

    @Test
    fun previewCountsRootItemsAndHostsBeforeAnythingIsStored() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"live","type":"m3u","url":"$m3u"}"""))
        val preview = (service.fetchLink(root) as TsiptvSourceService.LinkFetch.Source).preview
        assertEquals(1, preview.tvCount)
        assertEquals(1, preview.includeCount)
        assertTrue(preview.hosts.contains("lists.example.com"))
        assertNull(db.getPlaylistById(playlistId))
    }

    @Test
    fun failedIncludeOnRefreshKeepsTheStaleCopy() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"live","type":"m3u","url":"$m3u"}"""))
        http.ok(m3u, m3uBody)
        importFromLink()

        http.fail(m3u)
        now += 60_000
        val result = service.refresh(playlistId, force = true)

        assertIs<TsiptvRefreshResult.Stored>(result)
        assertEquals(listOf("One", "Own", "Two"), channelNames())
        val include = db.tsiptvStore.getIncludes(playlistId).single()
        assertEquals(TsiptvIncludeStatus.STALE.name, include.status)
        assertTrue(result.result.report.has(TsiptvIssueCode.E_INCLUDE))
    }

    @Test
    fun refreshSendsConditionalHeadersAndReusesNotModifiedIncludes() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"live","type":"m3u","url":"$m3u"}"""), etag = "\"r1\"")
        http.ok(m3u, m3uBody, etag = "\"m1\"")
        importFromLink()

        http.responses[root] = { SourceFetchResult.NotModified }
        http.responses[m3u] = { SourceFetchResult.NotModified }
        http.requests.clear()
        service.refresh(playlistId, force = true)

        assertEquals("\"r1\"", http.requests.first { it.url == root }.etag)
        assertEquals("\"m1\"", http.requests.first { it.url == m3u }.etag)
        assertEquals(listOf("One", "Own", "Two"), channelNames())
        assertEquals(TsiptvIncludeStatus.OK.name, db.tsiptvStore.getIncludes(playlistId).single().status)
    }

    @Test
    fun adultNestedIncludeIsReportedOnImport() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"n","type":"tsiptv-source","url":"$nested"}"""))
        http.ok(nested, nestedDoc(adult = true))
        val preview = (service.fetchLink(root) as TsiptvSourceService.LinkFetch.Source).preview
        val pending = service.resolve(preview)
        assertEquals(listOf("n"), pending.adultIncludePaths)
    }

    @Test
    fun newAdultContentOfAnUnconfirmedSourceIsWithheldUntilConfirmed() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"n","type":"tsiptv-source","url":"$nested"}"""))
        http.ok(nested, nestedDoc(adult = false, channelId = "old"))
        importFromLink()
        assertEquals(listOf("Nested old", "Own"), channelNames())

        // The include turns adult: its new content is held back, the last non-adult copy stays.
        http.ok(nested, nestedDoc(adult = true, channelId = "new"))
        service.refresh(playlistId, force = true)
        val include = db.tsiptvStore.getIncludes(playlistId).single()
        assertTrue(include.adultWithheld)
        assertFalse(channelNames().contains("Nested new"))
        val source = db.tsiptvStore.getSource(playlistId)!!
        assertTrue(source.needsAdultConfirmation(db.tsiptvStore.getIncludes(playlistId)))

        // "Confirm" in About merges it.
        service.confirmAdult(playlistId)
        assertTrue(channelNames().contains("Nested new"))
        assertFalse(db.tsiptvStore.getIncludes(playlistId).single().adultWithheld)
    }

    @Test
    fun includeCycleIsReportedAndDoesNotLoop() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"self","type":"tsiptv-source","url":"$root"}"""))
        val result = importFromLink()
        assertTrue(result.report.has(TsiptvIssueCode.E_INCLUDE_CYCLE))
        assertEquals(listOf("Own"), channelNames())
    }

    @Test
    fun nothingLoadedIsNotStored() = runBlocking<Unit> {
        http.ok(
            root,
            """{"format":"tsiptv-source","version":1,"id":"qa.root","meta":{"name":"Root"},
               "includes":[{"id":"live","type":"m3u","url":"$m3u"}]}""",
        )
        http.fail(m3u)
        val preview = (service.fetchLink(root) as TsiptvSourceService.LinkFetch.Source).preview
        val pending = service.resolve(preview)
        assertTrue(pending.nothingLoaded)
        assertFailsWith<TsiptvNothingLoadedException> { service.store(pending, adultConfirmed = false) }
        assertNull(db.getPlaylistById(playlistId))
    }

    @Test
    fun rejectedDocumentStoresNothing() = runBlocking<Unit> {
        http.ok(root, """{"format":"tsiptv-source","version":1,"meta":{"name":"No id"}}""")
        val error = assertFailsWith<TsiptvSourceRejectedException> { service.fetchLink(root) }
        assertEquals(TsiptvIssueCode.E_ID, error.report.primaryDocumentError)
        assertNull(db.getPlaylistById(playlistId))
    }

    @Test
    fun otherFormatsAreReturnedAsBytes() = runBlocking<Unit> {
        http.ok(m3u, m3uBody)
        val fetched = service.fetchLink(m3u)
        assertIs<TsiptvSourceService.LinkFetch.Other>(fetched)
        assertEquals(m3uBody, fetched.bytes.decodeToString())
    }

    @Test
    fun removeDeletesThePlaylistItsRowsHistoryAndOwnedAddons() = runBlocking<Unit> {
        http.ok(root, rootDoc("""{"id":"live","type":"m3u","url":"$m3u"}"""))
        http.ok(m3u, m3uBody)
        importFromLink()
        history.upsert(
            MediaHistoryRecord(
                sourceKind = MediaSourceKind.TSIPTV, sourceId = playlistId, itemType = "movie", itemId = "m", videoId = "m",
                title = "M", subtitle = null, posterUrl = null, season = null, episode = null, lastAddonId = null,
                lastBingeGroup = null, positionMs = 1, durationMs = 2, finished = false, updatedAt = 3,
            )
        )

        service.remove(playlistId)

        assertNull(db.getPlaylistById(playlistId))
        assertNull(db.tsiptvStore.getSource(playlistId))
        assertTrue(db.tsiptvStore.getIncludes(playlistId).isEmpty())
        assertTrue(db.getAllChannelsByPlayListId(playlistId).first().isEmpty())
        assertTrue(history.observeHistory().first().isEmpty())
        // Once when stored (keeping the current addons), once on removal.
        assertEquals(listOf(playlistId, playlistId), addons.removed)
    }

    @Test
    fun reimportFromTheSameLinkIsAnUpdateAndAnotherLinkAsksToReplace() = runBlocking<Unit> {
        http.ok(root, rootDoc(""))
        importFromLink()
        val again = (service.fetchLink(root) as TsiptvSourceService.LinkFetch.Source).preview
        assertTrue(again.isRefreshOfExisting)
        assertFalse(again.needsReplaceConfirmation)

        val file = service.preview(rootDoc("").encodeToByteArray(), rootUrl = null, fileName = "root.json")
        assertTrue(file.needsReplaceConfirmation)
        assertEquals("Root", file.existingName)
    }
}
