package tss.t.tsiptv.core.database

import tss.t.tsiptv.core.database.entity.ChannelEntity
import tss.t.tsiptv.core.database.entity.PlaylistEntity
import tss.t.tsiptv.core.database.entity.toChannel
import tss.t.tsiptv.core.database.entity.toChannelEntity
import tss.t.tsiptv.core.database.entity.toPlaylist
import tss.t.tsiptv.core.database.entity.toPlaylistEntity
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.core.parser.model.playback.CatchupMode
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.ClearKey
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.player.models.toMediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Everything playback needs survives the v4 columns, and old rows still load. */
class ChannelEntityMapperTest {

    private val channel = Channel(
        id = "channel-x~2",
        name = "Channel X HD",
        url = "https://cdn.example.com/x.mpd",
        logoUrl = "https://example.com/x.png",
        categoryId = "Entertainment",
        playlistId = "p",
        isFavorite = true,
        number = 101,
        groups = listOf("Entertainment", "HD Channels"),
        isRadio = true,
        isVod = true,
        headers = mapOf("User-Agent" to "UA", "Referer" to "https://example.com/"),
        mimeType = "application/dash+xml",
        drm = DrmSpec(
            DrmSystem.CLEARKEY,
            clearKeys = listOf(ClearKey("000102030405060708090a0b0c0d0e0f", "00112233445566778899aabbccddeeff"))
        ),
        catchup = CatchupSpec(CatchupMode.APPEND, "&cutv={Y}", 3, -1.5),
        epgShiftHours = -4.5,
        sortIndex = 7,
    )

    @Test
    fun channelRoundTrip() {
        assertEquals(channel, channel.toChannelEntity().toChannel())
        assertEquals("channel-x", channel.guideId)
    }

    @Test
    fun preV4RowLoadsWithDefaults() {
        val old = ChannelEntity(
            id = "a", name = "A", url = "https://cdn.example.com/a.m3u8",
            logoUrl = null, categoryId = "News", playlistId = "p",
        )
        val loaded = old.toChannel()
        assertEquals(listOf("News"), loaded.groups)
        assertTrue(loaded.headers.isEmpty())
        assertEquals(null, loaded.drm)
    }

    @Test
    fun damagedJsonDoesNotLoseTheChannel() {
        val loaded = channel.toChannelEntity().copy(drmJson = "{not json", headersJson = "[]").toChannel()
        assertEquals(null, loaded.drm)
        assertTrue(loaded.headers.isEmpty())
        assertEquals("Channel X HD", loaded.name)
    }

    @Test
    fun mediaItemCarriesHeadersDrmAndRadio() {
        val item = channel.toMediaItem()
        assertEquals(channel.headers, item.headers)
        assertEquals(channel.drm, item.drm)
        assertTrue(item.isRadio)
        assertEquals("application/dash+xml", item.mimeType)
    }

    @Test
    fun playlistRoundTrip() {
        val playlist = Playlist(
            id = "file:1", name = "My file", url = "file:list.m3u", lastUpdated = 5,
            epgUrl = "https://a.example/1.xml",
            sourceType = PlaylistSourceType.FILE,
            epgUrls = listOf("https://a.example/1.xml", "https://a.example/2.xml"),
            format = "M3U",
        )
        assertEquals(playlist, playlist.toPlaylistEntity().toPlaylist())

        val preV4 = PlaylistEntity("id", "n", "https://example.com/p.m3u", 1, "UNKNOWN", "https://a.example/g.xml")
        assertEquals(PlaylistSourceType.URL, preV4.toPlaylist().sourceType)
        assertEquals(listOf("https://a.example/g.xml"), preV4.toPlaylist().epgUrls)
    }
}
