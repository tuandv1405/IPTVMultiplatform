package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.ChannelHistoryEntity
import tss.t.tsiptv.core.database.entity.ChannelWithHistory

/**
 * DAO for accessing channel history data in the database.
 */
@Dao
interface ChannelHistoryDao {
    /**
     * Inserts or updates a channel history entry.
     * If a record with the same channelId and playlistId exists, it will be replaced.
     *
     * @param channelHistory The channel history to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateChannelHistory(channelHistory: ChannelHistoryEntity)

    /**
     * Updates the total played time for a channel.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     * @param additionalTimeMs The additional time to add to the total played time
     * @param newTimestamp The new timestamp for last played
     */
    @Query("""
        UPDATE channel_history 
        SET totalPlayedTimeMs = totalPlayedTimeMs + :additionalTimeMs,
            lastPlayedTimestamp = :newTimestamp
        WHERE channelId = :channelId AND playlistId = :playlistId
    """)
    suspend fun updatePlayedTime(
        channelId: String,
        playlistId: String,
        additionalTimeMs: Long,
        newTimestamp: Long
    )

    /**
     * Increments the play count for a channel.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     * @param newTimestamp The new timestamp for last played
     */
    @Query("""
        UPDATE channel_history 
        SET playCount = playCount + 1,
            lastPlayedTimestamp = :newTimestamp
        WHERE channelId = :channelId AND playlistId = :playlistId
    """)
    suspend fun incrementPlayCount(
        channelId: String,
        playlistId: String,
        newTimestamp: Long
    )

    /**
     * Updates the current position and total duration for a channel.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     * @param currentPositionMs The current playback position in milliseconds
     * @param totalDurationMs The total duration of the media in milliseconds
     * @param newTimestamp The new timestamp for last played
     */
    @Query("""
        UPDATE channel_history 
        SET currentPositionMs = :currentPositionMs,
            totalDurationMs = :totalDurationMs,
            lastPlayedTimestamp = :newTimestamp
        WHERE channelId = :channelId AND playlistId = :playlistId
    """)
    suspend fun updatePositionAndDuration(
        channelId: String,
        playlistId: String,
        currentPositionMs: Long,
        totalDurationMs: Long,
        newTimestamp: Long
    )

    /**
     * Gets all played channels for a specific playlist.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of channel history entries for the playlist
     */
    @Query("SELECT * FROM channel_history WHERE playlistId = :playlistId ORDER BY lastPlayedTimestamp DESC")
    fun getAllPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelHistoryEntity>>

    /**
     * Gets the last played channel for a specific playlist.
     *
     * @param playlistId The ID of the playlist
     * @return The most recently played channel history entry, or null if none exists
     */
    @Query("SELECT * FROM channel_history WHERE playlistId = :playlistId ORDER BY lastPlayedTimestamp DESC LIMIT 1")
    suspend fun getLastPlayedChannelInPlaylist(playlistId: String): ChannelHistoryEntity?

    /**
     * Gets the top 3 most played channels for a specific playlist, sorted by total played time.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of the top 3 most played channel history entries
     */
    @Query("SELECT * FROM channel_history WHERE playlistId = :playlistId ORDER BY totalPlayedTimeMs DESC LIMIT 3")
    fun getTop3MostPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelHistoryEntity>>

    /**
     * Gets a specific channel history entry.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     * @return The channel history entry, or null if not found
     */
    @Query("SELECT * FROM channel_history WHERE channelId = :channelId AND playlistId = :playlistId")
    suspend fun getChannelHistory(channelId: String, playlistId: String): ChannelHistoryEntity?

    /**
     * Deletes all channel history for a specific playlist.
     *
     * @param playlistId The ID of the playlist
     */
    @Query("DELETE FROM channel_history WHERE playlistId = :playlistId")
    suspend fun deleteChannelHistoryByPlaylist(playlistId: String)

    /**
     * Deletes a specific channel history entry.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     */
    @Query("DELETE FROM channel_history WHERE channelId = :channelId AND playlistId = :playlistId")
    suspend fun deleteChannelHistory(channelId: String, playlistId: String)

    /**
     * Gets the last watched channel with complete channel information using JOIN.
     * This query joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the most recently watched channel.
     *
     * @param playlistId The ID of the playlist (optional, if null gets from all playlists)
     * @return The last watched channel with complete information, or null if no history exists
     */
    @Query("""
        SELECT 
            c.id as channelId,
            c.name as channelName,
            c.url as channelUrl,
            c.logoUrl as logoUrl,
            c.categoryId as categoryId,
            c.playlistId as playlistId,
            c.isFavorite as isFavorite,
            c.lastWatched as lastWatched,
            ch.id as historyId,
            ch.lastPlayedTimestamp as lastPlayedTimestamp,
            ch.totalPlayedTimeMs as totalPlayedTimeMs,
            ch.playCount as playCount,
            ch.currentPositionMs as currentPositionMs,
            ch.totalDurationMs as totalDurationMs
        FROM channel_history ch
        INNER JOIN channels c ON ch.channelId = c.id
        WHERE (:playlistId IS NULL OR ch.playlistId = :playlistId)
        ORDER BY ch.lastPlayedTimestamp DESC
        LIMIT 1
    """)
    suspend fun getLastWatchedChannelWithDetails(playlistId: String? = null): ChannelWithHistory?

    /**
     * Gets all watched channels with complete channel information using JOIN.
     * This query joins the Channel and ChannelHistory tables to get both channel details
     * and history information for all watched channels, ordered by most recent first.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of watched channels with complete information
     */
    @Query("""
        SELECT 
            c.id as channelId,
            c.name as channelName,
            c.url as channelUrl,
            c.logoUrl as logoUrl,
            c.categoryId as categoryId,
            c.playlistId as playlistId,
            c.isFavorite as isFavorite,
            c.lastWatched as lastWatched,
            ch.id as historyId,
            ch.lastPlayedTimestamp as lastPlayedTimestamp,
            ch.totalPlayedTimeMs as totalPlayedTimeMs,
            ch.playCount as playCount,
            ch.currentPositionMs as currentPositionMs,
            ch.totalDurationMs as totalDurationMs
        FROM channel_history ch
        INNER JOIN channels c ON ch.channelId = c.id
        WHERE ch.playlistId = :playlistId
        ORDER BY ch.lastPlayedTimestamp DESC
    """)
    fun getAllWatchedChannelsWithDetails(playlistId: String): Flow<List<ChannelWithHistory>>

    /**
     * Gets the last top 3 watched channels with complete channel information using JOIN.
     * This query joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the 3 most recently watched channels.
     *
     * @param playlistId The ID of the playlist (optional, if null gets from all playlists)
     * @return A flow of the top 3 most recently watched channels with complete information
     */
    @Query("""
        SELECT 
            c.id as channelId,
            c.name as channelName,
            c.url as channelUrl,
            c.logoUrl as logoUrl,
            c.categoryId as categoryId,
            c.playlistId as playlistId,
            c.isFavorite as isFavorite,
            c.lastWatched as lastWatched,
            ch.id as historyId,
            ch.lastPlayedTimestamp as lastPlayedTimestamp,
            ch.totalPlayedTimeMs as totalPlayedTimeMs,
            ch.playCount as playCount,
            ch.currentPositionMs as currentPositionMs,
            ch.totalDurationMs as totalDurationMs
        FROM channel_history ch
        INNER JOIN channels c ON ch.channelId = c.id
        WHERE (:playlistId IS NULL OR ch.playlistId = :playlistId)
        ORDER BY ch.lastPlayedTimestamp DESC
        LIMIT 3
    """)
    fun getLastTop3WatchedChannelsWithDetails(playlistId: String? = null): Flow<List<ChannelWithHistory>>

    /**
     * Gets the last played channel with complete channel information using JOIN.
     * This query joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the most recently played channel.
     *
     * @param playlistId The ID of the playlist
     * @return The most recently played channel with complete information, or null if none exists
     */
    @Query("""
        SELECT 
            c.id as channelId,
            c.name as channelName,
            c.url as channelUrl,
            c.logoUrl as logoUrl,
            c.categoryId as categoryId,
            c.playlistId as playlistId,
            c.isFavorite as isFavorite,
            c.lastWatched as lastWatched,
            ch.id as historyId,
            ch.lastPlayedTimestamp as lastPlayedTimestamp,
            ch.totalPlayedTimeMs as totalPlayedTimeMs,
            ch.playCount as playCount,
            ch.currentPositionMs as currentPositionMs,
            ch.totalDurationMs as totalDurationMs
        FROM channel_history ch
        INNER JOIN channels c ON ch.channelId = c.id
        WHERE ch.playlistId = :playlistId
        ORDER BY ch.lastPlayedTimestamp DESC
        LIMIT 1
    """)
    suspend fun getLastPlayedChannelInPlaylistWithDetails(playlistId: String): ChannelWithHistory?

    /**
     * Gets the top 3 most played channels with complete channel information using JOIN.
     * This query joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the 3 most played channels, sorted by total played time.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of the top 3 most played channels with complete information
     */
    @Query("""
        SELECT 
            c.id as channelId,
            c.name as channelName,
            c.url as channelUrl,
            c.logoUrl as logoUrl,
            c.categoryId as categoryId,
            c.playlistId as playlistId,
            c.isFavorite as isFavorite,
            c.lastWatched as lastWatched,
            ch.id as historyId,
            ch.lastPlayedTimestamp as lastPlayedTimestamp,
            ch.totalPlayedTimeMs as totalPlayedTimeMs,
            ch.playCount as playCount,
            ch.currentPositionMs as currentPositionMs,
            ch.totalDurationMs as totalDurationMs
        FROM channel_history ch
        INNER JOIN channels c ON ch.channelId = c.id
        WHERE ch.playlistId = :playlistId
        ORDER BY ch.totalPlayedTimeMs DESC
        LIMIT 3
    """)
    fun getTop3MostPlayedChannelsInPlaylistWithDetails(playlistId: String): Flow<List<ChannelWithHistory>>

    /**
     * Gets the most played channel with complete channel information using JOIN.
     * This query joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the most played channel, sorted by total played time.
     *
     * @param playlistId The ID of the playlist
     * @return The most played channel with complete information, or null if none exists
     */
    @Query("""
        SELECT 
            c.id as channelId,
            c.name as channelName,
            c.url as channelUrl,
            c.logoUrl as logoUrl,
            c.categoryId as categoryId,
            c.playlistId as playlistId,
            c.isFavorite as isFavorite,
            c.lastWatched as lastWatched,
            ch.id as historyId,
            ch.lastPlayedTimestamp as lastPlayedTimestamp,
            ch.totalPlayedTimeMs as totalPlayedTimeMs,
            ch.playCount as playCount,
            ch.currentPositionMs as currentPositionMs,
            ch.totalDurationMs as totalDurationMs
        FROM channel_history ch
        INNER JOIN channels c ON ch.channelId = c.id
        WHERE ch.playlistId = :playlistId
        ORDER BY ch.totalPlayedTimeMs DESC
        LIMIT 1
    """)
    suspend fun getMostPlayedChannelInPlaylistWithDetails(playlistId: String): ChannelWithHistory?
}
