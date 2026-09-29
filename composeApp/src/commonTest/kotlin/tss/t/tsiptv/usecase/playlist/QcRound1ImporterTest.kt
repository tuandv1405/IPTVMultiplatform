package tss.t.tsiptv.usecase.playlist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.database.InMemoryIPTVDatabase
import tss.t.tsiptv.core.database.NewChannel
import tss.t.tsiptv.core.database.StoredChannel
import tss.t.tsiptv.core.database.matchPreviousChannels
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.network.DownloadProgress
import tss.t.tsiptv.core.network.HttpStatusException
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.network.UploadProgress
import tss.t.tsiptv.core.parser.model.IPTVFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Importer regressions from F1 QC round 1: items 3, 8, 11. */
class QcRound1ImporterTest {

    private class Network(val responses: MutableMap<String, String>) : NetworkClient {
        override suspend fun get(url: String, headers: Map<String, String>) = serve(url)
        override suspend fun getManualGzipIfNeed(url: String, headers: Map<String, String>) = serve(url)
        private fun serve(url: String) = responses[url] ?: throw HttpStatusException(503)
        override suspend fun post(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun put(url: String, body: String, headers: Map<String, String>) = error("unused")
        override suspend fun delete(url: String, headers: Map<String, String>) = error("unused")
        override suspend fun downloadFile(url: String, headers: Map<String, String>): Flow<DownloadProgress> = error("unused")
        override suspend fun uploadFile(
            url: String, fileBytes: ByteArray, fileName: String, mimeType: String, headers: Map<String, String>,
        ): Flow<UploadProgress> = error("unused")
    }

    private val url = "https://lists.example.com/list.m3u"
    private val db = InMemoryIPTVDatabase()
    private val network = Network(mutableMapOf())
    private var clock = 10_000_000L
    private val importer = PlaylistImporter(db, network, now = { clock })
    private val playlistId = PlaylistImporter.urlPlaylistId(url)

    @Test
    fun matchingPrefersIdThenSiblingThenLegacyId() {
        val previous = listOf(StoredChannel("x", "u3"), StoredChannel("with_comma", "u9"), StoredChannel("y", "u5"))
        val fresh = listOf(
            NewChannel("x", "u2", null),
            NewChannel("x~2", "u3", null),
            NewChannel("channel,_with_comma", "u1", "with_comma"),
            NewChannel("y", "u6", null),
        )
        assertEquals(
            mapOf("x~2" to "x", "y" to "y", "channel,_with_comma" to "with_comma"),
            matchPreviousChannels(previous, fresh)
        )
    }

    // QC round 2, item 20: a rotated token link must not hand one channel's data to another.
    @Test
    fun rotatedTokenLinkDoesNotMoveFavouriteToAnotherChannel() = runBlocking {
        db.insertPlaylist(Playlist(playlistId, "List", url, lastUpdated = 0))
        db.insertChannels(
            listOf(
                Channel("tok", "Token channel", "https://cdn.example.com/s.m3u8?token=NEW", playlistId = playlistId, isFavorite = true),
                Channel("plain", "Plain", "https://cdn.example.com/s.m3u8?c=plain", playlistId = playlistId),
            )
        )
        db.recordChannelPlay("tok", playlistId, 1)
        network.responses[url] = """
            #EXTM3U
            #EXTINF:-1 tvg-id="tok",Token channel
            https://cdn.example.com/s.m3u8?token=NEW2
            #EXTINF:-1 tvg-id="plain",Plain
            https://cdn.example.com/s.m3u8?c=plain
            #EXTINF:-1 tvg-id="intruder",Intruder
            https://cdn.example.com/s.m3u8?token=NEW
        """.trimIndent()

        importer.refresh(playlistId)

        assertTrue(db.getChannelById("tok")!!.isFavorite)
        assertEquals(false, db.getChannelById("intruder")!!.isFavorite)
        assertEquals(
            setOf("tok"),
            db.getAllPlayedChannelsInPlaylist(playlistId).first().map { it.channelId }.toSet()
        )
    }

    /**
     * QC's K29 repro: rows as the pre-F1 parser stored them (ids from tvg-name, the last comma,
     * a mis-cased tvg-ID, the last duplicate, the old token), each a favourite with history.
     */
    @Test
    fun k29UpgradeKeepsEveryFavouriteOnItsOwnChannel() = runBlocking {
        db.insertPlaylist(Playlist(playlistId, "List", url, lastUpdated = 0))
        val old = listOf(
            "cnn_us" to "?c=cnn",
            "weather" to "?c=weather",
            "mixed" to "?c=mixed",
            "dup" to "?c=dupB",
            "tok" to "?token=NEW",
        )
        db.insertChannels(old.map { (id, query) ->
            Channel(id, id, "https://cdn.example.com/s.m3u8$query", playlistId = playlistId, isFavorite = true)
        })
        old.forEachIndexed { i, (id, _) -> db.recordChannelPlay(id, playlistId, i.toLong()) }
        network.responses[url] = tss.t.tsiptv.TestAssets.read("kodi/qc-k29.m3u")

        importer.refresh(playlistId)

        val favourites = db.getAllChannelsByPlayListId(playlistId).first().filter { it.isFavorite }
        assertEquals(
            mapOf(
                "cnn_us" to "?c=cnn",
                "news,_weather" to "?c=weather",
                "Mixed.Case" to "?c=mixed",
                "dup~2" to "?c=dupB",
                "tok" to "?token=NEW2",
            ),
            favourites.associate { it.id to it.url.substringAfter("s.m3u8") }
        )
        assertEquals(
            favourites.map { it.id }.toSet(),
            db.getAllPlayedChannelsInPlaylist(playlistId).first().map { it.channelId }.toSet()
        )
    }

    @Test
    fun sharedPlaceholderUrlsNeverMatchByUrl() {
        val placeholder = "https://example.com/offline.m3u8"
        val previous = listOf(StoredChannel("a", placeholder), StoredChannel("b", placeholder))
        assertEquals(
            emptyMap(),
            matchPreviousChannels(previous, listOf(NewChannel("c", placeholder, null), NewChannel("d", placeholder, null)))
        )
        assertEquals(
            mapOf("a" to "a"),
            matchPreviousChannels(previous, listOf(NewChannel("a", placeholder, null), NewChannel("c", placeholder, null)))
        )
        // A unique link whose stored id vanished is a rename (new parser rules): it matches.
        assertEquals(
            mapOf("Mixed.Case" to "mixed"),
            matchPreviousChannels(
                listOf(StoredChannel("mixed", "u1")),
                listOf(NewChannel("Mixed.Case", "u1", null))
            )
        )
    }

    /** A pre-F1 database: ids from the old rules, a favourite and history on them. */
    private suspend fun seedPreF1() {
        db.insertPlaylist(Playlist(playlistId, "List", url, lastUpdated = 0))
        db.insertChannels(
            listOf(
                // Old parser: name cut at the last comma → " with comma" → "with_comma".
                Channel("with_comma", "with comma", "https://cdn.example.com/1.m3u8", playlistId = playlistId, isFavorite = true),
                // Duplicate tvg-id: the old REPLACE left the LAST occurrence under the plain id.
                Channel("x", "X HD", "https://cdn.example.com/x-hd.m3u8", playlistId = playlistId, isFavorite = true),
            )
        )
        db.recordChannelPlay("with_comma", playlistId, 1)
        db.recordChannelPlay("x", playlistId, 2)
    }

    @Test
    fun reparseKeepsFavouritesAndHistoryWhenIdsChange() = runBlocking {
        seedPreF1()
        network.responses[url] = """
            #EXTM3U
            #EXTINF:-1,Channel, with comma
            https://cdn.example.com/1.m3u8
            #EXTINF:-1 tvg-id="x",X SD
            https://cdn.example.com/x-sd.m3u8
            #EXTINF:-1 tvg-id="x",X HD
            https://cdn.example.com/x-hd.m3u8
        """.trimIndent()

        importer.refresh(playlistId)

        val channels = db.getAllChannelsByPlayListId(playlistId).first().associateBy { it.id }
        assertTrue(channels.getValue("channel,_with_comma").isFavorite)
        assertTrue(channels.getValue("x~2").isFavorite, "the HD feed kept its favourite")
        assertEquals(false, channels.getValue("x").isFavorite)
        val history = db.getAllPlayedChannelsInPlaylist(playlistId).first().map { it.channelId }.toSet()
        assertEquals(setOf("channel,_with_comma", "x~2"), history)
    }

    // Item 8
    @Test
    fun anEmptyOrFailedRefreshKeepsTheChannels() = runBlocking {
        network.responses[url] = "#EXTM3U\n#EXTINF:-1,A\nhttps://cdn.example.com/a.m3u8"
        importer.importFromUrl("List", url)

        network.responses[url] = "#EXTM3U\n"
        assertFailsWith<PlaylistImportException> { importer.refresh(playlistId) }
        assertEquals(1, db.getAllChannelsByPlayListId(playlistId).first().size)

        network.responses.remove(url) // server answers 503
        assertFailsWith<HttpStatusException> { importer.refresh(playlistId) }
        clock += 3 * 24 * 3_600_000L
        importer.refreshIfStale(playlistId)
        assertEquals(1, db.getAllChannelsByPlayListId(playlistId).first().size)
    }

    // Item 11
    @Test
    fun byteOrderMarkBeforeJsonIsAccepted() = runBlocking {
        network.responses[url] = "﻿[{\"id\":\"a\",\"name\":\"A\",\"url\":\"https://cdn.example.com/a.m3u8\"}]"
        val result = assertIs<ImportOutcome.Imported>(importer.importFromUrl("List", url)).result
        assertEquals(IPTVFormat.JSON, result.format)
        assertEquals(1, result.channelCount)
    }

    @Test
    fun truncatedGzipFileIsAReadError() = runBlocking {
        val error = assertFailsWith<PlaylistImportException> {
            importer.importFromFile("", "list.m3u.gz", byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0))
        }
        assertEquals(ImportError.FILE_READ, error.error)
    }
}
