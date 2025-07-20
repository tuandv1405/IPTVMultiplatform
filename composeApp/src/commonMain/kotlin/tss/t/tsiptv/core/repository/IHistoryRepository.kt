package tss.t.tsiptv.core.repository

import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.model.ChannelHistory
import tss.t.tsiptv.core.database.entity.ChannelWithHistory

/**
 * Interface for channel history repository operations.
 */
interface IHistoryRepository {
    /**
     * Gets all played channels for a specific playlist.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of channel history entries for the playlist
     */
    fun getAllPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelHistory>>

    /**
     * Gets the last played channel for a specific playlist with complete channel information.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the most recently played channel.
     *
     * @param playlistId The ID of the playlist
     * @return The most recently played channel with complete information, or null if none exists
     */
    suspend fun getLastPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory?

    /**
     * Gets the top 3 most played channels for a specific playlist with complete channel information.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the top 3 most played channels, sorted by total played time.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of the top 3 most played channels with complete information
     */
    fun getTop3MostPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelWithHistory>>

    /**
     * Gets the most played channel for a specific playlist with complete channel information.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the most played channel, sorted by total played time.
     *
     * @param playlistId The ID of the playlist
     * @return The most played channel with complete information, or null if none exists
     */
    suspend fun getMostPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory?

    /**
     * Gets the last watched channel with complete channel information using JOIN.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the most recently watched channel.
     *
     * @param playlistId The ID of the playlist (optional, if null gets from all playlists)
     * @return The last watched channel with complete information, or null if no history exists
     */
    suspend fun getLastWatchedChannelWithDetails(playlistId: String? = null): ChannelWithHistory?

    /**
     * Gets all watched channels with complete channel information using JOIN.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for all watched channels, ordered by most recent first.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of watched channels with complete information
     */
    fun getAllWatchedChannelsWithDetails(playlistId: String): Flow<List<ChannelWithHistory>>

    /**
     * Gets the last top 3 watched channels with complete channel information using JOIN.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the 3 most recently watched channels.
     *
     * @param playlistId The ID of the playlist (optional, if null gets from all playlists)
     * @return A flow of the top 3 most recently watched channels with complete information
     */
    fun getLastTop3WatchedChannelsWithDetails(playlistId: String? = null): Flow<List<ChannelWithHistory>>
}
