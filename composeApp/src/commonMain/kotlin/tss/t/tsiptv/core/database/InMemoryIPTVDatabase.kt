package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.database.dao.CategoryDao
import tss.t.tsiptv.core.database.dao.ChannelAttributeDao
import tss.t.tsiptv.core.database.dao.ChannelDao
import tss.t.tsiptv.core.database.dao.ChannelHistoryDao
import tss.t.tsiptv.core.database.dao.PlaylistDao
import tss.t.tsiptv.core.database.dao.ProgramDao
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.ChannelHistory
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.core.database.entity.ChannelWithHistory

/**
 * A simple in-memory implementation of IPTVDatabase.
 * This implementation stores data in memory and doesn't persist it between app restarts.
 * It's useful for testing and as a placeholder until platform-specific implementations are created.
 */
class InMemoryIPTVDatabase : IPTVDatabase {
    private val channels = MutableStateFlow<Map<String, Channel>>(emptyMap())
    private val categories = MutableStateFlow<Map<String, Category>>(emptyMap())
    private val playlists = MutableStateFlow<Map<String, Playlist>>(emptyMap())
    private val programs = MutableStateFlow<Map<String, IPTVProgram>>(emptyMap())
    private val channelHistory = MutableStateFlow<Map<String, ChannelHistory>>(emptyMap())
    override val categoryDao: CategoryDao
        get() = TODO("Not yet implemented")
    override val channelAttributeDao: ChannelAttributeDao
        get() = TODO("Not yet implemented")
    override val channelDao: ChannelDao
        get() = TODO("Not yet implemented")
    override val channelHistoryDao: ChannelHistoryDao
        get() = TODO("Not yet implemented")
    override val playlistDao: PlaylistDao
        get() = TODO("Not yet implemented")
    override val programDao: ProgramDao
        get() = TODO("Not yet implemented")

    override fun getAllChannels(): Flow<List<Channel>> {
        return channels.map { it.values.toList() }
    }

    override fun getAllChannelsByPlayListId(playListId: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values.filter { it.playlistId == playListId }
        }
    }

    override suspend fun getCategoriesByPlaylist(playlistId: String): List<Category> {
        return categories.map { categoryMap ->
            categoryMap.values.filter { it.playlistId == playlistId }
        }.toList().flatten()
    }

    override suspend fun getChannelById(id: String): Channel? {
        return channels.value[id]
    }

    override fun getChannelsByCategory(categoryId: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values.filter { it.categoryId == categoryId }
        }
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    override suspend fun insertChannel(channel: Channel) {
        channels.value = channels.value + (channel.id to channel)
    }

    override suspend fun insertChannels(channels: List<Channel>) {
        this.channels.value = this.channels.value + channels.associateBy { it.id }
    }

    override suspend fun deleteChannel(channel: Channel) {
        deleteChannelById(channel.id)
    }

    override suspend fun deleteChannelById(id: String) {
        channels.value = channels.value - id
    }

    override fun getAllCategories(): Flow<List<Category>> {
        return categories.map { it.values.toList() }
    }

    override suspend fun getAllCategoriesByPlayListId(playListId: String): List<Category> {
        return categories.map { categoryMap ->
            categoryMap.values.filter { it.playlistId == playListId }
        }.toList().flatten()
    }

    override suspend fun getCategoryById(id: String): Category? {
        return categories.value[id]
    }

    override suspend fun insertCategory(category: Category) {
        categories.value = categories.value + (category.id to category)
    }

    override suspend fun insertCategories(categories: List<Category>) {
        this.categories.value = this.categories.value + categories.associateBy { it.id }
    }

    override suspend fun deleteCategory(category: Category) {
        deleteCategoryById(category.id)
    }

    override suspend fun deleteCategoryById(id: String) {
        categories.value = categories.value - id
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlists.map { it.values.toList() }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        return playlists.value[id]
    }

    override suspend fun insertPlaylist(playlist: Playlist) {
        playlists.value = playlists.value + (playlist.id to playlist)
    }

    override suspend fun insertPlaylists(playlists: List<Playlist>) {
        this.playlists.value = this.playlists.value + playlists.associateBy { it.id }
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        deletePlaylistById(playlist.id)
    }

    override suspend fun deletePlaylistById(id: String) {
        playlists.value = playlists.value - id
    }

    override suspend fun deleteChannelsInPlaylist(playlistId: String) {
        channels.value = channels.value.filterNot { it.value.playlistId == playlistId }
    }

    override fun getAllPrograms(): Flow<List<IPTVProgram>> {
        return programs.map { it.values.toList() }
    }

    override suspend fun countValidPrograms(playlistId: String): Int {
        return programs.map {
            it.values.toList()
        }.toCollection(mutableListOf()).size
    }

    override suspend fun getProgramById(id: String): IPTVProgram? {
        return programs.value[id]
    }

    override suspend fun getProgramsForChannel(channelId: String): List<IPTVProgram> {
        return programs.value.values.filter { it.channelId == channelId }
    }

    override suspend fun getProgramsForChannelInTimeRange(
        channelId: String,
        startTime: Long,
        endTime: Long,
    ): List<IPTVProgram> {
        return programs.value.values.filter {
            it.channelId == channelId &&
                    it.startTime >= startTime &&
                    it.endTime <= endTime
        }
    }

    override suspend fun getCurrentAndUpcomingProgramsForChannel(
        channelId: String,
        currentTime: Long,
    ): List<IPTVProgram> {
        return programs.value.values.filter {
            it.channelId == channelId &&
                    it.endTime > currentTime
        }.sortedBy { it.startTime }
    }

    override suspend fun getCurrentProgramForChannel(
        channelId: String,
        currentTime: Long,
    ): IPTVProgram? {
        return programs.value.values.find {
            it.channelId == channelId &&
                    it.startTime <= currentTime &&
                    it.endTime > currentTime
        }
    }

    override suspend fun insertProgram(program: IPTVProgram, playlistId: String) {
        programs.value = programs.value + (program.id to program)
    }

    override suspend fun insertPrograms(programs: List<IPTVProgram>, playlistId: String) {
        this.programs.value = this.programs.value + programs.associateBy { it.id }
    }

    override suspend fun deleteProgram(program: IPTVProgram) {
        deleteProgramById(program.id)
    }

    override suspend fun deleteProgramById(id: String) {
        programs.value = programs.value - id
    }

    override suspend fun deleteProgramsForChannel(channelId: String) {
        programs.value = programs.value.filterNot { it.value.channelId == channelId }
    }

    override suspend fun deleteProgramsForPlaylist(playlistId: String) {
        // Since IPTVProgram doesn't have playlistId, we need to find programs by channels in the playlist
        val channelsInPlaylist =
            channels.value.values.filter { it.playlistId == playlistId }.map { it.id }
        programs.value =
            programs.value.filterNot { channelsInPlaylist.contains(it.value.channelId) }
    }

    override suspend fun clearAllData() {
        channels.value = emptyMap()
        categories.value = emptyMap()
        playlists.value = emptyMap()
        programs.value = emptyMap()
        channelHistory.value = emptyMap()
    }

    // Channel History methods implementation

    override suspend fun recordChannelPlay(channelId: String, playlistId: String, timestamp: Long) {
        val key = "${channelId}_${playlistId}"
        val currentHistory = channelHistory.value
        val existingHistory = currentHistory[key]

        if (existingHistory != null) {
            // Update existing record - increment play count and update timestamp
            val updatedHistory = existingHistory.copy(
                lastPlayedTimestamp = timestamp,
                playCount = existingHistory.playCount + 1
            )
            channelHistory.value = currentHistory + (key to updatedHistory)
        } else {
            // Create new record
            val newHistory = ChannelHistory(
                id = Clock.System.now().toEpochMilliseconds(), // Simple ID generation for in-memory
                channelId = channelId,
                playlistId = playlistId,
                lastPlayedTimestamp = timestamp,
                totalPlayedTimeMs = 0,
                playCount = 1
            )
            channelHistory.value = currentHistory + (key to newHistory)
        }
    }

    override suspend fun updateChannelPlayTime(
        channelId: String,
        playlistId: String,
        additionalTimeMs: Long,
        timestamp: Long,
    ) {
        val key = "${channelId}_${playlistId}"
        val currentHistory = channelHistory.value
        val existingHistory = currentHistory[key]

        if (existingHistory != null) {
            val updatedHistory = existingHistory.copy(
                totalPlayedTimeMs = existingHistory.totalPlayedTimeMs + additionalTimeMs,
                lastPlayedTimestamp = timestamp
            )
            channelHistory.value = currentHistory + (key to updatedHistory)
        }
    }

    override suspend fun updateChannelPositionAndDuration(
        channelId: String,
        playlistId: String,
        currentPositionMs: Long,
        totalDurationMs: Long,
        timestamp: Long,
    ) {
        val key = "$channelId-$playlistId"
        val currentHistory = channelHistory.value
        val existingHistory = currentHistory[key]

        if (existingHistory != null) {
            val updatedHistory = existingHistory.copy(
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                lastPlayedTimestamp = timestamp
            )
            channelHistory.value = currentHistory + (key to updatedHistory)
        }
    }

    override fun getAllPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelHistory>> {
        return channelHistory.map { historyMap ->
            historyMap.values
                .filter { it.playlistId == playlistId }
                .sortedByDescending { it.lastPlayedTimestamp }
        }
    }

    override suspend fun getLastPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory? {
        val lastHistory = channelHistory.value.values
            .filter { it.playlistId == playlistId }
            .maxByOrNull { it.lastPlayedTimestamp }

        return lastHistory?.let { history ->
            val channel = channels.value[history.channelId]
            channel?.let {
                ChannelWithHistory(
                    channelId = it.id,
                    channelName = it.name,
                    channelUrl = it.url,
                    logoUrl = it.logoUrl,
                    categoryId = it.categoryId,
                    playlistId = it.playlistId,
                    isFavorite = it.isFavorite,
                    lastWatched = it.lastWatched,
                    historyId = history.id,
                    lastPlayedTimestamp = history.lastPlayedTimestamp,
                    totalPlayedTimeMs = history.totalPlayedTimeMs,
                    playCount = history.playCount,
                    currentPositionMs = history.currentPositionMs,
                    totalDurationMs = history.totalDurationMs
                )
            }
        }
    }

    override fun getTop3MostPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelWithHistory>> {
        return channelHistory.map { historyMap ->
            historyMap.values
                .filter { it.playlistId == playlistId }
                .sortedByDescending { it.totalPlayedTimeMs }
                .take(3)
                .mapNotNull { history ->
                    val channel = channels.value[history.channelId]
                    channel?.let {
                        ChannelWithHistory(
                            channelId = it.id,
                            channelName = it.name,
                            channelUrl = it.url,
                            logoUrl = it.logoUrl,
                            categoryId = it.categoryId,
                            playlistId = it.playlistId,
                            isFavorite = it.isFavorite,
                            lastWatched = it.lastWatched,
                            historyId = history.id,
                            lastPlayedTimestamp = history.lastPlayedTimestamp,
                            totalPlayedTimeMs = history.totalPlayedTimeMs,
                            playCount = history.playCount,
                            currentPositionMs = history.currentPositionMs,
                            totalDurationMs = history.totalDurationMs
                        )
                    }
                }
        }
    }

    override suspend fun getMostPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory? {
        val mostPlayedHistory = channelHistory.value.values
            .filter { it.playlistId == playlistId }
            .maxByOrNull { it.totalPlayedTimeMs }

        return mostPlayedHistory?.let { history ->
            val channel = channels.value[history.channelId]
            channel?.let {
                ChannelWithHistory(
                    channelId = it.id,
                    channelName = it.name,
                    channelUrl = it.url,
                    logoUrl = it.logoUrl,
                    categoryId = it.categoryId,
                    playlistId = it.playlistId,
                    isFavorite = it.isFavorite,
                    lastWatched = it.lastWatched,
                    historyId = history.id,
                    lastPlayedTimestamp = history.lastPlayedTimestamp,
                    totalPlayedTimeMs = history.totalPlayedTimeMs,
                    playCount = history.playCount,
                    currentPositionMs = history.currentPositionMs,
                    totalDurationMs = history.totalDurationMs
                )
            }
        }
    }

    override suspend fun getLastWatchedChannelWithDetails(playlistId: String?): ChannelWithHistory? {
        val historyEntries = if (playlistId != null) {
            channelHistory.value.values.filter { it.playlistId == playlistId }
        } else {
            channelHistory.value.values.toList()
        }

        val lastHistory = historyEntries.maxByOrNull { it.lastPlayedTimestamp }

        return lastHistory?.let { history ->
            val channel = channels.value[history.channelId]
            channel?.let {
                ChannelWithHistory(
                    channelId = it.id,
                    channelName = it.name,
                    channelUrl = it.url,
                    logoUrl = it.logoUrl,
                    categoryId = it.categoryId,
                    playlistId = it.playlistId,
                    isFavorite = it.isFavorite,
                    lastWatched = it.lastWatched,
                    historyId = history.id,
                    lastPlayedTimestamp = history.lastPlayedTimestamp,
                    totalPlayedTimeMs = history.totalPlayedTimeMs,
                    playCount = history.playCount,
                    currentPositionMs = history.currentPositionMs,
                    totalDurationMs = history.totalDurationMs
                )
            }
        }
    }

    override fun getAllWatchedChannelsWithDetails(playlistId: String): Flow<List<ChannelWithHistory>> {
        return channelHistory.map { historyMap ->
            historyMap.values
                .filter { it.playlistId == playlistId }
                .sortedByDescending { it.lastPlayedTimestamp }
                .mapNotNull { history ->
                    val channel = channels.value[history.channelId]
                    channel?.let {
                        ChannelWithHistory(
                            channelId = it.id,
                            channelName = it.name,
                            channelUrl = it.url,
                            logoUrl = it.logoUrl,
                            categoryId = it.categoryId,
                            playlistId = it.playlistId,
                            isFavorite = it.isFavorite,
                            lastWatched = it.lastWatched,
                            historyId = history.id,
                            lastPlayedTimestamp = history.lastPlayedTimestamp,
                            totalPlayedTimeMs = history.totalPlayedTimeMs,
                            playCount = history.playCount,
                            currentPositionMs = history.currentPositionMs,
                            totalDurationMs = history.totalDurationMs
                        )
                    }
                }
        }
    }

    override fun getLastTop3WatchedChannelsWithDetails(playlistId: String?): Flow<List<ChannelWithHistory>> {
        return channelHistory.map { historyMap ->
            val historyEntries = if (playlistId != null) {
                historyMap.values.filter { it.playlistId == playlistId }
            } else {
                historyMap.values.toList()
            }

            historyEntries
                .sortedByDescending { it.lastPlayedTimestamp }
                .take(3)
                .mapNotNull { history ->
                    val channel = channels.value[history.channelId]
                    channel?.let {
                        ChannelWithHistory(
                            channelId = it.id,
                            channelName = it.name,
                            channelUrl = it.url,
                            logoUrl = it.logoUrl,
                            categoryId = it.categoryId,
                            playlistId = it.playlistId,
                            isFavorite = it.isFavorite,
                            lastWatched = it.lastWatched,
                            historyId = history.id,
                            lastPlayedTimestamp = history.lastPlayedTimestamp,
                            totalPlayedTimeMs = history.totalPlayedTimeMs,
                            playCount = history.playCount,
                            currentPositionMs = history.currentPositionMs,
                            totalDurationMs = history.totalDurationMs
                        )
                    }
                }
        }
    }
}
