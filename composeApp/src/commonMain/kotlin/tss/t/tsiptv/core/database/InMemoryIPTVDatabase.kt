package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

    /** Same semantics as the Room v5 tables (see `InMemoryStremioStores`). */
    override val stremioStores: tss.t.tsiptv.core.stremio.StremioStores = tss.t.tsiptv.core.stremio.InMemoryStremioStores()

    private val channelAttributes = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    // Same order as the Room queries: file order, then insertion order.
    override fun getAllChannels(): Flow<List<Channel>> {
        return channels.map { it.values.sortedBy(Channel::sortIndex) }
    }

    override fun getAllChannelsByPlayListId(playListId: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values.filter { it.playlistId == playListId }.sortedBy(Channel::sortIndex)
        }
    }

    // Reads the current value: collecting a StateFlow never completes.
    override suspend fun getCategoriesByPlaylist(playlistId: String): List<Category> {
        return categories.value.values.filter { it.playlistId == playlistId }
    }

    override suspend fun getChannelById(id: String): Channel? {
        return channels.value[id]
    }

    override fun getChannelsByCategory(categoryId: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values
                .filter { it.categoryId == categoryId || it.groups.any { g -> g.equals(categoryId, ignoreCase = true) } }
                .sortedBy(Channel::sortIndex)
        }
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values.filter {
                it.name.contains(query, ignoreCase = true)
            }.sortedBy(Channel::sortIndex)
        }
    }

    override suspend fun replacePlaylistContent(
        playlist: Playlist,
        categoriesIn: List<Category>,
        channelsIn: List<Channel>,
        attributesIn: Map<String, Map<String, String>>,
        legacyIdsIn: Map<String, String>,
    ) {
        // Same rule as the Room database: an id owned by another playlist is namespaced.
        val taken = this.channels.value.values.filter { it.playlistId != playlist.id }.map { it.id }.toSet()
            .intersect(channelsIn.map { it.id }.toSet())
        val (channels, renamed) = ChannelIdNamespace.resolve(playlist.id, channelsIn, taken)
        val takenCategories = this.categories.value.values.filter { it.playlistId != playlist.id }.map { it.id }.toSet()
        val categories = ChannelIdNamespace.resolveCategories(playlist.id, categoriesIn, takenCategories)
        val attributes = attributesIn.mapKeys { (id, _) -> renamed[id] ?: id }
        val legacyIds = legacyIdsIn.mapKeys { (id, _) -> renamed[id] ?: id }
        val previous = this.channels.value.values.filter { it.playlistId == playlist.id }
        val previousById = previous.associateBy { it.id }
        val removedIds = previous.map { it.id }.toSet()
        val continues = matchPreviousChannels(
            previous.map { StoredChannel(it.id, it.url) },
            channels.map { NewChannel(it.id, it.url, legacyIds[it.id]) },
        )
        // History is keyed "<channel>_<playlist>" here; move rows whose channel was renamed.
        val moved = continues.filter { (newId, oldId) -> newId != oldId }
        if (moved.isNotEmpty()) {
            val history = channelHistory.value
            val movedOld = moved.values.map { "${it}_${playlist.id}" }.toSet()
            channelHistory.value = history.filterKeys { it !in movedOld } +
                    moved.mapNotNull { (newId, oldId) ->
                        history["${oldId}_${playlist.id}"]?.let { "${newId}_${playlist.id}" to it.copy(channelId = newId) }
                    }
        }

        playlists.value = playlists.value + (playlist.id to playlist)
        this.categories.value = this.categories.value.filterValues { it.playlistId != playlist.id } +
                categories.associateBy { it.id }
        this.channels.value = this.channels.value.filterKeys { it !in removedIds } +
                channels.associate { channel ->
                    val old = continues[channel.id]?.let(previousById::get)
                    channel.id to channel.copy(
                        isFavorite = old?.isFavorite ?: channel.isFavorite,
                        lastWatched = old?.lastWatched ?: channel.lastWatched,
                    )
                }
        channelAttributes.value = channelAttributes.value.filterKeys { it !in removedIds } + attributes
    }

    override suspend fun getChannelAttributes(channelId: String): Map<String, String> {
        return channelAttributes.value[channelId].orEmpty()
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
        return categories.value.values.filter { it.playlistId == playListId }
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
        // Same as Room's ON DELETE CASCADE.
        deleteChannelsInPlaylist(id)
        categories.value = categories.value.filterValues { it.playlistId != id }
        inMemoryTsiptv.deletePlaylist(id)
    }

    private val inMemoryTsiptv = tss.t.tsiptv.core.tsiptv.InMemoryTsiptvStore { content ->
        replacePlaylistContent(content.playlist, content.categories, content.channels, emptyMap(), emptyMap())
        val plan = content.guides
        if (!plan.isEmpty) {
            val pl = content.playlist.id
            val removed = (plan.drop + plan.replace.keys).map { tss.t.tsiptv.core.tsiptv.TsiptvGuideIds.prefix(pl, it) }
            programs.value = programs.value.filterKeys { id -> removed.none { id.startsWith(it) } } +
                plan.replace.flatMap { (path, list) ->
                    list.map { it.copy(id = tss.t.tsiptv.core.tsiptv.TsiptvGuideIds.programId(pl, path, it.id)) }
                }.associateBy { it.id }
        }
    }

    /** Same semantics as the Room v6 tables. */
    override val tsiptvStore: tss.t.tsiptv.core.tsiptv.TsiptvStore get() = inMemoryTsiptv

    override suspend fun deleteChannelsInPlaylist(playlistId: String) {
        val removed = channels.value.values.filter { it.playlistId == playlistId }.map { it.id }.toSet()
        channels.value = channels.value.filterNot { it.value.playlistId == playlistId }
        channelAttributes.value = channelAttributes.value.filterKeys { it !in removed }
    }

    override fun getAllPrograms(): Flow<List<IPTVProgram>> {
        return programs.map { it.values.toList() }
    }

    override suspend fun countValidPrograms(playlistId: String): Int = programsOf(playlistId).size

    /** Programmes of [playlistId], one per (channel, start, title). */
    private fun programsOf(playlistId: String): List<IPTVProgram> {
        val playlistChannels = channels.value.values.filter { it.playlistId == playlistId }
        val guideIds = playlistChannels.flatMap { listOfNotNull(it.epgId, it.id) }.toSet()
        val sourcePrefix = "tsg:$playlistId#"
        return programs.value.values
            .filter { it.id.startsWith(sourcePrefix) || (!it.id.startsWith("tsg:") && it.channelId in guideIds) }
            .sortedBy { it.startTime }
            .distinctProgrammes()
    }

    override suspend fun getChannelsWithValidProgramCounts(playlistId: String, timeStamp: Long) =
        programsOf(playlistId).groupBy { it.channelId }.map { (guideId, list) ->
            val channel = channels.value.values.firstOrNull {
                it.playlistId == playlistId && (it.epgId == guideId || (it.epgId == null && it.id == guideId))
            }
            tss.t.tsiptv.core.database.entity.ChannelWithProgramCount(
                channelId = guideId,
                programCount = list.size,
                name = channel?.name,
                categoryId = channel?.categoryId,
                logoUrl = channel?.logoUrl?.takeIf { it.isNotBlank() }
                    ?: list.mapNotNull { it.logo?.takeIf { l -> l.isNotBlank() } }.maxOrNull(),
                isFavorite = channel?.isFavorite?.let { if (it) "1" else "0" },
            )
        }

    override suspend fun getProgramById(id: String): IPTVProgram? {
        return programs.value[id]
    }

    override suspend fun getProgramsForChannel(channelId: String): List<IPTVProgram> {
        return programs.value.values.filter { it.channelId == channelId }.sortedBy { it.startTime }.distinctProgrammes()
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
        }.sortedBy { it.startTime }.distinctProgrammes()
    }

    override suspend fun getCurrentAndUpcomingProgramsForChannel(
        channelId: String,
        currentTime: Long,
    ): List<IPTVProgram> {
        return programs.value.values.filter {
            it.channelId == channelId &&
                    it.endTime > currentTime
        }.sortedBy { it.startTime }.distinctProgrammes()
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
        // F3: a source's programmes carry its playlist id in their id.
        val sourcePrefix = "tsg:$playlistId#"
        programs.value = programs.value.filterKeys { !it.startsWith(sourcePrefix) }
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
        channelAttributes.value = emptyMap()
    }

    // Channel History methods implementation

    @OptIn(ExperimentalTime::class)
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
