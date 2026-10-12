package tss.t.tsiptv.ui.screens.player

import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.model.Channel
import kotlin.test.Test
import kotlin.test.assertEquals

class WidePlayerChannelsTest {

    private fun channel(id: String, playlistId: String = "p1") =
        Channel(id = id, name = "Channel $id", url = "https://example.com/$id.m3u8", playlistId = playlistId)

    private fun watched(id: String, playlistId: String = "p1") = ChannelWithHistory(
        channelId = id,
        channelName = "Channel $id",
        channelUrl = "https://example.com/$id.m3u8",
        logoUrl = null,
        categoryId = null,
        playlistId = playlistId,
        isFavorite = false,
        lastWatched = null,
        historyId = 0,
        lastPlayedTimestamp = 0,
        totalPlayedTimeMs = 0,
        playCount = 1,
        currentPositionMs = 0,
        totalDurationMs = 0,
    )

    private val all = (1..20).map { channel("c$it") }

    private fun ids(channels: List<Channel>) = channels.map { it.id }

    @Test
    fun aGroupOfTenOrMoreIsShownAlone() {
        val group = all.take(12)
        val result = WidePlayerChannels.build(group, listOf(watched("c15")), all, "p1")
        assertEquals(ids(group), ids(result))
    }

    @Test
    fun anEmptyGroupShowsEveryChannel() {
        val result = WidePlayerChannels.build(emptyList(), listOf(watched("c15")), all, "p1")
        assertEquals(ids(all), ids(result))
    }

    @Test
    fun aSmallGroupIsFollowedByRecentThenEveryOtherChannelOnce() {
        val group = listOf(channel("c3"), channel("c4"))
        val recent = listOf(watched("c9"), watched("c3"), watched("c1"))
        val result = WidePlayerChannels.build(group, recent, all, "p1")

        assertEquals(listOf("c3", "c4", "c9", "c1"), ids(result).take(4))
        assertEquals(ids(all).toSet(), ids(result).toSet())
        assertEquals(ids(result).size, ids(result).toSet().size)
    }

    @Test
    fun duplicatesInsideTheGroupAreDropped() {
        val group = listOf(channel("c3"), channel("c3"), channel("c4"))
        val result = WidePlayerChannels.build(group, emptyList(), all.take(4), "p1")
        assertEquals(listOf("c3", "c4", "c1", "c2"), ids(result))
    }

    @Test
    fun recentAndAllOfAnotherPlaylistAreNotUsed() {
        val group = listOf(channel("c3"))
        val other = listOf(channel("x1", "p2"), channel("x2", "p2"))
        val result = WidePlayerChannels.build(group, listOf(watched("x1", "p2")), all.take(2) + other, "p1")
        assertEquals(listOf("c3", "c1", "c2"), ids(result))
    }

    @Test
    fun aWatchedChannelNoLongerInThePlaylistIsSkipped() {
        val group = listOf(channel("c1"))
        val result = WidePlayerChannels.build(group, listOf(watched("gone")), all.take(2), "p1")
        assertEquals(listOf("c1", "c2"), ids(result))
    }
}
