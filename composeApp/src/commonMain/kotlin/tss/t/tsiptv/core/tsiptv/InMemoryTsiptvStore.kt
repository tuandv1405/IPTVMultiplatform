package tss.t.tsiptv.core.tsiptv

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory F3 tables for tests and previews, with the Room semantics (ordering, cascades).
 *
 * @param replacePlaylist Stores the playlist, categories and channels part of a
 *   [TsiptvStoredContent] in the owning in-memory database
 */
class InMemoryTsiptvStore(
    private val replacePlaylist: suspend (TsiptvStoredContent) -> Unit,
) : TsiptvStore {
    private val sources = MutableStateFlow<Map<String, TsiptvSourceRecord>>(emptyMap())
    private val includes = MutableStateFlow<Map<String, List<TsiptvIncludeRecord>>>(emptyMap())
    private val vodItems = MutableStateFlow<Map<String, List<TsiptvVodItemRecord>>>(emptyMap())
    private val episodes = MutableStateFlow<Map<String, List<TsiptvEpisodeRecord>>>(emptyMap())

    override fun observeSource(playlistId: String): Flow<TsiptvSourceRecord?> = sources.map { it[playlistId] }
    override fun observeSources(): Flow<List<TsiptvSourceRecord>> = sources.map { it.values.toList() }
    override suspend fun getSource(playlistId: String): TsiptvSourceRecord? = sources.value[playlistId]
    override suspend fun getSourceBySourceId(sourceId: String): TsiptvSourceRecord? =
        sources.value.values.firstOrNull { it.sourceId == sourceId }

    override suspend fun upsertSource(record: TsiptvSourceRecord) {
        sources.value = sources.value + (record.playlistId to record)
    }

    override suspend fun getIncludes(playlistId: String): List<TsiptvIncludeRecord> =
        includes.value[playlistId].orEmpty().sortedBy { it.includePath }

    override fun observeIncludes(playlistId: String): Flow<List<TsiptvIncludeRecord>> =
        includes.map { it[playlistId].orEmpty().sortedBy { r -> r.includePath } }

    override suspend fun upsertIncludes(records: List<TsiptvIncludeRecord>) {
        var map = includes.value
        records.groupBy { it.playlistId }.forEach { (playlistId, rows) ->
            val paths = rows.map { it.includePath }.toSet()
            map = map + (playlistId to map[playlistId].orEmpty().filter { it.includePath !in paths } + rows)
        }
        includes.value = map
    }

    override fun observeVodItems(playlistId: String): Flow<List<TsiptvVodItemRecord>> =
        vodItems.map { sortedItems(it[playlistId].orEmpty()) }

    override suspend fun getVodItems(playlistId: String): List<TsiptvVodItemRecord> =
        sortedItems(vodItems.value[playlistId].orEmpty())

    override suspend fun getVodItem(playlistId: String, itemId: String): TsiptvVodItemRecord? =
        vodItems.value[playlistId]?.firstOrNull { it.itemId == itemId }

    override suspend fun getEpisodes(playlistId: String, seriesItemId: String): List<TsiptvEpisodeRecord> =
        episodes.value[playlistId].orEmpty().filter { it.seriesItemId == seriesItemId }
            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

    override suspend fun getAllEpisodes(playlistId: String): List<TsiptvEpisodeRecord> =
        episodes.value[playlistId].orEmpty()
            .sortedWith(compareBy({ it.seriesItemId }, { it.seasonNumber }, { it.episodeNumber }))

    override suspend fun getEpisode(playlistId: String, episodeId: String): TsiptvEpisodeRecord? =
        episodes.value[playlistId]?.firstOrNull { it.episodeId == episodeId }

    override suspend fun countEpisodes(playlistId: String): Int = episodes.value[playlistId]?.size ?: 0

    override suspend fun replaceSourceContent(content: TsiptvStoredContent) {
        val playlistId = content.playlist.id
        replacePlaylist(content)
        sources.value = sources.value + (playlistId to content.source)
        includes.value = includes.value + (playlistId to content.includes)
        vodItems.value = vodItems.value + (playlistId to content.vodItems)
        episodes.value = episodes.value + (playlistId to content.episodes)
    }

    /** ON DELETE CASCADE from `playlists`. */
    fun deletePlaylist(playlistId: String) {
        sources.value = sources.value - playlistId
        includes.value = includes.value - playlistId
        vodItems.value = vodItems.value - playlistId
        episodes.value = episodes.value - playlistId
    }

    private fun sortedItems(items: List<TsiptvVodItemRecord>) =
        items.sortedWith(compareBy({ it.kind }, { it.sortIndex }))
}
