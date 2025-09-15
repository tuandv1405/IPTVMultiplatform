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
 */
@Dao
interface ChannelDao {
    /**
     * Gets all channel.
     *
     * @return A flow of all channel
     */
    @Query("SELECT * FROM channel")
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
    @Query("SELECT * FROM channel WHERE playlistId = :playlistId")
    fun getChannelsInPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    /**
     * Gets channel by category.
     *
     * @param categoryId The ID of the category to get channel for
     * @return A flow of channel in the given category
     */
    @Query("SELECT * FROM channel WHERE categoryId = :categoryId")
    fun getChannelsByCategory(categoryId: String): Flow<List<ChannelEntity>>

    /**
     * Gets channel by playlist.
     *
     * @param playlistId The ID of the playlist to get channel for
     * @return A flow of channel in the given playlist
     */
    @Query("SELECT * FROM channel WHERE playlistId = :playlistId")
    fun getChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    /**
     * Searches for channel by name.
     *
     * @param query The search query
     * @return A flow of channel matching the search query
     */
    @Query("SELECT * FROM channel WHERE name LIKE '%' || :query || '%'")
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
}