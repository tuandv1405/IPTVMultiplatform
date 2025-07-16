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
     * Gets all channels.
     *
     * @return A flow of all channels
     */
    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    /**
     * Gets a channel by ID.
     *
     * @param id The ID of the channel to get
     * @return The channel with the given ID, or null if not found
     */
    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getChannelById(id: String): ChannelEntity?

    /**
     * Gets channels by playlist ID.
     *
     * @param playlistId The ID of the playlist to get channels for
     * @return A flow of channels in the given playlist
     */
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    fun getChannelsInPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    /**
     * Gets channels by category.
     *
     * @param categoryId The ID of the category to get channels for
     * @return A flow of channels in the given category
     */
    @Query("SELECT * FROM channels WHERE categoryId = :categoryId")
    fun getChannelsByCategory(categoryId: String): Flow<List<ChannelEntity>>

    /**
     * Gets channels by playlist.
     *
     * @param playlistId The ID of the playlist to get channels for
     * @return A flow of channels in the given playlist
     */
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    fun getChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    /**
     * Searches for channels by name.
     *
     * @param query The search query
     * @return A flow of channels matching the search query
     */
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%'")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    /**
     * Inserts or updates a channel.
     *
     * @param channel The channel to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    /**
     * Inserts or updates multiple channels.
     *
     * @param channels The channels to insert or update
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
    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteChannelById(id: String)

    /**
     * Deletes all channels for a playlist.
     *
     * @param playlistId The ID of the playlist to delete channels for
     */
    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: String)
}