package tss.t.tsiptv.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JSONParserTest {

    @Test
    fun testParseValidJSONContentFormat1() {
        // Arrange - Format 1: Array of channel
        val content = """
            [
                {
                    "id": "channel1",
                    "name": "Channel 1",
                    "url": "http://example.com/stream1.m3u8",
                    "logoUrl": "http://example.com/logo1.png",
                    "groupTitle": "Sports",
                    "epgId": "epg1"
                },
                {
                    "id": "channel2",
                    "name": "Channel 2",
                    "url": "http://example.com/stream2.m3u8",
                    "logoUrl": "http://example.com/logo2.png",
                    "groupTitle": "News",
                    "epgId": "epg2"
                }
            ]
        """.trimIndent()

        val parser = JSONParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("IPTV Playlist", playlist.name) // Default name
        assertEquals(2, playlist.channels.size)
        assertEquals(2, playlist.groups.size)

        val channel1 = playlist.channels.find { it.id == "channel1" }
        assertNotNull(channel1)
        assertEquals("Channel 1", channel1.name)
        assertEquals("http://example.com/stream1.m3u8", channel1.url)
        assertEquals("http://example.com/logo1.png", channel1.logoUrl)
        assertEquals("Sports", channel1.groupTitle)
        assertEquals("epg1", channel1.epgId)

        val channel2 = playlist.channels.find { it.id == "channel2" }
        assertNotNull(channel2)
        assertEquals("Channel 2", channel2.name)
        assertEquals("http://example.com/stream2.m3u8", channel2.url)
        assertEquals("http://example.com/logo2.png", channel2.logoUrl)
        assertEquals("News", channel2.groupTitle)
        assertEquals("epg2", channel2.epgId)

        val sportsGroup = playlist.groups.find { it.title == "Sports" }
        assertNotNull(sportsGroup)
        assertEquals("sports", sportsGroup.id)

        val newsGroup = playlist.groups.find { it.title == "News" }
        assertNotNull(newsGroup)
        assertEquals("news", newsGroup.id)
    }

    @Test
    fun testParseValidJSONContentFormat2() {
        // Arrange - Format 2: Object with channel property
        val content = """
            {
                "name": "My JSON Playlist",
                "channel": [
                    {
                        "id": "channel1",
                        "name": "Channel 1",
                        "url": "http://example.com/stream1.m3u8",
                        "logoUrl": "http://example.com/logo1.png",
                        "groupTitle": "Sports"
                    },
                    {
                        "id": "channel2",
                        "name": "Channel 2",
                        "url": "http://example.com/stream2.m3u8"
                    }
                ]
            }
        """.trimIndent()

        val parser = JSONParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("My JSON Playlist", playlist.name)
        assertEquals(2, playlist.channels.size)
        assertEquals(1, playlist.groups.size)

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
        assertEquals(null, channel2.logoUrl)
        assertEquals(null, channel2.groupTitle)

        val sportsGroup = playlist.groups.find { it.title == "Sports" }
        assertNotNull(sportsGroup)
        assertEquals("sports", sportsGroup.id)
    }

    @Test
    fun testParseValidJSONContentFormat3() {
        // Arrange - Format 3: Object with groups property containing channel
        val content = """
            {
                "name": "Grouped JSON Playlist",
                "groups": [
                    {
                        "name": "Sports",
                        "channel": [
                            {
                                "id": "channel1",
                                "name": "Sports Channel 1",
                                "url": "http://example.com/sports1.m3u8",
                                "logoUrl": "http://example.com/logo1.png"
                            },
                            {
                                "id": "channel2",
                                "name": "Sports Channel 2",
                                "url": "http://example.com/sports2.m3u8"
                            }
                        ]
                    },
                    {
                        "name": "News",
                        "channel": [
                            {
                                "id": "channel3",
                                "name": "News Channel",
                                "url": "http://example.com/news.m3u8"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val parser = JSONParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("Grouped JSON Playlist", playlist.name)
        assertEquals(3, playlist.channels.size)
        assertEquals(2, playlist.groups.size)

        // Check Sports group channel
        val sportsChannels = playlist.channels.filter { it.groupTitle == "Sports" }
        assertEquals(2, sportsChannels.size)

        val channel1 = playlist.channels.find { it.id == "channel1" }
        assertNotNull(channel1)
        assertEquals("Sports Channel 1", channel1.name)
        assertEquals("http://example.com/sports1.m3u8", channel1.url)
        assertEquals("http://example.com/logo1.png", channel1.logoUrl)
        assertEquals("Sports", channel1.groupTitle)

        // Check News group channel
        val newsChannels = playlist.channels.filter { it.groupTitle == "News" }
        assertEquals(1, newsChannels.size)

        val channel3 = playlist.channels.find { it.id == "channel3" }
        assertNotNull(channel3)
        assertEquals("News Channel", channel3.name)
        assertEquals("http://example.com/news.m3u8", channel3.url)
        assertEquals("News", channel3.groupTitle)

        // Check groups
        val sportsGroup = playlist.groups.find { it.title == "Sports" }
        assertNotNull(sportsGroup)
        assertEquals("sports", sportsGroup.id)

        val newsGroup = playlist.groups.find { it.title == "News" }
        assertNotNull(newsGroup)
        assertEquals("news", newsGroup.id)
    }

    @Test
    fun testParseInvalidJSONContent() {
        // Arrange
        val content = """
            This is not a valid JSON file
            "id": "channel1",
            "name": "Channel 1"
        """.trimIndent()

        val parser = JSONParser()

        // Act & Assert
        assertFailsWith<IPTVParserException> {
            parser.parse(content)
        }
    }

    @Test
    fun testParseJSONContentWithoutChannels() {
        // Arrange
        val content = """
            {
                "name": "Empty Playlist",
                "description": "This playlist has no channel"
            }
        """.trimIndent()

        val parser = JSONParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals("Empty Playlist", playlist.name)
        assertEquals(0, playlist.channels.size)
        assertEquals(0, playlist.groups.size)
    }

    @Test
    fun testParseJSONContentWithAlternativeFieldNames() {
        // Arrange
        val content = """
            [
                {
                    "id": "channel1",
                    "name": "Channel 1",
                    "url": "http://example.com/stream1.m3u8",
                    "logo": "http://example.com/logo1.png",
                    "group": "Sports",
                    "epg": "epg1"
                }
            ]
        """.trimIndent()

        val parser = JSONParser()

        // Act
        val playlist = parser.parse(content)

        // Assert
        assertEquals(1, playlist.channels.size)

        val channel = playlist.channels[0]
        assertEquals("channel1", channel.id)
        assertEquals("Channel 1", channel.name)
        assertEquals("http://example.com/stream1.m3u8", channel.url)
        assertEquals("http://example.com/logo1.png", channel.logoUrl)
        assertEquals("Sports", channel.groupTitle)
        assertEquals("epg1", channel.epgId)
    }

    @Test
    fun testGetSupportedFormat() {
        // Arrange
        val parser = JSONParser()

        // Act
        val format = parser.getSupportedFormat()

        // Assert
        assertEquals(IPTVFormat.JSON, format)
    }
}
