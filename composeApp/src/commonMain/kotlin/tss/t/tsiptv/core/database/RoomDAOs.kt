package tss.t.tsiptv.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing playlist data in the database.
 */
@Dao
interface PlaylistDao {
    /**
     * Gets all playlists.
     *
     * @return A flow of all playlists
     */
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    /**
     * Gets a playlist by ID.
     *
     * @param id The ID of the playlist to get
     * @return The playlist with the given ID, or null if not found
     */
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    /**
     * Inserts or updates a playlist.
     *
     * @param playlist The playlist to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    /**
     * Inserts or updates multiple playlists.
     *
     * @param playlists The playlists to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    /**
     * Deletes a playlist.
     *
     * @param playlist The playlist to delete
     */
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    /**
     * Deletes a playlist by ID.
     *
     * @param id The ID of the playlist to delete
     */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)
}

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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    /**
     * Inserts or updates multiple channels.
     *
     * @param channels The channels to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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

/**
 * DAO for accessing category data in the database.
 */
@Dao
interface CategoryDao {
    /**
     * Gets all categories.
     *
     * @return A flow of all categories
     */
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /**
     * Gets a category by ID.
     *
     * @param id The ID of the category to get
     * @return The category with the given ID, or null if not found
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    /**
     * Gets categories by playlist.
     *
     * @param playlistId The ID of the playlist to get categories for
     * @return A flow of categories in the given playlist
     */
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId")
    suspend fun getCategoriesByPlaylist(playlistId: String): List<CategoryEntity>

    /**
     * Inserts or updates a category.
     *
     * @param category The category to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    /**
     * Inserts or updates multiple categories.
     *
     * @param categories The categories to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    /**
     * Deletes a category.
     *
     * @param category The category to delete
     */
    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    /**
     * Deletes a category by ID.
     *
     * @param id The ID of the category to delete
     */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)

    /**
     * Deletes all categories for a playlist.
     *
     * @param playlistId The ID of the playlist to delete categories for
     */
    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteCategoriesByPlaylist(playlistId: String)
}

/**
 * DAO for accessing channel attribute data in the database.
 */
@Dao
interface ChannelAttributeDao {
    /**
     * Gets all attributes for a channel.
     *
     * @param channelId The ID of the channel to get attributes for
     * @return A flow of attributes for the given channel
     */
    @Query("SELECT * FROM channel_attributes WHERE channelId = :channelId")
    fun getAttributesForChannel(channelId: String): Flow<List<ChannelAttributeEntity>>

    /**
     * Inserts or updates a channel attribute.
     *
     * @param attribute The attribute to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttribute(attribute: ChannelAttributeEntity)

    /**
     * Inserts or updates multiple channel attributes.
     *
     * @param attributes The attributes to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttributes(attributes: List<ChannelAttributeEntity>)

    /**
     * Deletes a channel attribute.
     *
     * @param attribute The attribute to delete
     */
    @Delete
    suspend fun deleteAttribute(attribute: ChannelAttributeEntity)

    /**
     * Deletes all attributes for a channel.
     *
     * @param channelId The ID of the channel to delete attributes for
     */
    @Query("DELETE FROM channel_attributes WHERE channelId = :channelId")
    suspend fun deleteAttributesForChannel(channelId: String)
}
