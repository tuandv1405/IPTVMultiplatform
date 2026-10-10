package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.database.entity.ChannelAttributeEntity
import tss.t.tsiptv.core.database.entity.ChannelHistoryEntity
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.database.entity.toCategory
import tss.t.tsiptv.core.database.entity.toCategoryEntity
import tss.t.tsiptv.core.database.entity.toChannel
import tss.t.tsiptv.core.database.entity.toChannelEntity
import tss.t.tsiptv.core.database.entity.toChannelHistory
import tss.t.tsiptv.core.database.entity.toIPTVProgram
import tss.t.tsiptv.core.database.entity.toPlaylist
import tss.t.tsiptv.core.database.entity.toPlaylistEntity
import tss.t.tsiptv.core.database.entity.toProgramEntity
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.ChannelHistory
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.parser.model.IPTVProgram
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room implementation of IPTVDatabase.
 * This implementation uses Room to persist data between app restarts.
 *
 * @property database The Room database instance
 */
class RoomIPTVDatabase(
    private val database: AppDatabase,
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
    override val stremioStores: tss.t.tsiptv.core.stremio.StremioStores by lazy { RoomStremioStores(database) }
    override val tsiptvStore: tss.t.tsiptv.core.tsiptv.TsiptvStore by lazy { RoomTsiptvStore(database, this) }
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

    override fun getChannelsByCategory(categoryId: String, playlistId: String?): Flow<List<Channel>> {
        // The pattern matches the JSON text of groupsJson, where `\` is stored as `\\` and `"`
        // as `\"`; then every `\`, `%` and `_` is escaped for LIKE … ESCAPE '\'.
        val escaped = categoryId.replace("\\", "\\\\\\\\").replace("\"", "\\\\\"")
            .replace("%", "\\%").replace("_", "\\_")
        return channelDao.getChannelsByCategory(categoryId, escaped, playlistId).map { channelEntities ->
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
        return playlistDao.getPlaylistById(id)?.toPlaylist()
    }

    override suspend fun replacePlaylistContent(
        playlist: Playlist,
        categories: List<Category>,
        channels: List<Channel>,
        attributes: Map<String, Map<String, String>>,
        legacyIds: Map<String, String>,
    ) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                replacePlaylistContentLocked(playlist, categories, channels, attributes, legacyIds)
            }
        }
    }

    /** The body of [replacePlaylistContent]; must run inside a writer transaction. */
    internal suspend fun replacePlaylistContentLocked(
        playlist: Playlist,
        categoriesIn: List<Category>,
        channelsIn: List<Channel>,
        attributesIn: Map<String, Map<String, String>>,
        legacyIdsIn: Map<String, String>,
    ) {
        // Ids another playlist already uses are namespaced: storing them as they are would take that
        // playlist's rows over (channel.id is the primary key on its own, QC r3).
        val taken = channelsIn.map { it.id }.distinct().chunked(900)
            .flatMap { channelDao.idsOwnedElsewhere(it, playlist.id) }.toSet()
        val (channels, renamed) = ChannelIdNamespace.resolve(playlist.id, channelsIn, taken)
        val takenCategories = categoriesIn.map { it.id }.distinct().chunked(900)
            .flatMap { categoryDao.idsOwnedElsewhere(it, playlist.id) }.toSet()
        val categories = ChannelIdNamespace.resolveCategories(playlist.id, categoriesIn, takenCategories)
        val attributes = if (renamed.isEmpty()) attributesIn else attributesIn.mapKeys { (id, _) -> renamed[id] ?: id }
        val legacyIds = if (renamed.isEmpty()) legacyIdsIn else legacyIdsIn.mapKeys { (id, _) -> renamed[id] ?: id }
        run {
            run {
                val stored = channelDao.getChannelsInPlaylistOnce(playlist.id)
                val previous = stored.associateBy { it.id }
                val continues = matchPreviousChannels(
                    stored.map { StoredChannel(it.id, it.url) },
                    channels.map { NewChannel(it.id, it.url, legacyIds[it.id]) },
                )
                playlistDao.upsertPlaylist(playlist.toPlaylistEntity())
                channelAttributeDao.deleteAttributesForPlaylist(playlist.id)
                channelDao.deleteChannelsByPlaylist(playlist.id)
                categoryDao.deleteCategoriesByPlaylist(playlist.id)
                categoryDao.insertCategories(categories.map { it.toCategoryEntity() })

                // Two steps, so a chain of renames (a → b, b → c) cannot merge rows.
                val moved = continues.filter { (newId, oldId) -> newId != oldId }
                moved.forEach { (newId, oldId) ->
                    channelHistoryDao.moveHistory(oldId, TEMP_ID_PREFIX + newId, playlist.id)
                }
                moved.keys.forEach { newId ->
                    val temp = TEMP_ID_PREFIX + newId
                    channelHistoryDao.moveHistory(temp, newId, playlist.id)
                    // Still there only when newId already had history: merge it in.
                    channelHistoryDao.mergeHistoryInto(temp, newId, playlist.id)
                    channelHistoryDao.deleteHistoryRow(temp, playlist.id)
                }

                channelDao.insertChannels(channels.map { channel ->
                    val old = continues[channel.id]?.let(previous::get)
                    channel.copy(
                        isFavorite = old?.isFavorite ?: channel.isFavorite,
                        lastWatched = old?.lastWatched ?: channel.lastWatched,
                    ).toChannelEntity()
                })
                channelAttributeDao.insertAttributes(
                    attributes.flatMap { (channelId, values) ->
                        values.map { (key, value) ->
                            ChannelAttributeEntity(channelId = channelId, attrKey = key, attrValue = value)
                        }
                    }
                )
            }
        }
    }

    private companion object {
        // Plain text: a NUL character is not safe in SQLite text comparisons.
        const val TEMP_ID_PREFIX = "::tsiptv-moving::"
    }

    override suspend fun getChannelAttributes(channelId: String): Map<String, String> {
        return channelAttributeDao.getAttributesForChannelOnce(channelId)
            .associate { it.attrKey to it.attrValue }
    }

    override suspend fun insertPlaylist(playlist: Playlist) {
        // Upsert, not REPLACE: replacing the row would cascade-delete its history.
        playlistDao.upsertPlaylist(playlist.toPlaylistEntity())
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

    override suspend fun getChannelsWithValidProgramCounts(playlistId: String, timeStamp: Long) =
        programDao.getChannelsWithValidProgramCounts(playlistId, timeStamp)

    @OptIn(ExperimentalTime::class)
    override suspend fun countValidPrograms(playlistId: String): Int {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        return programDao.countValidPrograms(playlistId, timestamp)
    }

    override suspend fun getProgramById(id: String): IPTVProgram? {
        return programDao.getProgramById(id)?.toIPTVProgram()
    }

    override suspend fun getProgramsForChannel(channelId: String, playlistId: String?): List<IPTVProgram> {
        return programDao.getProgramsForChannel(channelId, playlistId).map { it.toIPTVProgram() }.distinctProgrammes()
    }

    override suspend fun getProgramsForChannelInTimeRange(
        channelId: String,
        startTime: Long,
        endTime: Long,
        playlistId: String?,
    ): List<IPTVProgram> {
        return programDao.getProgramsForChannelInTimeRange(channelId, playlistId, startTime, endTime)
            .map { it.toIPTVProgram() }.distinctProgrammes()
    }

    override suspend fun getCurrentAndUpcomingProgramsForChannel(
        channelId: String,
        currentTime: Long,
        playlistId: String?,
    ): List<IPTVProgram> {
        return programDao.getCurrentAndUpcomingProgramsForChannel(channelId, playlistId, currentTime)
            .map { it.toIPTVProgram() }.distinctProgrammes()
    }

    override suspend fun getCurrentProgramForChannel(
        channelId: String,
        currentTime: Long,
        playlistId: String?,
    ): IPTVProgram? {
        return programDao.getCurrentProgramForChannel(channelId, playlistId, currentTime)?.toIPTVProgram()
    }

    // Ids are scoped by playlist (ProgramIds): two playlists with the same guide keep their own rows.
    override suspend fun insertProgram(program: IPTVProgram, playlistId: String) {
        programDao.insertProgram(program.copy(id = ProgramIds.scoped(playlistId, program.id)).toProgramEntity(playlistId))
    }

    override suspend fun insertPrograms(programs: List<IPTVProgram>, playlistId: String) {
        programDao.insertPrograms(programs.map { it.copy(id = ProgramIds.scoped(playlistId, it.id)).toProgramEntity(playlistId) })
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
