package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory `stremio_addons` + `media_history`, with the same semantics as the Room tables
 * (ordering, unique key, deleting an addon deletes its history). Used by `InMemoryIPTVDatabase`
 * and tests.
 */
class InMemoryStremioStores : StremioStores {
    private val addonRows = MutableStateFlow<Map<String, StoredAddon>>(emptyMap())
    private val historyRows = MutableStateFlow<List<MediaHistoryRecord>>(emptyList())
    private var nextId = 1L

    override fun observeAddons(): Flow<List<StoredAddon>> = addonRows.map { sortAddons(it.values) }
    override suspend fun getAddons(): List<StoredAddon> = sortAddons(addonRows.value.values)
    override suspend fun getAddon(addonId: String): StoredAddon? = addonRows.value[addonId]
    override suspend fun upsertAddon(addon: StoredAddon) = addonRows.update { it + (addon.addonId to addon) }

    override suspend fun deleteAddon(addonId: String) {
        addonRows.update { it - addonId }
        historyRows.update { rows -> rows.filterNot { it.sourceKind == MediaSourceKind.STREMIO && it.sourceId == addonId } }
    }

    private fun sortAddons(rows: Collection<StoredAddon>) = rows.sortedWith(compareBy({ it.sortOrder }, { it.addedAt }))

    override fun observeHistory(): Flow<List<MediaHistoryRecord>> = historyRows.map { it.sortedByDescending { r -> r.updatedAt } }

    override suspend fun find(sourceKind: String, sourceId: String, itemId: String, videoId: String): MediaHistoryRecord? =
        historyRows.value.firstOrNull { it.sourceKind == sourceKind && it.sourceId == sourceId && it.itemId == itemId && it.videoId == videoId }

    override suspend fun findVideo(sourceKind: String, itemId: String, videoId: String): MediaHistoryRecord? =
        historyRows.value.filter { it.sourceKind == sourceKind && it.itemId == itemId && it.videoId == videoId }.maxByOrNull { it.updatedAt }

    override suspend fun latestForItem(sourceKind: String, itemId: String): MediaHistoryRecord? =
        historyRows.value.filter { it.sourceKind == sourceKind && it.itemId == itemId }.maxByOrNull { it.updatedAt }

    override suspend fun upsert(record: MediaHistoryRecord) {
        historyRows.update { rows ->
            val existing = rows.firstOrNull {
                it.sourceKind == record.sourceKind && it.sourceId == record.sourceId &&
                    it.itemId == record.itemId && it.videoId == record.videoId
            }
            val row = record.copy(id = existing?.id ?: nextId++)
            rows.filterNot { it.id == row.id } + row
        }
    }

    override suspend fun delete(id: Long) = historyRows.update { rows -> rows.filterNot { it.id == id } }

    override suspend fun deleteForItem(sourceKind: String, itemId: String) =
        historyRows.update { rows -> rows.filterNot { it.sourceKind == sourceKind && it.itemId == itemId } }

    override suspend fun deleteForSource(sourceKind: String, sourceId: String) =
        historyRows.update { rows -> rows.filterNot { it.sourceKind == sourceKind && it.sourceId == sourceId } }

    override suspend fun clear() = historyRows.update { emptyList() }
}
