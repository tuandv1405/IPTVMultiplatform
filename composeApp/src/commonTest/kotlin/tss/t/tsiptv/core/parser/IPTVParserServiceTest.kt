package tss.t.tsiptv.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IPTVParserServiceTest {

    @Test
    fun testParsePlaylistWithEPG() {
        // Arrange
        val m3uContent = """
            #EXTM3U url-tvg="http://example.com/epg.xml"
            #EXTINF:-1 tvg-id="channel1" tvg-name="Channel 1" tvg-logo="http://example.com/logo1.png" group-title="Group 1",Channel 1
            http://example.com/stream1.m3u8
            #EXTINF:-1 tvg-id="channel2" tvg-name="Channel 2" tvg-logo="http://example.com/logo2.png" group-title="Group 2",Channel 2
            http://example.com/stream2.m3u8
        """.trimIndent()

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

        // Parse the M3U content first
        val parser = M3UParser()
        val playlist = parser.parse(m3uContent)

        // Then use the service to add EPG data
        val service = IPTVParserService(mockNetworkClient())

        // Act
        val playlistWithEPG = service.parsePlaylistWithEPG(playlist, epgContent)

        // Assert
        assertEquals(2, playlistWithEPG.channels.size)
        assertEquals("http://example.com/epg.xml", playlistWithEPG.epgUrl)

        // Verify that the playlist contains programs
        assertTrue(playlistWithEPG.programs.isNotEmpty())
        assertEquals(3, playlistWithEPG.programs.size)

        // Check programs for channel1
        val channel1Programs = playlistWithEPG.programs.filter { it.channelId == "channel1" }
        assertEquals(2, channel1Programs.size)

        val program1 = channel1Programs.find { it.title == "Program 1" }
        assertNotNull(program1)
        assertEquals("Description 1", program1.description)
        assertEquals("Movie", program1.category)

        val program2 = channel1Programs.find { it.title == "Program 2" }
        assertNotNull(program2)
        assertEquals("Description 2", program2.description)
        assertEquals("News", program2.category)

        // Check programs for channel2
        val channel2Programs = playlistWithEPG.programs.filter { it.channelId == "channel2" }
        assertEquals(1, channel2Programs.size)

        val program3 = channel2Programs[0]
        assertEquals("Program 3", program3.title)
        assertEquals("Description 3", program3.description)
        assertEquals("Sports", program3.category)
    }

    // Helper function to create a mock NetworkClient
    // We don't actually use this in the test, but we need it to create the IPTVParserService
    private fun mockNetworkClient(): tss.t.tsiptv.core.network.NetworkClient {
        return object : tss.t.tsiptv.core.network.NetworkClient {
            override suspend fun get(url: String, headers: Map<String, String>): String {
                throw UnsupportedOperationException("Not used in this test")
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
