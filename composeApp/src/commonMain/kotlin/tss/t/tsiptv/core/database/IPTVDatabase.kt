package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.Program

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
     * Gets all programs.
     *
     * @return A flow of all programs
     */
    fun getAllPrograms(): Flow<List<Program>>

    /**
     * Gets a program by ID.
     *
     * @param id The ID of the program to get
     * @return The program with the given ID, or null if not found
     */
    suspend fun getProgramById(id: String): Program?

    /**
     * Gets programs for a channel.
     *
     * @param channelId The ID of the channel
     * @return A list of programs for the channel
     */
    suspend fun getProgramsForChannel(channelId: String): List<Program>

    /**
     * Gets programs for a channel within a time range.
     *
     * @param channelId The ID of the channel
     * @param startTime The start time of the range
     * @param endTime The end time of the range
     * @return A list of programs for the channel within the time range
     */
    suspend fun getProgramsForChannelInTimeRange(channelId: String, startTime: Long, endTime: Long): List<Program>

    /**
     * Gets current and upcoming programs for a channel.
     *
     * @param channelId The ID of the channel
     * @param currentTime The current time
     * @return A list of current and upcoming programs for the channel
     */
    suspend fun getCurrentAndUpcomingProgramsForChannel(channelId: String, currentTime: Long): List<Program>

    /**
     * Gets the current program for a channel.
     *
     * @param channelId The ID of the channel
     * @param currentTime The current time
     * @return The current program, or null if not found
     */
    suspend fun getCurrentProgramForChannel(channelId: String, currentTime: Long): Program?

    /**
     * Inserts or updates a program.
     *
     * @param program The program to insert or update
     */
    suspend fun insertProgram(program: Program)

    /**
     * Inserts or updates multiple programs.
     *
     * @param programs The programs to insert or update
     */
    suspend fun insertPrograms(programs: List<Program>)

    /**
     * Deletes a program.
     *
     * @param program The program to delete
     */
    suspend fun deleteProgram(program: Program)

    /**
     * Deletes a program by ID.
     *
     * @param id The ID of the program to delete
     */
    suspend fun deleteProgramById(id: String)

    /**
     * Deletes all programs for a channel.
     *
     * @param channelId The ID of the channel
     */
    suspend fun deleteProgramsForChannel(channelId: String)

    /**
     * Deletes all programs for a playlist.
     *
     * @param playlistId The ID of the playlist
     */
    suspend fun deleteProgramsForPlaylist(playlistId: String)

    /**
     * Clears all data from the database.
     */
    suspend fun clearAllData()
}
