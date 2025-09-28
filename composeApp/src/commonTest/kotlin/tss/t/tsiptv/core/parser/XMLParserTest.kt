package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.exception.IPTVParserException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class XMLParserTest {

    @Test
    fun testParseValidXMLContent() {
        // Arrange
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <title>Test XML Playlist</title>
                <channel id="channel1">
                    <display-name>Channel 1</display-name>
                    <icon src="http://example.com/logo1.png"/>
                    <group>Sports</group>
                    <url>http://example.com/stream1.m3u8</url>
                </channel>
                <channel id="channel2">
                    <display-name>Channel 2</display-name>
                    <icon src="http://example.com/logo2.png"/>
                    <group>News</group>
                    <url>http://example.com/stream2.m3u8</url>
                </channel>
            </tv>
        """.trimIndent()

        val parser = XMLParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("Test XML Playlist", playlist.name)
        assertEquals(2, playlist.channels.size)
        assertEquals(2, playlist.groups.size)

        val channel1 = playlist.channels.find { it.id == "channel1" }
        assertNotNull(channel1)
        assertEquals("Channel 1", channel1.name)
        assertEquals("http://example.com/stream1.m3u8", channel1.url)
        assertEquals("http://example.com/logo1.png", channel1.logoUrl)
        assertEquals("Sports", channel1.groupTitle)

        val channel2 = playlist.channels.find { it.id == "channel2" }
        assertNotNull(channel2)
        assertEquals("Channel 2", channel2.name)
        assertEquals("http://example.com/stream2.m3u8", channel2.url)
        assertEquals("http://example.com/logo2.png", channel2.logoUrl)
        assertEquals("News", channel2.groupTitle)

        val sportsGroup = playlist.groups.find { it.title == "Sports" }
        assertNotNull(sportsGroup)
        assertEquals("sports", sportsGroup.id)

        val newsGroup = playlist.groups.find { it.title == "News" }
        assertNotNull(newsGroup)
        assertEquals("news", newsGroup.id)
    }

    @Test
    fun testParseXMLContentWithTvRootElement() {
        // Arrange
        val content = """
            <tv>
                <title>Test XML Playlist</title>
                <channel id="channel1">
                    <display-name>Channel 1</display-name>
                    <url>http://example.com/stream1.m3u8</url>
                </channel>
            </tv>
        """.trimIndent()

        val parser = XMLParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("Test XML Playlist", playlist.name)
        assertEquals(1, playlist.channels.size)
    }

    @Test
    fun testParseInvalidXMLContent() {
        // Arrange
        val content = """
            This is not a valid XML file
            <channel id="channel1">
                <display-name>Channel 1</display-name>
                <url>http://example.com/stream1.m3u8</url>
            </channel>
        """.trimIndent()

        val parser = XMLParser()

        // Act & Assert
        assertFailsWith<IPTVParserException> {
            parser.parse(content)
        }
    }

    @Test
    fun testParseXMLContentWithoutTitle() {
        // Arrange
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="channel1">
                    <display-name>Channel 1</display-name>
                    <url>http://example.com/stream1.m3u8</url>
                </channel>
            </tv>
        """.trimIndent()

        val parser = XMLParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("IPTV Playlist", playlist.name) // Default name
        assertEquals(1, playlist.channels.size)
    }

    @Test
    fun testParseXMLContentWithoutLogo() {
        // Arrange
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="channel1">
                    <display-name>Channel 1</display-name>
                    <url>http://example.com/stream1.m3u8</url>
                </channel>
            </tv>
        """.trimIndent()

        val parser = XMLParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(1, playlist.channels.size)

        val channel = playlist.channels[0]
        assertEquals(null, channel.logoUrl)
    }

    @Test
    fun testParseXMLContentWithoutGroup() {
        // Arrange
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="channel1">
                    <display-name>Channel 1</display-name>
                    <url>http://example.com/stream1.m3u8</url>
                </channel>
            </tv>
        """.trimIndent()

        val parser = XMLParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(1, playlist.channels.size)
        assertEquals(0, playlist.groups.size)

        val channel = playlist.channels[0]
        assertEquals(null, channel.groupTitle)
    }

    @Test
    fun testParseXMLContentWithIncompleteChannel() {
        // Arrange
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="channel1">
                    <display-name>Channel 1</display-name>
                    <!-- Missing URL -->
                </channel>
                <channel id="channel2">
                    <!-- Missing display-name -->
                    <url>http://example.com/stream2.m3u8</url>
                </channel>
                <channel>
                    <!-- Missing ID -->
                    <display-name>Channel 3</display-name>
                    <url>http://example.com/stream3.m3u8</url>
                </channel>
                <channel id="channel4">
                    <display-name>Channel 4</display-name>
                    <url>http://example.com/stream4.m3u8</url>
                </channel>
            </tv>
        """.trimIndent()

        val parser = XMLParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(1, playlist.channels.size) // Only channel4 is complete

        val channel = playlist.channels[0]
        assertEquals("channel4", channel.id)
        assertEquals("Channel 4", channel.name)
        assertEquals("http://example.com/stream4.m3u8", channel.url)
    }

    @Test
    fun testGetSupportedFormat() {
        // Arrange
        val parser = XMLParser()

        // Act
        val format = parser.getSupportedFormat()

        // Assert
        assertEquals(IPTVFormat.XML, format)
    }
}
