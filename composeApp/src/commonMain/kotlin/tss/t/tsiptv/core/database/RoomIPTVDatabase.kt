package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.database.entity.ChannelAttributeEntity
import tss.t.tsiptv.core.database.entity.ChannelHistoryEntity
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.database.entity.toCategory
import tss.t.tsiptv.core.database.entity.toCategoryEntity
import tss.t.tsiptv.core.database.entity.toChannel
import tss.t.tsiptv.core.database.entity.toChannelEntity
import tss.t.tsiptv.core.database.entity.toChannelHistory
import tss.t.tsiptv.core.database.entity.toPlaylist
import tss.t.tsiptv.core.database.entity.toPlaylistEntity
import tss.t.tsiptv.core.database.entity.toIPTVProgram
import tss.t.tsiptv.core.database.entity.toProgramEntity
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.ChannelHistory
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.IPTVParserFactory
import kotlin.time.Duration.Companion.days

/**
 * Room implementation of IPTVDatabase.
 * This implementation uses Room to persist data between app restarts.
 *
 * @property database The Room database instance
 * @property networkClient The network client to use for fetching playlists
 */
class RoomIPTVDatabase(
    private val database: AppDatabase,
    private val networkClient: NetworkClient,
) : IPTVDatabase {

    override val playlistDao
        get() = database.playlistDao()
    override val channelDao
        get() = database.channelDao()
    override val categoryDao
        get() = database.categoryDao()
    override val channelAttributeDao
        get() = database.channelAttributeDao()
    override val programDao
        get() = database.programDao()
    override val channelHistoryDao
        get() = database.channelHistoryDao()

    override fun getAllChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels().map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override fun getAllChannelsByPlayListId(playListId: String): Flow<List<Channel>> {
        return channelDao.getChannelsInPlaylist(playListId).map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override suspend fun getCategoriesByPlaylist(playlistId: String): List<Category> {
        return categoryDao.getCategoriesByPlaylist(playlistId).map {
            it.toCategory()
        }
    }

    override suspend fun getChannelById(id: String): Channel? {
        return channelDao.getChannelById(id)?.toChannel()
    }

    override fun getChannelsByCategory(categoryId: String): Flow<List<Channel>> {
        return channelDao.getChannelsByCategory(categoryId).map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return channelDao.searchChannels(query).map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override suspend fun insertChannel(channel: Channel) {
        channelDao.insertChannel(channel.toChannelEntity())
    }

    override suspend fun insertChannels(channels: List<Channel>) {
        channelDao.insertChannels(channels.map { it.toChannelEntity() })
    }

    override suspend fun deleteChannel(channel: Channel) {
        channelDao.deleteChannel(channel.toChannelEntity())
    }

    override suspend fun deleteChannelById(id: String) {
        channelDao.deleteChannelById(id)
    }

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { categoryEntities ->
            categoryEntities.map { it.toCategory() }
        }
    }

    override suspend fun getAllCategoriesByPlayListId(playListId: String): List<Category> {
        return categoryDao.getCategoriesByPlaylist(playListId).map {
            it.toCategory()
        }
    }

    override suspend fun getCategoryById(id: String): Category? {
        return categoryDao.getCategoryById(id)?.toCategory()
    }

    override suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category.toCategoryEntity())
    }

    override suspend fun insertCategories(categories: List<Category>) {
        categoryDao.insertCategories(categories.map { it.toCategoryEntity() })
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category.toCategoryEntity())
    }

    override suspend fun deleteCategoryById(id: String) {
        categoryDao.deleteCategoryById(id)
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { playlistEntities ->
            playlistEntities.map { it.toPlaylist() }
        }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        val playlist = playlistDao.getPlaylistById(id)?.toPlaylist() ?: return null

        // Check if playlist needs to be updated (older than 1 day)
        val oneDayInMillis = 1.days.inWholeMilliseconds
        val currentTime = Clock.System.now().toEpochMilliseconds()

        if (currentTime - playlist.lastUpdated > oneDayInMillis) {
            // Playlist is too old, update it from the network
            try {
                val content = networkClient.get(playlist.url)
                val format = IPTVParserFactory.detectFormat(content)
                val parser = IPTVParserFactory.createParser(format)
                val parsedPlaylist = parser.parse(content)

                // Delete old channel and categories
                channelDao.deleteChannelsByPlaylist(playlist.id)
                categoryDao.deleteCategoriesByPlaylist(playlist.id)

                // Insert new playlist with updated timestamp
                val updatedPlaylist = playlist.copy(lastUpdated = currentTime)
                insertPlaylist(updatedPlaylist)

                // Insert new categories
                val categories = parsedPlaylist.groups.map { group ->
                    Category(
                        id = group.id,
                        name = group.title,
                        playlistId = playlist.id
                    )
                }
                insertCategories(categories)

                // Insert new channel
                val channels = parsedPlaylist.channels.map { channel ->
                    Channel(
                        id = channel.id,
                        name = channel.name,
                        url = channel.url,
                        logoUrl = channel.logoUrl,
                        categoryId = channel.groupTitle,
                        playlistId = playlist.id,
                        isFavorite = false,
                        lastWatched = null
                    )
                }
                insertChannels(channels)

                // Insert channel attributes
                for (channel in parsedPlaylist.channels) {
                    val attributes = channel.attributes.map { (key, value) ->
                        ChannelAttributeEntity(
                            channelId = channel.id,
                            attrKey = key,
                            attrValue = value
                        )
                    }
                    channelAttributeDao.insertAttributes(attributes)
                }

                return updatedPlaylist
            } catch (e: Exception) {
                println(e.message)
                e.printStackTrace()
                // If update fails, return the existing playlist
                return playlist
            }
        }

        return playlist
    }

    override suspend fun insertPlaylist(playlist: Playlist) {
        playlistDao.insertPlaylist(playlist.toPlaylistEntity())
    }

    override suspend fun insertPlaylists(playlists: List<Playlist>) {
        playlistDao.insertPlaylists(playlists.map { it.toPlaylistEntity() })
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist.toPlaylistEntity())
    }

    override suspend fun deletePlaylistById(id: String) {
        playlistDao.deletePlaylistById(id)
    }

    override suspend fun deleteChannelsInPlaylist(playlistId: String) {
        channelDao.deleteChannelsByPlaylist(playlistId)
    }

    override suspend fun clearAllData() {

    }

    // Program-related methods
    override fun getAllPrograms(): Flow<List<IPTVProgram>> {
        return programDao.getAllPrograms().map { programEntities ->
            programEntities.map { it.toIPTVProgram() }
        }
    }

    override suspend fun countValidPrograms(playlistId: String): Int {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        return programDao.countValidPrograms(playlistId, timestamp)
    }

    override suspend fun getProgramById(id: String): IPTVProgram? {
        return programDao.getProgramById(id)?.toIPTVProgram()
    }

    override suspend fun getProgramsForChannel(channelId: String): List<IPTVProgram> {
        return programDao.getProgramsForChannel(channelId).map { it.toIPTVProgram() }
    }

    override suspend fun getProgramsForChannelInTimeRange(
        channelId: String,
        startTime: Long,
        endTime: Long,
    ): List<IPTVProgram> {
        return programDao.getProgramsForChannelInTimeRange(channelId, startTime, endTime)
            .map { it.toIPTVProgram() }
    }

    override suspend fun getCurrentAndUpcomingProgramsForChannel(
        channelId: String,
        currentTime: Long,
    ): List<IPTVProgram> {
        return programDao.getCurrentAndUpcomingProgramsForChannel(channelId, currentTime)
            .map { it.toIPTVProgram() }
    }

    override suspend fun getCurrentProgramForChannel(
        channelId: String,
        currentTime: Long,
    ): IPTVProgram? {
        return programDao.getCurrentProgramForChannel(channelId, currentTime)?.toIPTVProgram()
    }

    override suspend fun insertProgram(program: IPTVProgram, playlistId: String) {
        programDao.insertProgram(program.toProgramEntity(playlistId))
    }

    override suspend fun insertPrograms(programs: List<IPTVProgram>, playlistId: String) {
        programDao.insertPrograms(programs.map { it.toProgramEntity(playlistId) })
    }

    override suspend fun deleteProgram(program: IPTVProgram) {
        programDao.deleteProgramById(program.id)
    }

    override suspend fun deleteProgramById(id: String) {
        programDao.deleteProgramById(id)
    }

    override suspend fun deleteProgramsForChannel(channelId: String) {
        programDao.deleteProgramsForChannel(channelId)
    }

    override suspend fun deleteProgramsForPlaylist(playlistId: String) {
        programDao.deleteProgramsForPlaylist(playlistId)
    }

    // Channel History methods implementation

    override suspend fun recordChannelPlay(channelId: String, playlistId: String, timestamp: Long) {
        val existingHistory = channelHistoryDao.getChannelHistory(channelId, playlistId)
        if (existingHistory != null) {
            // Update existing record - increment play count and update timestamp
            channelHistoryDao.incrementPlayCount(channelId, playlistId, timestamp)
        } else {
            // Create new record
            val newHistory = ChannelHistoryEntity(
                channelId = channelId,
                playlistId = playlistId,
                lastPlayedTimestamp = timestamp,
                totalPlayedTimeMs = 0,
                playCount = 1
            )
            channelHistoryDao.insertOrUpdateChannelHistory(newHistory)
        }
    }

    override suspend fun updateChannelPlayTime(
        channelId: String,
        playlistId: String,
        additionalTimeMs: Long,
        timestamp: Long,
    ) {
        channelHistoryDao.updatePlayedTime(channelId, playlistId, additionalTimeMs, timestamp)
    }

    override suspend fun updateChannelPositionAndDuration(
        channelId: String,
        playlistId: String,
        currentPositionMs: Long,
        totalDurationMs: Long,
        timestamp: Long,
    ) {
        channelHistoryDao.updatePositionAndDuration(
            channelId,
            playlistId,
            currentPositionMs,
            totalDurationMs,
            timestamp
        )
    }

    override fun getAllPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelHistory>> {
        return channelHistoryDao.getAllPlayedChannelsInPlaylist(playlistId).map { historyEntities ->
            historyEntities.map { it.toChannelHistory() }
        }
    }

    override suspend fun getLastPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory? {
        return channelHistoryDao.getLastPlayedChannelInPlaylistWithDetails(playlistId)
    }

    override fun getTop3MostPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelWithHistory>> {
        return channelHistoryDao.getTop3MostPlayedChannelsInPlaylistWithDetails(playlistId)
    }

    override suspend fun getMostPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory? {
        return channelHistoryDao.getMostPlayedChannelInPlaylistWithDetails(playlistId)
    }

    override suspend fun getLastWatchedChannelWithDetails(playlistId: String?): ChannelWithHistory? {
        return channelHistoryDao.getLastWatchedChannelWithDetails(playlistId)
    }

    override fun getAllWatchedChannelsWithDetails(playlistId: String): Flow<List<ChannelWithHistory>> {
        return channelHistoryDao.getAllWatchedChannelsWithDetails(playlistId)
    }

    override fun getLastTop3WatchedChannelsWithDetails(playlistId: String?): Flow<List<ChannelWithHistory>> {
        return channelHistoryDao.getLastTop3WatchedChannelsWithDetails(playlistId)
    }
}
