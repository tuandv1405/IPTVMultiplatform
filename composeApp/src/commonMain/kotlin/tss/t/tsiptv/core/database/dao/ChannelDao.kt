package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.ChannelEntity

/**
 * DAO for accessing channel data in the database.
 *
 * Lists follow the playlist's own order (`sortIndex`). Rows written before v4 all have
 * `sortIndex = 0` until their playlist is re-parsed, so `rowid` keeps them in insertion order.
 */
@Dao
interface ChannelDao {
    /**
     * Gets all channel.
     *
     * @return A flow of all channel
     */
    @Query("SELECT * FROM channel ORDER BY sortIndex, rowid")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    /**
     * Gets a channel by ID.
     *
     * @param id The ID of the channel to get
     * @return The channel with the given ID, or null if not found
     */
    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun getChannelById(id: String): ChannelEntity?

    /**
     * Gets channel by playlist ID.
     *
     * @param playlistId The ID of the playlist to get channel for
     * @return A flow of channel in the given playlist
     */
    @Query("SELECT * FROM channel WHERE playlistId = :playlistId ORDER BY sortIndex, rowid")
    fun getChannelsInPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    /** One-shot read of a playlist's channels, for use inside a transaction. */
    @Query("SELECT * FROM channel WHERE playlistId = :playlistId ORDER BY sortIndex, rowid")
    suspend fun getChannelsInPlaylistOnce(playlistId: String): List<ChannelEntity>

    /**
     * Gets channel by category: its primary group, or any of its other groups
     * (`groupsJson` is a JSON array of titles, so the title appears quoted).
     *
     * @param categoryId The ID of the category to get channel for
     * @param escapedCategoryId [categoryId] with `\`, `%` and `_` escaped by `\`, so a group
     *   name containing a LIKE wildcard matches only itself
     * @return A flow of channel in the given category
     */
    @Query(
        """
        SELECT * FROM channel
        WHERE (categoryId = :categoryId OR groupsJson LIKE '%"' || :escapedCategoryId || '"%' ESCAPE '\')
            AND (:playlistId IS NULL OR playlistId = :playlistId)
        ORDER BY sortIndex, rowid
    """
    )
    fun getChannelsByCategory(categoryId: String, escapedCategoryId: String, playlistId: String?): Flow<List<ChannelEntity>>

    /**
     * Gets channel by playlist.
     *
     * @param playlistId The ID of the playlist to get channel for
     * @return A flow of channel in the given playlist
     */
    @Query("SELECT * FROM channel WHERE playlistId = :playlistId ORDER BY sortIndex, rowid")
    fun getChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    /**
     * Searches for channel by name.
     *
     * @param query The search query
     * @return A flow of channel matching the search query
     */
    @Query("SELECT * FROM channel WHERE name LIKE '%' || :query || '%' ORDER BY sortIndex, rowid")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    /**
     * Inserts or updates a channel.
     *
     * @param channel The channel to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    /**
     * Inserts or updates multiple channel.
     *
     * @param channels The channel to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    /**
     * Deletes a channel.
     *
     * @param channel The channel to delete
     */
    @Delete
    suspend fun deleteChannel(channel: ChannelEntity)

    /**
     * Deletes a channel by ID.
     *
     * @param id The ID of the channel to delete
     */
    @Query("DELETE FROM channel WHERE id = :id")
    suspend fun deleteChannelById(id: String)

    /**
     * Deletes all channel for a playlist.
     *
     * @param playlistId The ID of the playlist to delete channel for
     */
    @Query("DELETE FROM channel WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: String)

    /** Which of [ids] belong to a playlist other than [playlistId] (call with at most 900 ids). */
    @Query("SELECT id FROM channel WHERE id IN (:ids) AND playlistId != :playlistId")
    suspend fun idsOwnedElsewhere(ids: List<String>, playlistId: String): List<String>
}
