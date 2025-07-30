package tss.t.tsiptv.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XSPFParserTest {

    @Test
    fun testParseValidXSPFContent() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <playlist xmlns="http://xspf.org/ns/0/" xmlns:vlc="http://www.videolan.org/vlc/playlist/ns/0/" version="1">
                <title>Test XSPF Playlist</title>
                <trackList>
                    <track>
                        <title>Channel 1</title>
                        <location>http://example.com/channel1.m3u8</location>
                        <image>http://example.com/logo/channel1.png</image>
                        <extension application="http://www.videolan.org/vlc/playlist/0">
                            <vlc:id>1</vlc:id>
                        </extension>
                    </track>
                    <track>
                        <title>Channel 2</title>
                        <location>http://example.com/channel2.m3u8</location>
                        <image>http://example.com/logo/channel2.png</image>
                        <extension application="http://www.videolan.org/vlc/playlist/0">
                            <vlc:id>2</vlc:id>
                        </extension>
                    </track>
                </trackList>
                <extension application="http://www.videolan.org/vlc/playlist/0">
                    <vlc:node title="Sports">
                        <vlc:item tid="1"/>
                    </vlc:node>
                    <vlc:node title="Movies">
                        <vlc:item tid="2"/>
                    </vlc:node>
                </extension>
            </playlist>
        """.trimIndent()

        val parser = XSPFParser()
        val playlist = parser.parse(content)

        // Verify playlist name
        assertEquals("Test XSPF Playlist", playlist.name)

        // Verify channel
        assertEquals(2, playlist.channels.size)
        
        val channel1 = playlist.channels.find { it.name == "Channel 1" }
        assertNotNull(channel1)
        assertEquals("http://example.com/channel1.m3u8", channel1.url)
        assertEquals("http://example.com/logo/channel1.png", channel1.logoUrl)
        assertEquals("1", channel1.id)
        
        val channel2 = playlist.channels.find { it.name == "Channel 2" }
        assertNotNull(channel2)
        assertEquals("http://example.com/channel2.m3u8", channel2.url)
        assertEquals("http://example.com/logo/channel2.png", channel2.logoUrl)
        assertEquals("2", channel2.id)

        // Verify groups
        assertEquals(2, playlist.groups.size)
        assertTrue(playlist.groups.any { it.title == "Sports" })
        assertTrue(playlist.groups.any { it.title == "Movies" })
    }

    @Test
    fun testParseXSPFContentWithoutImages() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <playlist xmlns="http://xspf.org/ns/0/" xmlns:vlc="http://www.videolan.org/vlc/playlist/ns/0/" version="1">
                <title>Test XSPF Playlist</title>
                <trackList>
                    <track>
                        <title>Channel 1</title>
                        <location>http://example.com/channel1.m3u8</location>
                        <extension application="http://www.videolan.org/vlc/playlist/0">
                            <vlc:id>1</vlc:id>
                        </extension>
                    </track>
                    <track>
                        <title>Channel 2</title>
                        <location>http://example.com/channel2.m3u8</location>
                        <extension application="http://www.videolan.org/vlc/playlist/0">
                            <vlc:id>2</vlc:id>
                        </extension>
                    </track>
                </trackList>
            </playlist>
        """.trimIndent()

        val parser = XSPFParser()
        val playlist = parser.parse(content)

        // Verify playlist name
        assertEquals("Test XSPF Playlist", playlist.name)

        // Verify channel
        assertEquals(2, playlist.channels.size)
        
        val channel1 = playlist.channels.find { it.name == "Channel 1" }
        assertNotNull(channel1)
        assertEquals("http://example.com/channel1.m3u8", channel1.url)
        assertEquals(null, channel1.logoUrl)
        assertEquals("1", channel1.id)
        
        val channel2 = playlist.channels.find { it.name == "Channel 2" }
        assertNotNull(channel2)
        assertEquals("http://example.com/channel2.m3u8", channel2.url)
        assertEquals(null, channel2.logoUrl)
        assertEquals("2", channel2.id)
    }

    @Test
    fun testParseXSPFContentWithoutIds() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <playlist xmlns="http://xspf.org/ns/0/" xmlns:vlc="http://www.videolan.org/vlc/playlist/ns/0/" version="1">
                <title>Test XSPF Playlist</title>
                <trackList>
                    <track>
                        <title>Channel 1</title>
                        <location>http://example.com/channel1.m3u8</location>
                    </track>
                    <track>
                        <title>Channel 2</title>
                        <location>http://example.com/channel2.m3u8</location>
                    </track>
                </trackList>
            </playlist>
        """.trimIndent()

        val parser = XSPFParser()
        val playlist = parser.parse(content)

        // Verify channel
        assertEquals(2, playlist.channels.size)
        
        val channel1 = playlist.channels.find { it.name == "Channel 1" }
        assertNotNull(channel1)
        assertEquals("channel_1", channel1.id)
        
        val channel2 = playlist.channels.find { it.name == "Channel 2" }
        assertNotNull(channel2)
        assertEquals("channel_2", channel2.id)
    }

    @Test
    fun testParseInvalidXSPFContent() {
        val content = """
            {
                "invalid": "json"
            }
        """.trimIndent()

        val parser = XSPFParser()
        assertFailsWith<IPTVParserException> {
            parser.parse(content)
        }
    }

    @Test
    fun testGetSupportedFormat() {
        val parser = XSPFParser()
        assertEquals(IPTVFormat.XSPF, parser.getSupportedFormat())
    }
}