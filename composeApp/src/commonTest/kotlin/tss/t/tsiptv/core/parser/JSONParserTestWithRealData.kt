package tss.t.tsiptv.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JSONParserTestWithRealData {

    @Test
    fun testParseRealJSONFile() {
        // Load the test.json file
        val content = loadTestJson()

        // Create a parser
        val parser = JSONParser()

        // Parse the content
        val playlist = parser.parse(content)

        // Verify the playlist was parsed correctly
        assertNotNull(playlist)
        assertEquals("TV PHIM", playlist.name)

        // Verify that channel were extracted
        assertTrue(playlist.channels.isNotEmpty(), "Playlist should have channel")
        println("[DEBUG_LOG] Found ${playlist.channels.size} channel")

        // Verify that groups were extracted
        assertTrue(playlist.groups.isNotEmpty(), "Playlist should have groups")
        println("[DEBUG_LOG] Found ${playlist.groups.size} groups")

        // Verify specific channel were extracted correctly
        val channel1 = playlist.channels.find { it.id == "NWV22PrWLY" }
        assertNotNull(channel1, "Channel with ID NWV22PrWLY should exist")
        assertEquals("Phim Đảo Hải Tặc", channel1.name)
        assertEquals("https://tv-pvd.moviedb.dev/channel-detail?uid=NWV22PrWLY", channel1.url)
        assertEquals("https://img.moviedb.dev/thumb/NWV22PrWLY_dao-hai-tac.png", channel1.logoUrl)

        val channel2 = playlist.channels.find { it.id == "w5Z1x6PxJk" }
        assertNotNull(channel2, "Channel with ID w5Z1x6PxJk should exist")
        assertEquals("Phim Hỗn Loạn", channel2.name)

        // Verify specific groups were extracted correctly
        val group = playlist.groups.find { it.id == "POPULAR" }
        assertNotNull(group, "Group with ID POPULAR should exist")
        assertEquals("Phim thịnh hành", group.title)

        // Verify channel from groups were extracted correctly
        val groupChannel = playlist.channels.find { it.id == "76c270Eujb" }
        assertNotNull(groupChannel, "Channel with ID 76c270Eujb from group should exist")
        assertEquals("Phim Cõng Anh Mà Chạy", groupChannel.name)
        assertEquals("Phim thịnh hành", groupChannel.groupTitle)
        assertEquals("POPULAR", groupChannel.groupId)
    }

    @Test
    fun testParseChannelArray() {
        val content = """
        [
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
                "url": "http://example.com/stream2.m3u8",
                "image": {
                    "url": "http://example.com/logo2.png",
                    "type": "cover"
                },
                "group": "News"
            }
        ]
        """.trimIndent()

        val parser = JSONParser()
        val playlist = parser.parse(content)

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
    }

    @Test
    fun testParseRemoteDataChannel() {
        val content = """
        {
            "name": "Remote Data Test",
            "channel": [
                {
                    "id": "remote1",
                    "name": "Remote Channel",
                    "remote_data": {
                        "url": "http://example.com/remote1"
                    },
                    "image": {
                        "url": "http://example.com/remote_logo.png",
                        "type": "cover"
                    }
                }
            ]
        }
        """.trimIndent()

        val parser = JSONParser()
        val playlist = parser.parse(content)

        assertEquals("Remote Data Test", playlist.name)
        assertEquals(1, playlist.channels.size)

        val channel = playlist.channels.first()
        assertEquals("remote1", channel.id)
        assertEquals("Remote Channel", channel.name)
        assertEquals("http://example.com/remote1", channel.url)
        assertEquals("http://example.com/remote_logo.png", channel.logoUrl)
    }

    private fun loadTestJson(): String {
        // In a real test, we would load the file from resources
        // For this example, we'll use a simplified version of the structure
        return """
        {
          "name": "TV PHIM",
          "id": "tvphim-pvd",
          "url": "https://tv-pvd.moviedb.dev",
          "channel": [
            {
              "id": "NWV22PrWLY",
              "name": "Phim Đảo Hải Tặc",
              "image": {
                "url": "https://img.moviedb.dev/thumb/NWV22PrWLY_dao-hai-tac.png",
                "type": "cover"
              },
              "remote_data": {
                "url": "https://tv-pvd.moviedb.dev/channel-detail?uid=NWV22PrWLY"
              }
            },
            {
              "id": "w5Z1x6PxJk",
              "name": "Phim Hỗn Loạn",
              "image": {
                "url": "https://img.moviedb.dev/thumb/w5Z1x6PxJk_hon-loan.png",
                "type": "cover"
              },
              "remote_data": {
                "url": "https://tv-pvd.moviedb.dev/channel-detail?uid=w5Z1x6PxJk"
              }
            }
          ],
          "groups": [
            {
              "id": "POPULAR",
              "name": "Phim thịnh hành",
              "channel": [
                {
                  "id": "76c270Eujb",
                  "name": "Phim Cõng Anh Mà Chạy",
                  "image": {
                    "url": "https://img.moviedb.dev/thumb/76c270Eujb_cong-anh-ma-chay.png",
                    "type": "cover"
                  },
                  "remote_data": {
                    "url": "https://tv-pvd.moviedb.dev/channel-detail?uid=76c270Eujb"
                  }
                }
              ]
            }
          ]
        }
        """.trimIndent()
    }
}
