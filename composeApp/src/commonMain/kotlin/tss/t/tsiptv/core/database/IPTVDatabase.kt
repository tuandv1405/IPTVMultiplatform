package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Interface for the IPTV database.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface IPTVDatabase {
    /**
     * Gets all channels.
     *
     * @return A flow of all channels
     */
    fun getAllChannels(): Flow<List<Channel>>

    /**
     * Gets all channels.
     *
     * @return A flow of all channels
     */
    fun getAllChannelsByPlayListId(playListId: String): Flow<List<Channel>>

    /**
     * Gets all categories.
     *
     * @return A flow of all categories
     */
    suspend fun getCategoriesByPlaylist(playlistId: String): List<Category>

    /**
     * Gets a channel by ID.
     *
     * @param id The ID of the channel to get
     * @return The channel with the given ID, or null if not found
     */
    suspend fun getChannelById(id: String): Channel?

    /**
     * Gets channels by category.
     *
     * @param categoryId The ID of the category to get channels for
     * @return A flow of channels in the given category
     */
    fun getChannelsByCategory(categoryId: String): Flow<List<Channel>>

    /**
     * Searches for channels by name.
     *
     * @param query The search query
     * @return A flow of channels matching the search query
     */
    fun searchChannels(query: String): Flow<List<Channel>>

    /**
     * Inserts or updates a channel.
     *
     * @param channel The channel to insert or update
     */
    suspend fun insertChannel(channel: Channel)

    /**
     * Inserts or updates multiple channels.
     *
     * @param channels The channels to insert or update
     */
    suspend fun insertChannels(channels: List<Channel>)

    /**
     * Deletes a channel.
     *
     * @param channel The channel to delete
     */
    suspend fun deleteChannel(channel: Channel)

    /**
     * Deletes a channel by ID.
     *
     * @param id The ID of the channel to delete
     */
    suspend fun deleteChannelById(id: String)

    /**
     * Gets all categories.
     *
     * @return A flow of all categories
     */
    fun getAllCategories(): Flow<List<Category>>

    /**
     * Gets all categories.
     *
     * @return A flow of all categories
     */
    suspend fun getAllCategoriesByPlayListId(playListId: String): List<Category>

    /**
     * Gets a category by ID.
     *
     * @param id The ID of the category to get
     * @return The category with the given ID, or null if not found
     */
    suspend fun getCategoryById(id: String): Category?

    /**
     * Inserts or updates a category.
     *
     * @param category The category to insert or update
     */
    suspend fun insertCategory(category: Category)

    /**
     * Inserts or updates multiple categories.
     *
     * @param categories The categories to insert or update
     */
    suspend fun insertCategories(categories: List<Category>)

    /**
     * Deletes a category.
     *
     * @param category The category to delete
     */
    suspend fun deleteCategory(category: Category)

    /**
     * Deletes a category by ID.
     *
     * @param id The ID of the category to delete
     */
    suspend fun deleteCategoryById(id: String)

    /**
     * Gets all playlists.
     *
     * @return A flow of all playlists
     */
    fun getAllPlaylists(): Flow<List<Playlist>>

    /**
     * Gets a playlist by ID.
     *
     * @param id The ID of the playlist to get
     * @return The playlist with the given ID, or null if not found
     */
    suspend fun getPlaylistById(id: String): Playlist?

    /**
     * Inserts or updates a playlist.
     *
     * @param playlist The playlist to insert or update
     */
    suspend fun insertPlaylist(playlist: Playlist)

    /**
     * Inserts or updates multiple playlists.
     *
     * @param playlists The playlists to insert or update
     */
    suspend fun insertPlaylists(playlists: List<Playlist>)

    /**
     * Deletes a playlist.
     *
     * @param playlist The playlist to delete
     */
    suspend fun deletePlaylist(playlist: Playlist)

    /**
     * Deletes a playlist by ID.
     *
     * @param id The ID of the playlist to delete
     */
    suspend fun deletePlaylistById(id: String)

    suspend fun deleteChannelsInPlaylist(playlistId: String)

    /**
     * Clears all data from the database.
     */
    suspend fun clearAllData()
}

/**
 * Data class representing a channel.
 *
 * @property id The unique ID of the channel
 * @property name The name of the channel
 * @property url The URL of the channel
 * @property logoUrl The URL of the channel's logo
 * @property categoryId The ID of the category the channel belongs to
 * @property playlistId The ID of the playlist the channel belongs to
 * @property isFavorite Whether the channel is a favorite
 * @property lastWatched The timestamp when the channel was last watched, or null if never watched
 */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val playlistId: String,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null
)

/**
 * Data class representing a category.
 *
 * @property id The unique ID of the category
 * @property name The name of the category
 * @property playlistId The ID of the playlist the category belongs to
 */
data class Category(
    val id: String,
    val name: String,
    val playlistId: String
)

/**
 * Data class representing a playlist.
 *
 * @property id The unique ID of the playlist
 * @property name The name of the playlist
 * @property url The URL of the playlist
 * @property lastUpdated The timestamp when the playlist was last updated
 */
data class Playlist(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long,
)
