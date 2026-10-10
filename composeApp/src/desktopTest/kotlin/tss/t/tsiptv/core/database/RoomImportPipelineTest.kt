package tss.t.tsiptv.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.TestAssets
import tss.t.tsiptv.core.network.DownloadProgress
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.network.UploadProgress
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.usecase.playlist.ImportOutcome
import tss.t.tsiptv.usecase.playlist.PlaylistImporter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The importer against real Room: the single write transaction, channel_attributes on every
 * path (AC-K30), headers and DRM in the new columns, favourites and history kept.
 */
class RoomImportPipelineTest {

    private val room = Room.inMemoryDatabaseBuilder<AppDatabase> { AppDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .build()
    private val db = RoomIPTVDatabase(room)
    private var clock = 1_000_000L

    private val playlists = mapOf(
        "https://lists.example.com/reference.m3u" to TestAssets.read("kodi/reference.m3u"),
        "https://lists.example.com/drm.m3u" to TestAssets.read("kodi/drm.m3u"),
    )
    private val importer = PlaylistImporter(db, StaticNetwork(playlists), now = { clock })

    @AfterTest
    fun close() = room.close()

    private suspend fun snapshot(playlistId: String) = db.getAllChannelsByPlayListId(playlistId).first()
        .map { it to db.getChannelAttributes(it.id) }

    @Test
    fun importRefreshAndAutoRefreshStoreTheSameRows() = runBlocking {
        val url = "https://lists.example.com/reference.m3u"
        val result = assertIs<ImportOutcome.Imported>(importer.importFromUrl("Reference", url)).result
        val id = result.playlist.id
        val afterImport = snapshot(id)
        assertEquals(9, afterImport.size)
        assertEquals("val", afterImport.first { it.first.id == "channel-x" }.second["kodiprop:key"])
        assertEquals(listOf("Entertainment", "HD Channels"), db.getChannelById("channel-x~2")!!.groups)

        // A favourite and a play survive both refresh paths.
        db.insertChannel(db.getChannelById("channel-x")!!.copy(isFavorite = true))
        db.recordChannelPlay("channel-x", id, 5)

        importer.refresh(id)
        clock += 2 * 24 * 3_600_000L
        importer.refreshIfStale(id)

        val afterRefreshes = snapshot(id)
        assertEquals(
            afterImport.map { (c, a) -> c.copy(isFavorite = c.id == "channel-x") to a },
            afterRefreshes
        )
        assertEquals(1, db.getAllWatchedChannelsWithDetails(id).first().size)
        assertEquals(
            listOf("Entertainment", "HD Channels"),
            db.getAllCategoriesByPlayListId(id).map { it.name }.sorted()
        )
    }

    @Test
    fun twoPlaylistsWithTheSameEntriesKeepTheirChannels() = runBlocking {
        // QC r3: channel.id is the primary key alone, so the second import used to take the
        // first playlist's rows. Now the second copy is namespaced and both stay complete.
        val text = TestAssets.read("kodi/reference.m3u")
        val first = importer.importFromFile("First", "first.m3u", text.encodeToByteArray()).playlist.id
        val second = importer.importFromFile("Second", "second.m3u", text.encodeToByteArray()).playlist.id
        assertEquals(9, snapshot(first).size)
        assertEquals(9, snapshot(second).size)
        // Group categories (slugged titles) are kept by both too.
        val groups = db.getAllCategoriesByPlayListId(first).map { it.name }.sorted()
        assertEquals(groups, db.getAllCategoriesByPlayListId(second).map { it.name }.sorted())
        assertTrue(groups.isNotEmpty())
        // Attributes and the guide id follow the renamed rows.
        val copy = snapshot(second).first { it.first.guideId == "channel-x" && !it.first.id.contains('~') }
        assertEquals("val", copy.second["kodiprop:key"])
        // Refreshing / re-importing either one keeps both.
        importer.importFromFile("First", "first.m3u", text.encodeToByteArray())
        importer.importFromFile("Second", "second.m3u", text.encodeToByteArray())
        assertEquals(9, snapshot(first).size)
        assertEquals(9, snapshot(second).size)
    }

    @Test
    fun twoPlaylistsWithTheSameGuideKeepTheirOwnProgrammes() = runBlocking {
        // QC r4 N9: programme ids were "<channel>_<start>" in both, so parsing B's guide replaced A's.
        val text = TestAssets.read("kodi/reference.m3u")
        val a = importer.importFromFile("A", "a.m3u", text.encodeToByteArray()).playlist.id
        val b = importer.importFromFile("B", "b.m3u", text.encodeToByteArray()).playlist.id
        val guide = (0 until 4).map { i ->
            tss.t.tsiptv.core.parser.model.IPTVProgram(
                id = "channel-x_${1_000L + i}", channelId = "channel-x", title = "Show $i",
                startTime = clock - 3_600_000L + i * 600_000L, endTime = clock - 3_000_000L + i * 600_000L,
            )
        }
        db.deleteProgramsForPlaylist(a)
        db.insertPrograms(guide, a)
        db.deleteProgramsForPlaylist(b)
        db.insertPrograms(guide, b)
        assertEquals(4, db.getProgramsForChannel("channel-x", a).size)
        assertEquals(4, db.getProgramsForChannel("channel-x", b).size)
        assertEquals(db.countValidPrograms(a), db.countValidPrograms(b))
        // Re-parsing A (delete + insert) leaves B alone, and the other way round.
        db.deleteProgramsForPlaylist(a)
        db.insertPrograms(guide, a)
        assertEquals(4, db.getProgramsForChannel("channel-x", b).size)
        db.deleteProgramsForPlaylist(b)
        assertEquals(4, db.getProgramsForChannel("channel-x", a).size)
        assertEquals(0, db.getProgramsForChannel("channel-x", b).size)
        // The Programs tab rows carry their playlist, for the per-channel list.
        assertEquals(setOf(a), db.getChannelsWithValidProgramCounts(a, clock).map { it.playlistId }.toSet())
    }

    @Test
    fun relatedChannelsStayInTheirPlaylist() = runBlocking {
        val text = TestAssets.read("kodi/reference.m3u")
        val a = importer.importFromFile("A", "a.m3u", text.encodeToByteArray()).playlist.id
        importer.importFromFile("B", "b.m3u", text.encodeToByteArray())
        val channel = snapshot(a).first { it.first.categoryId != null }.first
        val related = db.getChannelsByCategory(channel.categoryId!!, a).first()
        assertTrue(related.isNotEmpty())
        assertTrue(related.all { it.playlistId == a })
    }

    @Test
    fun headersAndDrmArePersisted() = runBlocking {
        val result = assertIs<ImportOutcome.Imported>(
            importer.importFromUrl("DRM", "https://lists.example.com/drm.m3u")
        ).result
        val wv = db.getChannelById("wv")!!
        assertEquals(DrmSystem.WIDEVINE, wv.drm?.system)
        assertEquals("https://lic.example.com/wv", wv.drm?.licenseUrl)
        assertEquals("application/dash+xml", wv.mimeType)
        assertTrue(db.getChannelById("drm-in-url")!!.drm == null)
        assertEquals(10, result.channelCount)
    }

    /** QC round 1, item 3: the v4 re-parse renames channels; favourites and history follow. */
    @Test
    fun renamedChannelsKeepFavouriteAndHistory() = runBlocking {
        val url = "https://lists.example.com/renamed.m3u"
        val id = PlaylistImporter.urlPlaylistId(url)
        db.insertPlaylist(tss.t.tsiptv.core.model.Playlist(id, "List", url, lastUpdated = 0))
        db.insertChannels(
            listOf(
                tss.t.tsiptv.core.model.Channel("with_comma", "with comma", "https://cdn.example.com/1.m3u8", playlistId = id, isFavorite = true),
                tss.t.tsiptv.core.model.Channel("x", "X HD", "https://cdn.example.com/x-hd.m3u8", playlistId = id, isFavorite = true),
            )
        )
        db.recordChannelPlay("with_comma", id, 1)
        db.recordChannelPlay("x", id, 2)

        val importer = PlaylistImporter(
            db,
            StaticNetwork(
                mapOf(
                    url to "#EXTM3U\n#EXTINF:-1,Channel, with comma\nhttps://cdn.example.com/1.m3u8\n" +
                            "#EXTINF:-1 tvg-id=\"x\",X SD\nhttps://cdn.example.com/x-sd.m3u8\n" +
                            "#EXTINF:-1 tvg-id=\"x\",X HD\nhttps://cdn.example.com/x-hd.m3u8"
                )
            ),
            now = { clock },
        )
        importer.refresh(id)

        assertTrue(db.getChannelById("channel,_with_comma")!!.isFavorite)
        assertTrue(db.getChannelById("x~2")!!.isFavorite)
        assertEquals(false, db.getChannelById("x")!!.isFavorite)
        assertEquals(
            setOf("channel,_with_comma", "x~2"),
            db.getAllWatchedChannelsWithDetails(id).first().map { it.channelId }.toSet()
        )
    }

    /** QC round 2, item 24: moving onto an id that already has history merges, leaves no temp row. */
    @Test
    fun historyMoveMergesAndLeavesNoTemporaryRows() = runBlocking {
        val url = "https://lists.example.com/merge.m3u"
        val id = PlaylistImporter.urlPlaylistId(url)
        db.insertPlaylist(tss.t.tsiptv.core.model.Playlist(id, "List", url, lastUpdated = 0))
        db.insertChannels(
            listOf(
                tss.t.tsiptv.core.model.Channel("old", "Old", "https://cdn.example.com/u1.m3u8", playlistId = id),
                tss.t.tsiptv.core.model.Channel("x", "X HD", "https://cdn.example.com/hd.m3u8", playlistId = id),
                tss.t.tsiptv.core.model.Channel("x~2", "X SD", "https://cdn.example.com/sd.m3u8", playlistId = id),
            )
        )
        db.recordChannelPlay("old", id, 10)
        db.recordChannelPlay("y", id, 20) // orphan history of a channel that left the list
        db.recordChannelPlay("x", id, 30)
        db.recordChannelPlay("x", id, 31) // x: 2 plays, x~2: 1 play
        db.recordChannelPlay("x~2", id, 40)

        val importer = PlaylistImporter(
            db,
            StaticNetwork(
                mapOf(
                    url to "#EXTM3U\n#EXTINF:-1 tvg-id=\"y\",Y\nhttps://cdn.example.com/u1.m3u8\n" +
                            // The feeds swapped places: ids swap, history follows the URLs.
                            "#EXTINF:-1 tvg-id=\"x\",X SD\nhttps://cdn.example.com/sd.m3u8\n" +
                            "#EXTINF:-1 tvg-id=\"x\",X HD\nhttps://cdn.example.com/hd.m3u8"
                )
            ),
            now = { clock },
        )
        importer.refresh(id)

        val history = room.channelHistoryDao().getAllPlayedChannelsInPlaylist(id).first()
        assertTrue(history.none { it.channelId.startsWith("::tsiptv-moving::") })
        val byId = history.associateBy { it.channelId }
        assertEquals(setOf("y", "x", "x~2"), byId.keys)
        assertEquals(2, byId.getValue("y").playCount, "old's play merged into y's")
        assertEquals(1, byId.getValue("x").playCount, "x is now the SD feed")
        assertEquals(2, byId.getValue("x~2").playCount, "x~2 is now the HD feed")
    }

    @Test
    fun categoryFilterTreatsLikeWildcardsLiterally() = runBlocking {
        val url = "https://lists.example.com/wild.m3u"
        val importer = PlaylistImporter(
            db,
            StaticNetwork(
                mapOf(
                    url to "#EXTM3U\n#EXTINF:-1 group-title=\"Main;100%_Fun\",A\nhttps://cdn.example.com/a.m3u8\n" +
                            "#EXTINF:-1 group-title=\"Main;100xxFun\",B\nhttps://cdn.example.com/b.m3u8"
                )
            ),
            now = { clock },
        )
        importer.importFromUrl("Wild", url)
        assertEquals(listOf("A"), db.getChannelsByCategory("100%_Fun").first().map { it.name })
    }

    private class StaticNetwork(private val responses: Map<String, String>) : NetworkClient {
        override suspend fun get(url: String, headers: Map<String, String>) = responses.getValue(url)
        override suspend fun getManualGzipIfNeed(url: String, headers: Map<String, String>) = responses.getValue(url)
        override suspend fun post(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun put(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun delete(url: String, headers: Map<String, String>) = error("unused")
        override suspend fun downloadFile(url: String, headers: Map<String, String>): Flow<DownloadProgress> =
            error("unused")

        override suspend fun uploadFile(
            url: String, fileBytes: ByteArray, fileName: String, mimeType: String, headers: Map<String, String>,
        ): Flow<UploadProgress> = error("unused")
    }
}
