package tss.t.tsiptv.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.database.entity.toEntity
import tss.t.tsiptv.core.database.entity.toRecord
import tss.t.tsiptv.core.database.entity.toStoredAddon
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.core.stremio.MediaSourceKind
import tss.t.tsiptv.core.stremio.StoredAddon
import tss.t.tsiptv.core.stremio.StremioStores

/** Room-backed `stremio_addons` + `media_history` (v5). */
class RoomStremioStores(private val database: AppDatabase) : StremioStores {
    private val addons get() = database.stremioAddonDao()
    private val history get() = database.mediaHistoryDao()

    override fun observeAddons(): Flow<List<StoredAddon>> = addons.observeAll().map { rows -> rows.map { it.toStoredAddon() } }
    override suspend fun getAddons(): List<StoredAddon> = addons.getAll().map { it.toStoredAddon() }
    override suspend fun getAddon(addonId: String): StoredAddon? = addons.get(addonId)?.toStoredAddon()
    override suspend fun upsertAddon(addon: StoredAddon) = addons.upsert(addon.toEntity())

    override suspend fun deleteAddon(addonId: String) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                history.deleteForSource(MediaSourceKind.STREMIO, addonId)
                addons.delete(addonId)
            }
        }
    }

    override fun observeHistory(): Flow<List<MediaHistoryRecord>> = history.observeAll().map { rows -> rows.map { it.toRecord() } }

    override suspend fun find(sourceKind: String, sourceId: String, itemId: String, videoId: String): MediaHistoryRecord? =
        history.find(sourceKind, sourceId, itemId, videoId)?.toRecord()

    override suspend fun findVideo(sourceKind: String, itemId: String, videoId: String): MediaHistoryRecord? =
        history.findVideo(sourceKind, itemId, videoId)?.toRecord()

    override suspend fun latestForItem(sourceKind: String, itemId: String): MediaHistoryRecord? =
        history.latestForItem(sourceKind, itemId)?.toRecord()

    /** The unique key is not the primary key, so reuse the existing row's id (an upsert by id alone would miss). */
    override suspend fun upsert(record: MediaHistoryRecord) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val existing = history.find(record.sourceKind, record.sourceId, record.itemId, record.videoId)
                history.upsert(record.copy(id = existing?.id ?: 0).toEntity())
            }
        }
    }

    override suspend fun delete(id: Long) = history.delete(id)
    override suspend fun deleteForItem(sourceKind: String, itemId: String) =
        history.deleteForItem(sourceKind, itemId)
    override suspend fun deleteForSource(sourceKind: String, sourceId: String) =
        history.deleteForSource(sourceKind, sourceId)
    override suspend fun clear() = history.clear()
}
