package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.core.parser.IPTVParserService
import tss.t.tsiptv.core.parser.M3UParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for the EPG parser and database flow.
 * This test verifies that EPG data can be parsed and stored in the database, and then retrieved correctly.
 */
class EPGDatabaseTest {

    /**
     * Test the full flow from parsing EPG data to storing it in the database and retrieving it.
     */
    @Test
    fun testEPGParserAndDatabaseFlow() = runBlocking {
        // Arrange
        val database = InMemoryIPTVDatabase()
        val networkClient = mockNetworkClient()
        val parserService = IPTVParserService(networkClient)

        // Sample M3U content with EPG URL
        val m3uContent = """
            #EXTM3U url-tvg="http://example.com/epg.xml"
            #EXTINF:-1 tvg-id="channel1" tvg-name="Channel 1" tvg-logo="http://example.com/logo1.png" group-title="Group 1",Channel 1
            http://example.com/stream1.m3u8
            #EXTINF:-1 tvg-id="channel2" tvg-name="Channel 2" tvg-logo="http://example.com/logo2.png" group-title="Group 2",Channel 2
            http://example.com/stream2.m3u8
        """.trimIndent()

        // Sample EPG content
        val epgContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="channel1">
                    <display-name>Channel 1</display-name>
                </channel>
                <channel id="channel2">
                    <display-name>Channel 2</display-name>
                </channel>
                <programme start="20230101120000 +0000" stop="20230101130000 +0000" channel="channel1">
                    <title>Program 1</title>
                    <desc>Description 1</desc>
                    <category>Movie</category>
                </programme>
                <programme start="20230101130000 +0000" stop="20230101140000 +0000" channel="channel1">
                    <title>Program 2</title>
                    <desc>Description 2</desc>
                    <category>News</category>
                </programme>
                <programme start="20230101120000 +0000" stop="20230101130000 +0000" channel="channel2">
                    <title>Program 3</title>
                    <desc>Description 3</desc>
                    <category>Sports</category>
                </programme>
            </tv>
        """.trimIndent()

        // Act
        // 1. Parse the M3U content
        val parser = M3UParser()
        val playlist = parser.parse(m3uContent)

        // 2. Add EPG data to the playlist
        val playlistWithEPG = parserService.parsePlaylistWithEPG(playlist, epgContent)

        // 3. Store the playlist in the database
        val dbPlaylist = Playlist(
            id = "test_playlist",
            name = playlistWithEPG.name,
            url = "http://example.com/playlist.m3u",
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        )
        database.insertPlaylist(dbPlaylist)

        // 4. Store the channel in the database
        val channels = playlistWithEPG.channels.map { channel ->
            Channel(
                id = channel.id,
                name = channel.name,
                url = channel.url,
                logoUrl = channel.logoUrl,
                categoryId = channel.groupId,
                playlistId = dbPlaylist.id
            )
        }
        database.insertChannels(channels)

        // 5. Store the programs in the database
        database.insertPrograms(playlistWithEPG.programs, dbPlaylist.id)

        // Assert
        // 1. Verify that the playlist was stored correctly
        val storedPlaylist = database.getPlaylistById(dbPlaylist.id)
        assertNotNull(storedPlaylist)
        assertEquals(dbPlaylist.name, storedPlaylist.name)

        // 2. Verify that the channel were stored correctly
        val storedChannels = database.getAllChannelsByPlayListId(dbPlaylist.id).first()
        assertEquals(2, storedChannels.size)

        val channel1 = storedChannels.find { it.name == "Channel 1" }
        assertNotNull(channel1)
        assertEquals("http://example.com/logo1.png", channel1.logoUrl)

        val channel2 = storedChannels.find { it.name == "Channel 2" }
        assertNotNull(channel2)
        assertEquals("http://example.com/logo2.png", channel2.logoUrl)

        // 3. Verify that the programs were stored correctly
        val channel1Programs = database.getProgramsForChannel(channel1.id)
        assertEquals(2, channel1Programs.size)

        val program1 = channel1Programs.find { it.title == "Program 1" }
        assertNotNull(program1)
        assertEquals("Description 1", program1.description)
        assertEquals("Movie", program1.category)

        val program2 = channel1Programs.find { it.title == "Program 2" }
        assertNotNull(program2)
        assertEquals("Description 2", program2.description)
        assertEquals("News", program2.category)

        val channel2Programs = database.getProgramsForChannel(channel2.id)
        assertEquals(1, channel2Programs.size)

        val program3 = channel2Programs[0]
        assertEquals("Program 3", program3.title)
        assertEquals("Description 3", program3.description)
        assertEquals("Sports", program3.category)

        // 4. Test time-based queries
        val currentTime = program1.startTime + (program1.endTime - program1.startTime) / 2
        val currentProgram = database.getCurrentProgramForChannel(channel1.id, currentTime)
        assertNotNull(currentProgram)
        assertEquals("Program 1", currentProgram.title)

        val upcomingPrograms = database.getCurrentAndUpcomingProgramsForChannel(channel1.id, currentTime)
        assertEquals(2, upcomingPrograms.size) // Current program + upcoming program
        assertEquals("Program 1", upcomingPrograms[0].title)
        assertEquals("Program 2", upcomingPrograms[1].title)

        val programsInRange = database.getProgramsForChannelInTimeRange(
            channel1.id,
            program1.startTime,
            program2.endTime
        )
        assertEquals(2, programsInRange.size)
    }

    /**
     * Test edge cases for the EPG parser and database flow.
     */
    @Test
    fun testEPGParserAndDatabaseFlowEdgeCases() = runBlocking {
        // Arrange
        val database = InMemoryIPTVDatabase()
        val networkClient = mockNetworkClient()
        val parserService = IPTVParserService(networkClient)

        // Sample M3U content with no EPG URL
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="channel1" tvg-name="Channel 1" tvg-logo="http://example.com/logo1.png" group-title="Group 1",Channel 1
            http://example.com/stream1.m3u8
        """.trimIndent()

        // Empty EPG content
        val emptyEpgContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
            </tv>
        """.trimIndent()

        // Act
        // 1. Parse the M3U content
        val parser = M3UParser()
        val playlist = parser.parse(m3uContent)

        // 2. Add empty EPG data to the playlist
        val playlistWithEmptyEPG = parserService.parsePlaylistWithEPG(playlist, emptyEpgContent)

        // 3. Store the playlist in the database
        val dbPlaylist = Playlist(
            id = "test_playlist",
            name = playlistWithEmptyEPG.name,
            url = "http://example.com/playlist.m3u",
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        )
        database.insertPlaylist(dbPlaylist)

        // 4. Store the channel in the database
        val channels = playlistWithEmptyEPG.channels.map { channel ->
            Channel(
                id = channel.id,
                name = channel.name,
                url = channel.url,
                logoUrl = channel.logoUrl,
                categoryId = channel.groupId,
                playlistId = dbPlaylist.id
            )
        }
        database.insertChannels(channels)

        // 5. Store the programs in the database (should be empty)
        database.insertPrograms(playlistWithEmptyEPG.programs, dbPlaylist.id)

        // Assert
        // 1. Verify that the playlist was stored correctly
        val storedPlaylist = database.getPlaylistById(dbPlaylist.id)
        assertNotNull(storedPlaylist)
        assertEquals(dbPlaylist.name, storedPlaylist.name)

        // 2. Verify that the channel were stored correctly
        val storedChannels = database.getAllChannelsByPlayListId(dbPlaylist.id).first()
        assertEquals(1, storedChannels.size)

        val channel1 = storedChannels[0]
        assertEquals("Channel 1", channel1.name)
        assertEquals("http://example.com/logo1.png", channel1.logoUrl)

        // 3. Verify that no programs were stored
        val channel1Programs = database.getProgramsForChannel(channel1.id)
        assertTrue(channel1Programs.isEmpty())
    }

    /**
     * Helper function to create a mock NetworkClient.
     * We don't actually use this in the test, but we need it to create the IPTVParserService.
     */
    private fun mockNetworkClient(): tss.t.tsiptv.core.network.NetworkClient {
        return object : tss.t.tsiptv.core.network.NetworkClient {
            override suspend fun get(url: String, headers: Map<String, String>): String {
                throw UnsupportedOperationException("Not used in this test")
            }

            override suspend fun getManualGzipIfNeed(
                url: String,
                headers: Map<String, String>,
            ): String {
                TODO("Not yet implemented")
            }

            override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
                throw UnsupportedOperationException("Not used in this test")
            }

            override suspend fun put(url: String, body: String, headers: Map<String, String>): String {
                throw UnsupportedOperationException("Not used in this test")
            }

            override suspend fun delete(url: String, headers: Map<String, String>): String {
                throw UnsupportedOperationException("Not used in this test")
            }

            override suspend fun downloadFile(url: String, headers: Map<String, String>): kotlinx.coroutines.flow.Flow<tss.t.tsiptv.core.network.DownloadProgress> {
                throw UnsupportedOperationException("Not used in this test")
            }

            override suspend fun uploadFile(
                url: String,
                fileBytes: ByteArray,
                fileName: String,
                mimeType: String,
                headers: Map<String, String>
            ): kotlinx.coroutines.flow.Flow<tss.t.tsiptv.core.network.UploadProgress> {
                throw UnsupportedOperationException("Not used in this test")
            }
        }
    }
}
