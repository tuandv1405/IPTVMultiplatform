package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.ChannelHistory
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.core.database.entity.ChannelWithHistory

/**
 * Interface for the IPTV database.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface IPTVDatabase {
    /**
     * Gets all channel.
     *
     * @return A flow of all channel
     */
    fun getAllChannels(): Flow<List<Channel>>

    /**
     * Gets all channel.
     *
     * @return A flow of all channel
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
     * Gets channel by category.
     *
     * @param categoryId The ID of the category to get channel for
     * @return A flow of channel in the given category
     */
    fun getChannelsByCategory(categoryId: String): Flow<List<Channel>>

    /**
     * Searches for channel by name.
     *
     * @param query The search query
     * @return A flow of channel matching the search query
     */
    fun searchChannels(query: String): Flow<List<Channel>>

    /**
     * Inserts or updates a channel.
     *
     * @param channel The channel to insert or update
     */
    suspend fun insertChannel(channel: Channel)

    /**
     * Inserts or updates multiple channel.
     *
     * @param channels The channel to insert or update
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
    fun getAllPrograms(): Flow<List<IPTVProgram>>

    suspend fun countValidPrograms(playlistId: String): Int

    /**
     * Gets a program by ID.
     *
     * @param id The ID of the program to get
     * @return The program with the given ID, or null if not found
     */
    suspend fun getProgramById(id: String): IPTVProgram?

    /**
     * Gets programs for a channel.
     *
     * @param channelId The ID of the channel
     * @return A list of programs for the channel
     */
    suspend fun getProgramsForChannel(channelId: String): List<IPTVProgram>

    /**
     * Gets programs for a channel within a time range.
     *
     * @param channelId The ID of the channel
     * @param startTime The start time of the range
     * @param endTime The end time of the range
     * @return A list of programs for the channel within the time range
     */
    suspend fun getProgramsForChannelInTimeRange(channelId: String, startTime: Long, endTime: Long): List<IPTVProgram>

    /**
     * Gets current and upcoming programs for a channel.
     *
     * @param channelId The ID of the channel
     * @param currentTime The current time
     * @return A list of current and upcoming programs for the channel
     */
    suspend fun getCurrentAndUpcomingProgramsForChannel(channelId: String, currentTime: Long): List<IPTVProgram>

    /**
     * Gets the current program for a channel.
     *
     * @param channelId The ID of the channel
     * @param currentTime The current time
     * @return The current program, or null if not found
     */
    suspend fun getCurrentProgramForChannel(channelId: String, currentTime: Long): IPTVProgram?

    /**
     * Inserts or updates a program.
     *
     * @param program The program to insert or update
     * @param playlistId The ID of the playlist the program belongs to
     */
    suspend fun insertProgram(program: IPTVProgram, playlistId: String)

    /**
     * Inserts or updates multiple programs.
     *
     * @param programs The programs to insert or update
     * @param playlistId The ID of the playlist the programs belong to
     */
    suspend fun insertPrograms(programs: List<IPTVProgram>, playlistId: String)

    /**
     * Deletes a program.
     *
     * @param program The program to delete
     */
    suspend fun deleteProgram(program: IPTVProgram)

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

    // Channel History methods

    /**
     * Records or updates channel play history.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     * @param timestamp The timestamp when the channel was played
     */
    suspend fun recordChannelPlay(channelId: String, playlistId: String, timestamp: Long)

    /**
     * Updates the total played time for a channel.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     * @param additionalTimeMs The additional time to add to the total played time
     * @param timestamp The current timestamp
     */
    suspend fun updateChannelPlayTime(channelId: String, playlistId: String, additionalTimeMs: Long, timestamp: Long)

    /**
     * Updates the current position and total duration for a channel.
     *
     * @param channelId The ID of the channel
     * @param playlistId The ID of the playlist
     * @param currentPositionMs The current playback position in milliseconds
     * @param totalDurationMs The total duration of the media in milliseconds
     * @param timestamp The current timestamp
     */
    suspend fun updateChannelPositionAndDuration(channelId: String, playlistId: String, currentPositionMs: Long, totalDurationMs: Long, timestamp: Long)

    /**
     * Gets all played channel for a specific playlist.
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
     * Gets the top 3 most played channel for a specific playlist with complete channel information.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the top 3 most played channel, sorted by total played time.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of the top 3 most played channel with complete information
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
     * Gets all watched channel with complete channel information using JOIN.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for all watched channel, ordered by most recent first.
     *
     * @param playlistId The ID of the playlist
     * @return A flow of watched channel with complete information
     */
    fun getAllWatchedChannelsWithDetails(playlistId: String): Flow<List<ChannelWithHistory>>

    /**
     * Gets the last top 3 watched channel with complete channel information using JOIN.
     * This method joins the Channel and ChannelHistory tables to get both channel details
     * and history information for the 3 most recently watched channel.
     *
     * @param playlistId The ID of the playlist (optional, if null gets from all playlists)
     * @return A flow of the top 3 most recently watched channel with complete information
     */
    fun getLastTop3WatchedChannelsWithDetails(playlistId: String? = null): Flow<List<ChannelWithHistory>>

    /**
     * Clears all data from the database.
     */
    suspend fun clearAllData()
}
