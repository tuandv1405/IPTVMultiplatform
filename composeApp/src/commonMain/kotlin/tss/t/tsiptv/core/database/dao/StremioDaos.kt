package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.MediaHistoryEntity
import tss.t.tsiptv.core.database.entity.StremioAddonEntity

@Dao
interface StremioAddonDao {
    @Query("SELECT * FROM stremio_addons ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<StremioAddonEntity>>

    @Query("SELECT * FROM stremio_addons ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAll(): List<StremioAddonEntity>

    @Query("SELECT * FROM stremio_addons WHERE addonId = :addonId")
    suspend fun get(addonId: String): StremioAddonEntity?

    @Upsert
    suspend fun upsert(entity: StremioAddonEntity)

    @Query("DELETE FROM stremio_addons WHERE addonId = :addonId")
    suspend fun delete(addonId: String)
}

@Dao
interface MediaHistoryDao {
    @Query("SELECT * FROM media_history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MediaHistoryEntity>>

    @Query(
        "SELECT * FROM media_history WHERE sourceKind = :sourceKind AND sourceId = :sourceId " +
            "AND itemId = :itemId AND videoId = :videoId LIMIT 1"
    )
    suspend fun find(sourceKind: String, sourceId: String, itemId: String, videoId: String): MediaHistoryEntity?

    @Query(
        "SELECT * FROM media_history WHERE sourceKind = :sourceKind AND itemId = :itemId AND videoId = :videoId " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun findVideo(sourceKind: String, itemId: String, videoId: String): MediaHistoryEntity?

    @Query("SELECT * FROM media_history WHERE sourceKind = :sourceKind AND itemId = :itemId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForItem(sourceKind: String, itemId: String): MediaHistoryEntity?

    @Upsert
    suspend fun upsert(entity: MediaHistoryEntity)

    @Query("DELETE FROM media_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM media_history WHERE sourceKind = :sourceKind AND itemId = :itemId")
    suspend fun deleteForItem(sourceKind: String, itemId: String)

    @Query("DELETE FROM media_history WHERE sourceKind = :sourceKind AND sourceId = :sourceId")
    suspend fun deleteForSource(sourceKind: String, sourceId: String)

    @Query("DELETE FROM media_history")
    suspend fun clear()
}
