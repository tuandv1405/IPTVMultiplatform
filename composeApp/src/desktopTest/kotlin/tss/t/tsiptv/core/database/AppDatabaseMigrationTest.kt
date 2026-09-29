package tss.t.tsiptv.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v3 → v4 is a release gate: with `fallbackToDestructiveMigration(true)`, a migration that
 * fails silently deletes every user's playlists and history (AC-K29).
 */
class AppDatabaseMigrationTest {

    private val dir: Path = Files.createTempDirectory("tsiptv-migration")
    private val dbFile: File = dir.resolve("migration.db").toFile()

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = schemaDirectory(),
        databasePath = dbFile.toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
        databaseFactory = { AppDatabaseConstructor.initialize() },
    )

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun migrate3To4KeepsUserDataAndMarksUrlPlaylistsStale() {
        helper.createDatabase(3).use { v3 ->
            v3.execSQL(
                "INSERT INTO playlists (id, name, url, lastUpdated, format, epgUrl) " +
                        "VALUES ('p1', 'My list', 'https://lists.example.com/a.m3u', 1700000000000, 'M3U', 'https://a.example/g.xml')"
            )
            v3.execSQL("INSERT INTO categories (id, name, playlistId) VALUES ('news', 'News', 'p1')")
            v3.execSQL(
                "INSERT INTO channel (id, name, url, logoUrl, categoryId, playlistId, isFavorite, lastWatched) " +
                        "VALUES ('ch1', 'Channel 1', 'https://cdn.example.com/1.m3u8', NULL, 'News', 'p1', 1, 1700000000001)"
            )
            v3.execSQL("INSERT INTO channel_attributes (channelId, attrKey, attrValue) VALUES ('ch1', 'tvg-id', 'ch1')")
            v3.execSQL(
                "INSERT INTO channel_history (channelId, playlistId, lastPlayedTimestamp, totalPlayedTimeMs, playCount, currentPositionMs, totalDurationMs) " +
                        "VALUES ('ch1', 'p1', 1700000000002, 60000, 2, 0, 0)"
            )
        }

        helper.runMigrationsAndValidate(4).use { v4 ->
            assertEquals(listOf("0|URL|"), v4.rows("SELECT lastUpdated, sourceType, epgUrlsJson FROM playlists"))
            assertEquals(
                listOf("Channel 1|1|1700000000001|0|0|0||"),
                v4.rows("SELECT name, isFavorite, lastWatched, isRadio, isVod, sortIndex, headersJson, drmJson FROM channel")
            )
            assertEquals(listOf("2"), v4.rows("SELECT playCount FROM channel_history"))
            assertEquals(listOf("tvg-id"), v4.rows("SELECT attrKey FROM channel_attributes"))
            assertEquals(listOf("News"), v4.rows("SELECT name FROM categories"))
        }

        // The app's own DAOs read the migrated rows.
        val db = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath) { AppDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            runBlocking {
                val iptv = RoomIPTVDatabase(db)
                val channel = iptv.getChannelById("ch1")!!
                assertTrue(channel.isFavorite)
                assertEquals(listOf("News"), channel.groups)
                assertTrue(channel.headers.isEmpty())
                assertNull(channel.drm)
                assertEquals(0L, iptv.getPlaylistById("p1")!!.lastUpdated)
                assertEquals(1, iptv.getAllWatchedChannelsWithDetails("p1").first().size)
            }
        } finally {
            db.close()
        }
    }

    /** F2 (AC-S24): v4 → v5 adds `stremio_addons` and `media_history` and keeps every F1 row. */
    @Test
    fun migrate4To5KeepsF1DataAndAddsAddonTables() {
        helper.createDatabase(4).use { v4 ->
            v4.execSQL(
                "INSERT INTO playlists (id, name, url, lastUpdated, format, epgUrl, sourceType, epgUrlsJson) " +
                    "VALUES ('p1', 'My list', 'https://lists.example.com/a.m3u', 1700000000000, 'M3U', NULL, 'FILE', '[\"https://a.example/g.xml\"]')"
            )
            v4.execSQL("INSERT INTO categories (id, name, playlistId) VALUES ('news', 'News', 'p1')")
            v4.execSQL(
                "INSERT INTO channel (id, name, url, logoUrl, categoryId, playlistId, isFavorite, lastWatched, groupsJson, " +
                    "isRadio, isVod, headersJson, mimeType, drmJson, sortIndex) VALUES ('ch1', 'Channel 1', " +
                    "'https://cdn.example.com/1.mpd', NULL, 'News', 'p1', 1, 1700000000001, '[\"News\"]', 0, 1, " +
                    "'{\"User-Agent\":\"UA\"}', 'application/dash+xml', NULL, 7)"
            )
            v4.execSQL("INSERT INTO channel_attributes (channelId, attrKey, attrValue) VALUES ('ch1', 'tvg-id', 'ch1')")
            v4.execSQL(
                "INSERT INTO programs (id, channelId, title, description, startTime, endTime, category, playlistId) " +
                    "VALUES ('pr1', 'ch1', 'News at 9', NULL, 1700000000000, 1700003600000, NULL, 'p1')"
            )
            v4.execSQL(
                "INSERT INTO channel_history (channelId, playlistId, lastPlayedTimestamp, totalPlayedTimeMs, playCount, currentPositionMs, totalDurationMs) " +
                    "VALUES ('ch1', 'p1', 1700000000002, 60000, 3, 120000, 3600000)"
            )
        }

        helper.runMigrationsAndValidate(5).use { v5 ->
            assertEquals(listOf("My list|FILE"), v5.rows("SELECT name, sourceType FROM playlists"))
            assertEquals(listOf("Channel 1|1|1|7|{\"User-Agent\":\"UA\"}"), v5.rows("SELECT name, isFavorite, isVod, sortIndex, headersJson FROM channel"))
            assertEquals(listOf("3|120000"), v5.rows("SELECT playCount, currentPositionMs FROM channel_history"))
            assertEquals(listOf("News at 9"), v5.rows("SELECT title FROM programs"))
            assertEquals(listOf("0"), v5.rows("SELECT COUNT(*) FROM stremio_addons"))
            assertEquals(listOf("0"), v5.rows("SELECT COUNT(*) FROM media_history"))
            // Column defaults of the new tables (AutoMigration uses the entity's @ColumnInfo defaults).
            v5.execSQL(
                "INSERT INTO stremio_addons (addonId, transportUrlEnc, transportHost, manifestJson, name, version, logoUrl, " +
                    "sortOrder, ownerSourceId, addedAt, lastFetchedAt, lastError) VALUES ('a', 'v1:x', 'h', '{}', 'A', '1', NULL, 0, NULL, 1, 1, NULL)"
            )
            assertEquals(listOf("1|0|0|0|0"), v5.rows("SELECT enabled, isAdult, isP2p, failCount, blocked FROM stremio_addons"))
        }

        val db = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath) { AppDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            runBlocking {
                val iptv = RoomIPTVDatabase(db)
                val channel = iptv.getChannelById("ch1")!!
                assertEquals(mapOf("User-Agent" to "UA"), channel.headers)
                assertTrue(channel.isFavorite)
                assertEquals(1, iptv.getAllWatchedChannelsWithDetails("p1").first().size)

                // The new stores work on the migrated database, including the history cascade on removal.
                val stores = iptv.stremioStores
                assertEquals(listOf("a"), stores.getAddons().map { it.addonId })
                val record = tss.t.tsiptv.core.stremio.MediaHistoryRecord(
                    sourceKind = "STREMIO", sourceId = "a", itemType = "movie", itemId = "m", videoId = "m",
                    title = "M", subtitle = null, posterUrl = null, season = null, episode = null, lastAddonId = "a",
                    lastBingeGroup = null, positionMs = 1, durationMs = 2, finished = false, updatedAt = 3,
                )
                stores.upsert(record)
                stores.upsert(record.copy(positionMs = 99, updatedAt = 4)) // same unique key: updated in place
                assertEquals(listOf(99L), stores.observeHistory().first().map { it.positionMs })
                stores.deleteAddon("a")
                assertTrue(stores.getAddons().isEmpty())
                assertTrue(stores.observeHistory().first().isEmpty())
            }
        } finally {
            db.close()
        }
    }

    /** F3 (AC-T25): v5 → v6 adds the source tables and channel columns and keeps every v5 row. */
    @Test
    fun migrate5To6KeepsEveryV5RowAndAddsSourceTables() {
        helper.createDatabase(5).use { v5 ->
            v5.execSQL(
                "INSERT INTO playlists (id, name, url, lastUpdated, format, epgUrl, sourceType, epgUrlsJson) " +
                    "VALUES ('p1', 'My list', 'https://lists.example.com/a.m3u', 1700000000000, 'M3U', NULL, 'URL', '[\"https://a.example/g.xml\"]')"
            )
            v5.execSQL("INSERT INTO categories (id, name, playlistId) VALUES ('news', 'News', 'p1')")
            v5.execSQL(
                "INSERT INTO channel (id, name, url, logoUrl, categoryId, playlistId, isFavorite, lastWatched, channelNumber, groupsJson, " +
                    "isRadio, isVod, headersJson, mimeType, drmJson, catchupJson, epgShiftHours, sortIndex) VALUES ('ch1', 'Channel 1', " +
                    "'https://cdn.example.com/1.m3u8', NULL, 'News', 'p1', 1, 1700000000001, 5, '[\"News\"]', 0, 0, " +
                    "'{\"Referer\":\"https://r.example/\"}', NULL, NULL, NULL, 1.5, 3)"
            )
            v5.execSQL("INSERT INTO channel_attributes (channelId, attrKey, attrValue) VALUES ('ch1', 'tvg-id', 'ch1')")
            v5.execSQL(
                "INSERT INTO programs (id, channelId, title, description, startTime, endTime, category, playlistId) " +
                    "VALUES ('pr1', 'ch1', 'News at 9', NULL, 1700000000000, 1700003600000, NULL, 'p1')"
            )
            v5.execSQL(
                "INSERT INTO channel_history (channelId, playlistId, lastPlayedTimestamp, totalPlayedTimeMs, playCount, currentPositionMs, totalDurationMs) " +
                    "VALUES ('ch1', 'p1', 1700000000002, 60000, 4, 0, 0)"
            )
            v5.execSQL(
                "INSERT INTO stremio_addons (addonId, transportUrlEnc, transportHost, manifestJson, name, version, logoUrl, enabled, " +
                    "sortOrder, isAdult, isP2p, ownerSourceId, addedAt, lastFetchedAt, failCount, lastError, blocked) " +
                    "VALUES ('a', 'v1:x', 'h', '{}', 'A', '1', NULL, 1, 0, 0, 0, NULL, 1, 1, 0, NULL, 0)"
            )
            v5.execSQL(
                "INSERT INTO media_history (sourceKind, sourceId, itemType, itemId, videoId, title, subtitle, posterUrl, season, episode, " +
                    "lastAddonId, lastBingeGroup, positionMs, durationMs, finished, updatedAt) " +
                    "VALUES ('STREMIO', 'a', 'movie', 'm', 'm', 'M', NULL, NULL, NULL, NULL, 'a', NULL, 10, 20, 0, 30)"
            )
        }

        helper.runMigrationsAndValidate(6).use { v6 ->
            assertEquals(listOf("My list|URL|M3U"), v6.rows("SELECT name, sourceType, format FROM playlists"))
            assertEquals(
                listOf("Channel 1|1|5|3|1.5|{\"Referer\":\"https://r.example/\"}||||||"),
                v6.rows(
                    "SELECT name, isFavorite, channelNumber, sortIndex, epgShiftHours, headersJson, epgId, nameJson, " +
                        "originIncludePath, tagsJson, descriptionJson, streamsJson FROM channel"
                )
            )
            assertEquals(listOf("tvg-id"), v6.rows("SELECT attrKey FROM channel_attributes"))
            assertEquals(listOf("News"), v6.rows("SELECT name FROM categories"))
            assertEquals(listOf("News at 9"), v6.rows("SELECT title FROM programs"))
            assertEquals(listOf("4"), v6.rows("SELECT playCount FROM channel_history"))
            assertEquals(listOf("A|1"), v6.rows("SELECT name, enabled FROM stremio_addons"))
            assertEquals(listOf("10|20"), v6.rows("SELECT positionMs, durationMs FROM media_history"))
            for (table in listOf("tsiptv_sources", "tsiptv_includes", "vod_items", "vod_episodes")) {
                assertEquals(listOf("0"), v6.rows("SELECT COUNT(*) FROM $table"))
            }
        }

        val db = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath) { AppDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            runBlocking {
                val iptv = RoomIPTVDatabase(db)
                val channel = iptv.getChannelById("ch1")!!
                assertTrue(channel.isFavorite)
                assertEquals("ch1", channel.guideId)
                assertEquals(1, iptv.getAllWatchedChannelsWithDetails("p1").first().size)

                // A source stored on the migrated database, then removed with its rows (cascade).
                val pl = "tsiptv:demo"
                val store = iptv.tsiptvStore
                store.replaceSourceContent(
                    tss.t.tsiptv.core.tsiptv.TsiptvStoredContent(
                        playlist = tss.t.tsiptv.core.model.Playlist(id = pl, name = "Demo", url = "file:demo", lastUpdated = 1, format = "TSIPTV_SOURCE"),
                        categories = emptyList(),
                        channels = listOf(
                            tss.t.tsiptv.core.model.Channel(
                                id = "ts:$pl:news", name = "News", url = "https://cdn.example.com/n.m3u8", playlistId = pl, epgId = "news.example",
                                streamsJson = "[{\"url\":\"https://cdn.example.com/n.m3u8\"},{\"url\":\"https://cdn.example.com/n2.m3u8\"}]",
                            )
                        ),
                        source = tss.t.tsiptv.core.tsiptv.TsiptvSourceRecord(
                            playlistId = pl, sourceId = "demo", revision = 1, rootUrl = null, metaJson = "{\"name\":{\"values\":{\"en\":\"Demo\"}}}",
                            appearanceJson = null, layoutJson = null, etag = null, lastModified = null, fetchedAt = 1, reportJson = null,
                            adultConfirmed = false, rootDocumentJson = null, lastErrorCode = "network", lastErrorAt = 2,
                        ),
                        includes = listOf(
                            tss.t.tsiptv.core.tsiptv.TsiptvIncludeRecord(
                                playlistId = pl, includePath = "epg-0", type = "xmltv", url = "https://epg.example/g.xml", refreshHours = 24,
                                etag = null, lastModified = null, lastSuccessAt = 1, lastAttemptAt = 1, status = "OK", lastError = null,
                            )
                        ),
                        vodItems = emptyList(),
                        episodes = emptyList(),
                        guides = tss.t.tsiptv.core.tsiptv.TsiptvGuidePlan(
                            replace = mapOf(
                                "epg-0" to listOf(
                                    tss.t.tsiptv.core.parser.model.IPTVProgram(
                                        id = "p1", channelId = "news.example", title = "Morning", startTime = 0, endTime = Long.MAX_VALUE,
                                    )
                                ),
                                // A second guide with the same programme (QC r2 #3).
                                "epg-1" to listOf(
                                    tss.t.tsiptv.core.parser.model.IPTVProgram(
                                        id = "p1", channelId = "news.example", title = "Morning", startTime = 0, endTime = Long.MAX_VALUE,
                                    )
                                ),
                            )
                        ),
                    )
                )
                val stored = iptv.getChannelById("ts:$pl:news")!!
                assertEquals("news.example", stored.guideId)
                assertEquals(2, tss.t.tsiptv.core.tsiptv.TsiptvChannelStreams.streams(stored).size)
                assertEquals("network", store.getSource(pl)!!.lastErrorCode)
                // Programmes are stored per guide (prefixed ids) in the same transaction.
                assertEquals("Morning", iptv.getCurrentProgramForChannel("news.example", 1000)!!.title)
                assertEquals(1, iptv.getProgramsForChannel("news.example").size)
                assertEquals(1, iptv.getCurrentAndUpcomingProgramsForChannel("news.example", 1000).size)
                // Programs tab (QC r3): counted once, joined to the source channel by its guide id.
                assertEquals(1, iptv.countValidPrograms(pl))
                assertEquals(1, iptv.programDao.getValidPrograms(pl, 1000, 0, 10).size)
                val row = iptv.getChannelsWithValidProgramCounts(pl, 1000).single()
                assertEquals("news.example", row.channelId)
                assertEquals(1, row.programCount)
                assertEquals("News", row.name)
                // F1 channels still join by their id.
                assertEquals("Channel 1", iptv.getChannelsWithValidProgramCounts("p1", 1000).single().name)
                assertEquals(listOf("News at 9"), iptv.getProgramsForChannel("ch1").map { it.title })
                assertEquals(1, store.getIncludes(pl).size)
                iptv.deletePlaylistById(pl)
                assertNull(store.getSource(pl))
                assertTrue(store.getIncludes(pl).isEmpty())
                // The F1 playlist is untouched.
                assertEquals(1, iptv.getAllChannelsByPlayListId("p1").first().size)
            }
        } finally {
            db.close()
        }
    }

    private fun SQLiteConnection.rows(sql: String): List<String> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add((0 until statement.getColumnCount()).joinToString("|") { i ->
                    if (statement.isNull(i)) "" else statement.getText(i)
                })
            }
        }
    }

    private companion object {
        /** composeApp/schemas, found from the module or the repository root. */
        fun schemaDirectory(): Path {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (candidate in listOf("schemas", "composeApp/schemas")) {
                    val schemas = File(dir, candidate)
                    if (File(schemas, "${AppDatabase::class.qualifiedName}/5.json").exists()) return schemas.toPath()
                }
                dir = dir.parentFile
            }
            return Paths.get("schemas")
        }
    }
}
