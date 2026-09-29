package tss.t.tsiptv.usecase.playlist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.TestAssets
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.InMemoryIPTVDatabase
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.core.network.DownloadProgress
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.network.UploadProgress
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.SkipReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The one import pipeline: AC-K23 … AC-K28 and AC-K30 against the in-memory database. */
class PlaylistImporterTest {

    private class FakeNetwork(val responses: MutableMap<String, String> = mutableMapOf()) : NetworkClient {
        val requested = mutableListOf<String>()
        private fun serve(url: String): String {
            requested += url
            return responses[url] ?: throw IllegalStateException("HTTP 404")
        }

        override suspend fun get(url: String, headers: Map<String, String>) = serve(url)
        override suspend fun getManualGzipIfNeed(url: String, headers: Map<String, String>) = serve(url)
        override suspend fun post(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun put(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun delete(url: String, headers: Map<String, String>) = error("unused")
        override suspend fun downloadFile(url: String, headers: Map<String, String>): Flow<DownloadProgress> =
            error("unused")

        override suspend fun uploadFile(
            url: String, fileBytes: ByteArray, fileName: String, mimeType: String, headers: Map<String, String>,
        ): Flow<UploadProgress> = error("unused")
    }

    private var clock = 1_000_000L
    private val db = InMemoryIPTVDatabase()
    private val network = FakeNetwork()
    private val importer = PlaylistImporter(db, network, now = { clock })

    private suspend fun IPTVDatabase.channels(playlistId: String): List<Channel> =
        getAllChannelsByPlayListId(playlistId).first()

    private fun imported(outcome: ImportOutcome): ImportResult = assertIs<ImportOutcome.Imported>(outcome).result

    @Test
    fun importStoresChannelsAttributesAndSkips() = runBlocking {
        network.responses["https://lists.example.com/skips.m3u"] = TestAssets.read("kodi/skips.m3u")
        val result = imported(importer.importFromUrl("Skips", "https://lists.example.com/skips.m3u"))

        assertEquals(2, result.channelCount)
        assertEquals(mapOf(SkipReason.KODI_ADDON to 1, SkipReason.WEB_SCRAPING to 1, SkipReason.NO_URL to 2), result.skipped)
        assertEquals(listOf("ok-1", "ok-2"), db.channels(result.playlist.id).map { it.id })
        assertEquals(listOf(0, 1), db.channels(result.playlist.id).map { it.sortIndex })
        assertEquals("ok-1", db.getChannelAttributes("ok-1")["tvg-id"])
    }

    // AC-K23
    @Test
    fun jsonArrayImports() = runBlocking {
        network.responses["https://lists.example.com/a.json"] = TestAssets.read("kodi/array.json")
        val result = imported(importer.importFromUrl("Array", "https://lists.example.com/a.json"))
        assertEquals(IPTVFormat.JSON, result.format)
        assertEquals(1, result.channelCount)
    }

    @Test
    fun iptvOrgStreamsCarryHeaders() = runBlocking {
        network.responses["https://lists.example.com/streams.json"] = TestAssets.read("kodi/iptv-org-streams.json")
        val result = imported(importer.importFromUrl("iptv-org", "https://lists.example.com/streams.json"))
        assertEquals(IPTVFormat.JSON_IPTV_ORG, result.format)
        val news = db.channels(result.playlist.id).first { it.id == "ExampleNews.us" }
        assertEquals("Example News HD", news.name)
        assertEquals(
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Referer" to "https://example.com/"),
            news.headers
        )
    }

    // AC-K24
    @Test
    fun htmlPageAndUnknownContentAreTypedErrors() = runBlocking {
        network.responses["https://code.example.com/view"] = TestAssets.read("kodi/page.html")
        network.responses["https://code.example.com/text"] = "just some words"
        network.responses["https://code.example.com/source"] = """{"format":"tsiptv-source","version":1}"""
        assertEquals(
            ImportError.HTML_PAGE,
            assertFailsWith<PlaylistImportException> { importer.importFromUrl("x", "https://code.example.com/view") }.error
        )
        assertEquals(
            ImportError.UNKNOWN_FORMAT,
            assertFailsWith<PlaylistImportException> { importer.importFromUrl("x", "https://code.example.com/text") }.error
        )
        assertEquals(
            ImportError.NEEDS_UPDATE,
            assertFailsWith<PlaylistImportException> { importer.importFromUrl("x", "https://code.example.com/source") }.error
        )
    }

    // AC-K25
    @Test
    fun hlsManifestAsksAndThenAddsOneChannel() = runBlocking {
        val url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        network.responses[url] = TestAssets.read("kodi/hls-master.m3u8")
        val outcome = importer.importFromUrl("Mux test", url)
        assertEquals(ImportOutcome.SingleStream("Mux test", url), outcome)
        assertTrue(db.getAllPlaylists().first().isEmpty(), "nothing is stored before the user agrees")

        val result = importer.importSingleStream("Mux test", url)
        val channel = db.channels(result.playlist.id).single()
        assertEquals(url, channel.url)
        assertEquals("Mux test", channel.name)

        // Refreshing it keeps the one channel.
        assertEquals(1, importer.refresh(result.playlist.id).channelCount)
    }

    // AC-K26
    @Test
    fun strmFilesImportOrRefuseAddons() = runBlocking {
        val result = importer.importFromFile(
            name = "",
            displayName = "My Channel.strm",
            bytes = TestAssets.read("kodi/channel.strm").encodeToByteArray(),
        )
        assertEquals("My Channel", result.playlist.name)
        assertEquals(PlaylistSourceType.FILE, result.playlist.sourceType)
        assertEquals("file:My Channel.strm", result.playlist.url)
        val channel = db.channels(result.playlist.id).single()
        assertEquals("My Channel", channel.name)
        assertEquals(mapOf("User-Agent" to "TSIPTV-QC/1.0"), channel.headers)

        val error = assertFailsWith<PlaylistImportException> {
            importer.importFromFile("", "addon.strm", TestAssets.read("kodi/addon.strm").encodeToByteArray())
        }
        assertEquals(ImportError.STRM_KODI_ADDON, error.error)
        assertEquals(1, db.getAllPlaylists().first().size, "the refused file imports nothing")
    }

    // AC-K27
    @Test
    fun oversizedFileIsRefused() = runBlocking {
        val error = assertFailsWith<PlaylistImportException> {
            importer.importFromFile("big", "big.m3u", ByteArray(PlaylistImporter.MAX_FILE_BYTES + 1))
        }
        assertEquals(ImportError.FILE_TOO_LARGE, error.error)
    }

    // AC-K28
    @Test
    fun refreshingAFilePlaylistMakesNoNetworkCall() = runBlocking {
        val result = importer.importFromFile("", "list.m3u", TestAssets.read("kodi/headers.m3u").encodeToByteArray())
        val error = assertFailsWith<PlaylistImportException> { importer.refresh(result.playlist.id) }
        assertEquals(ImportError.FILE_REFRESH, error.error)
        clock += 10 * 24 * 3_600_000L
        assertEquals(result.playlist.id, importer.refreshIfStale(result.playlist.id)?.id)
        assertTrue(network.requested.isEmpty())
    }

    // AC-K30
    @Test
    fun importRefreshAndAutoRefreshStoreTheSameRows() = runBlocking {
        val url = "https://lists.example.com/reference.m3u"
        network.responses[url] = TestAssets.read("kodi/reference.m3u")
        val id = imported(importer.importFromUrl("Reference", url)).playlist.id

        suspend fun snapshot() = db.channels(id).map { it to db.getChannelAttributes(it.id) }
        val afterImport = snapshot()

        importer.refresh(id)
        val afterRefresh = snapshot()

        clock += 2 * 24 * 3_600_000L
        importer.refreshIfStale(id)
        val afterAutoRefresh = snapshot()

        assertEquals(afterImport, afterRefresh)
        assertEquals(afterImport, afterAutoRefresh)
        assertEquals(3, network.requested.size)
    }

    @Test
    fun favouritesSurviveARefresh() = runBlocking {
        val url = "https://lists.example.com/reference.m3u"
        network.responses[url] = TestAssets.read("kodi/reference.m3u")
        val id = imported(importer.importFromUrl("Reference", url)).playlist.id
        db.insertChannel(db.getChannelById("channel-x")!!.copy(isFavorite = true))

        importer.refresh(id)
        assertTrue(db.getChannelById("channel-x")!!.isFavorite)
    }

    @Test
    fun staleOnlyAfterOneDay() = runBlocking {
        val url = "https://lists.example.com/reference.m3u"
        network.responses[url] = TestAssets.read("kodi/reference.m3u")
        val id = imported(importer.importFromUrl("Reference", url)).playlist.id
        clock += 3_600_000L
        importer.refreshIfStale(id)
        assertEquals(1, network.requested.size)

        // A failing refresh keeps the stored copy.
        network.responses.remove(url)
        clock += 2 * 24 * 3_600_000L
        assertEquals(id, importer.refreshIfStale(id)?.id)
        assertEquals(9, db.channels(id).size)
    }

    @Test
    fun guidesAreFetchedEachOnItsOwnAndMerged() = runBlocking {
        fun guide(channel: String, start: String) = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="$start +0000" stop="20300101130000 +0000" channel="$channel"><title>P</title></programme>
            </tv>
        """.trimIndent()
        network.responses["https://a.example/1.xml"] = guide("a", "20300101120000")
        network.responses["https://a.example/3.xml"] = guide("a", "20300101120000") // duplicate
        network.responses["https://a.example/4.xml"] = guide("b", "20300101120000")

        val count = importer.fetchEpg(
            "p",
            listOf("https://a.example/1.xml", "https://a.example/2.xml", "https://a.example/3.xml", "https://a.example/4.xml")
        )
        assertEquals(2, count)
        assertEquals(1, db.getProgramsForChannel("a").size)
        assertEquals(1, db.getProgramsForChannel("b").size)
        assertEquals(null, importer.fetchEpg("p", listOf("https://a.example/404.xml")))
    }

    @Test
    fun atMostFiveGuidesAreFetched() = runBlocking {
        importer.fetchEpg("p", (1..8).map { "https://a.example/$it.xml" })
        assertEquals(PlaylistImporter.MAX_EPG_URLS, network.requested.size)
    }
}
