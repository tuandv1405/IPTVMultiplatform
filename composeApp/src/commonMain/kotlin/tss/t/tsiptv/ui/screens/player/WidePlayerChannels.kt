package tss.t.tsiptv.ui.screens.player

import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.model.Channel

/**
 * The channel column on the right of the desktop wide player (`WidePlayerLayout`).
 *
 * - The playing channel's group, or every channel of the playlist when the group has none.
 * - A group under [MIN_GROUP_SIZE] channels is followed by the recently watched channels (most recent
 *   first), then every other channel of the playlist, so the column is never nearly empty.
 * - A channel is listed once.
 */
object WidePlayerChannels {

    const val MIN_GROUP_SIZE = 10

    /**
     * @param group the playing channel's group.
     * @param recent watch history of the playlist, most recent first.
     * @param all every channel of the playlist shown on Home.
     * @param playlistId the playing channel's playlist: [recent] and [all] of another playlist are not used.
     */
    fun build(
        group: List<Channel>,
        recent: List<ChannelWithHistory>,
        all: List<Channel>,
        playlistId: String?,
    ): List<Channel> {
        val groupChannels = group.distinctBy { it.id }
        if (groupChannels.size >= MIN_GROUP_SIZE) return groupChannels
        val playlistChannels = all.filter { it.playlistId == playlistId }
        if (groupChannels.isEmpty()) return playlistChannels.distinctBy { it.id }

        // History rows carry only part of a channel: the full row comes from the playlist.
        val byId = playlistChannels.associateBy { it.id }
        val recentChannels = recent.asSequence()
            .filter { it.playlistId == playlistId }
            .mapNotNull { byId[it.channelId] }
        return (groupChannels.asSequence() + recentChannels + playlistChannels.asSequence())
            .distinctBy { it.id }
            .toList()
    }
}
