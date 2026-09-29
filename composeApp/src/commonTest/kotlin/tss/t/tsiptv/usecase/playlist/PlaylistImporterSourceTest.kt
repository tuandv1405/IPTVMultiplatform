package tss.t.tsiptv.usecase.playlist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.database.InMemoryIPTVDatabase
import tss.t.tsiptv.core.network.DownloadProgress
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.network.UploadProgress
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.stremio.InMemoryStremioStores
import tss.t.tsiptv.core.tsiptv.AddonProbe
import tss.t.tsiptv.core.tsiptv.SourceAddonBridge
import tss.t.tsiptv.core.tsiptv.SourceFetchRequest
import tss.t.tsiptv.core.tsiptv.SourceFetchResult
import tss.t.tsiptv.core.tsiptv.SourceHttpTransport
import tss.t.tsiptv.core.tsiptv.TsiptvSourceService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** F3 step 2: "Add by link" / file routes TS IPTV Sources to the preview; other formats keep the F1 path. */
class PlaylistImporterSourceTest {

    private object NoNetwork : NetworkClient {
        override suspend fun get(url: String, headers: Map<String, String>) = error("unused")
        override suspend fun getManualGzipIfNeed(url: String, headers: Map<String, String>) = error("unused")
        override suspend fun post(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun put(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun delete(url: String, headers: Map<String, String>) = error("unused")
        override suspend fun downloadFile(url: String, headers: Map<String, String>): Flow<DownloadProgress> = error("unused")
        override suspend fun uploadFile(
            url: String, fileBytes: ByteArray, fileName: String, mimeType: String, headers: Map<String, String>,
        ): Flow<UploadProgress> = error("unused")
    }

    private val bodies = HashMap<String, String>()
    private val http = SourceHttpTransport { request: SourceFetchRequest ->
        val body = bodies[request.url]?.encodeToByteArray() ?: return@SourceHttpTransport SourceFetchResult.Failed("http_404", 404)
        request.sniffCap?.invoke(body.copyOf(minOf(body.size, SourceFetchRequest.SNIFF_BYTES)))
        SourceFetchResult.Ok(body, null, null)
    }
    private val addons = object : SourceAddonBridge {
        override suspend fun probe(manifestUrl: String): AddonProbe = AddonProbe.Failed("unreachable")
        override suspend fun install(probe: AddonProbe.Ready, ownerPlaylistId: String) = probe.addonId
        override suspend fun removeOwned(ownerPlaylistId: String, keep: Set<String>) = Unit
    }
    private val db = InMemoryIPTVDatabase()
    private val service = TsiptvSourceService(db, http, addons, InMemoryStremioStores(), nowMs = { 1L })
    private val importer = PlaylistImporter(db, NoNetwork, now = { 1L }, sources = service)

    private val source = """{"format":"tsiptv-source","version":1,"id":"qa.src","meta":{"name":"QA"},
        "channels":[{"id":"a","name":"A","url":"https://cdn.example.com/a.m3u8"}]}"""

    @Test
    fun sourceLinkBecomesAPreviewAndNothingIsStored() = runBlocking<Unit> {
        bodies["https://src.example.com/qa.json"] = source
        val outcome = importer.importFromUrl("", "https://src.example.com/qa.json")
        val preview = assertIs<ImportOutcome.SourcePreview>(outcome).preview
        assertEquals(1, preview.tvCount)
        assertTrue(db.getAllPlaylists().first().isEmpty())
    }

    @Test
    fun otherLinksStillImportThroughTheF1Parsers() = runBlocking<Unit> {
        bodies["https://lists.example.com/a.m3u"] = "#EXTM3U\n#EXTINF:-1,One\nhttps://cdn.example.com/1.m3u8\n"
        val result = assertIs<ImportOutcome.Imported>(importer.importFromUrl("List", "https://lists.example.com/a.m3u")).result
        assertEquals(1, result.channelCount)
        assertEquals(IPTVFormat.M3U, result.format)
    }

    @Test
    fun sourceFileBecomesAPreview() = runBlocking<Unit> {
        val outcome = importer.importFileOutcome("", "qa.tsiptv.json", source.encodeToByteArray())
        assertIs<ImportOutcome.SourcePreview>(outcome)
    }

    @Test
    fun refreshOfAStoredSourceGoesThroughTheService() = runBlocking<Unit> {
        bodies["https://src.example.com/qa.json"] = source
        val preview = (importer.importFromUrl("", "https://src.example.com/qa.json") as ImportOutcome.SourcePreview).preview
        val stored = service.store(service.resolve(preview), adultConfirmed = false)
        val refreshed = importer.refresh(stored.playlist.id)
        assertEquals(IPTVFormat.TSIPTV_SOURCE, refreshed.format)
        assertEquals(1, refreshed.channelCount)
    }
}
