package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.exception.IPTVParserException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class M3UParserTest {

    @Test
    fun testParseValidM3UContent() {
        // Arrange
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="channel1" tvg-name="Channel 1" tvg-logo="http://example.com/logo1.png" group-title="Group 1",Channel 1
            http://example.com/stream1.m3u8
            #EXTINF:-1 tvg-id="channel2" tvg-name="Channel 2" tvg-logo="http://example.com/logo2.png" group-title="Group 2",Channel 2
            http://example.com/stream2.m3u8
        """.trimIndent()

        val parser = M3UParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(2, playlist.channels.size)

        val channel1 = playlist.channels.find { it.id == "channel1" }
        assertNotNull(channel1)
        assertEquals("Channel 1", channel1.name)
        assertEquals("http://example.com/stream1.m3u8", channel1.url)
        assertEquals("http://example.com/logo1.png", channel1.logoUrl)
        assertEquals("Group 1", channel1.groupTitle)

        val channel2 = playlist.channels.find { it.id == "channel2" }
        assertNotNull(channel2)
        assertEquals("Channel 2", channel2.name)
        assertEquals("http://example.com/stream2.m3u8", channel2.url)
        assertEquals("http://example.com/logo2.png", channel2.logoUrl)
        assertEquals("Group 2", channel2.groupTitle)

        // Check that groups exist in the playlist
        assertTrue(playlist.groups.any { it.title == "Group 1" })
        assertTrue(playlist.groups.any { it.title == "Group 2" })
    }

    @Test
    fun testParseInvalidM3UContent() {
        // Arrange
        val content = """
            This is not a valid M3U file
            #EXTINF:-1,Channel 1
            http://example.com/stream1.m3u8
        """.trimIndent()

        val parser = M3UParser()

        // Act & Assert
        assertFailsWith<IPTVParserException> {
            parser.parse(content)
        }
    }

    @Test
    fun testParseM3UContentWithPlaylistName() {
        // Arrange
        val content = """
            #EXTM3U
            #PLAYLIST:My Test Playlist
            #EXTINF:-1,Channel 1
            http://example.com/stream1.m3u8
        """.trimIndent()

        val parser = M3UParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("My Test Playlist", playlist.name)
        assertEquals(1, playlist.channels.size)
    }

    @Test
    fun testParseM3UContentWithExtGrp() {
        // Arrange
        val content = """
            #EXTM3U
            #EXTINF:-1,Channel 1
            #EXTGRP:Sports
            http://example.com/stream1.m3u8
        """.trimIndent()

        val parser = M3UParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(1, playlist.channels.size)
        assertEquals(1, playlist.groups.size)

        val channel = playlist.channels[0]
        assertEquals("Sports", channel.groupTitle)

        val group = playlist.groups[0]
        assertEquals("sports", group.id)
        assertEquals("Sports", group.title)
    }

    @Test
    fun testParseM3UContentWithoutAttributes() {
        // Arrange
        val content = """
            #EXTM3U
            #EXTINF:-1,Channel 1
            http://example.com/stream1.m3u8
        """.trimIndent()

        val parser = M3UParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(1, playlist.channels.size)

        val channel = playlist.channels[0]
        assertEquals("channel_1", channel.id)
        assertEquals("Channel 1", channel.name)
        assertEquals("http://example.com/stream1.m3u8", channel.url)
    }

    @Test
    fun testParseM3UContentWithEmptyLines() {
        // Arrange
        val content = """
            #EXTM3U

            #EXTINF:-1,Channel 1

            http://example.com/stream1.m3u8

        """.trimIndent()

        val parser = M3UParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(1, playlist.channels.size)
    }

    @Test
    fun testGetSupportedFormat() {
        // Arrange
        val parser = M3UParser()

        // Act
        val format = parser.getSupportedFormat()

        // Assert
        assertEquals(IPTVFormat.M3U, format)
    }

    @Test
    fun testParseM3UContentWithEPGUrl() {
        // Arrange
        val content = """
            #EXTM3U url-tvg="http://lichphatsong.xyz/schedule/epg.xml.gz"
            #EXTINF:-1 tvg-id="channel1" tvg-name="Channel 1" tvg-logo="http://example.com/logo1.png" group-title="Group 1",Channel 1
            http://example.com/stream1.m3u8
            #EXTINF:-1 tvg-id="channel2" tvg-name="Channel 2" tvg-logo="http://example.com/logo2.png" group-title="Group 2",Channel 2
            http://example.com/stream2.m3u8
        """.trimIndent()

        val parser = M3UParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(2, playlist.channels.size)
        assertEquals("http://lichphatsong.xyz/schedule/epg.xml.gz", playlist.epgUrl)

        // Verify that the playlist doesn't contain any programs
        assertTrue(playlist.programs.isEmpty())
    }
}
