package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.TsiptvIncludeEntity
import tss.t.tsiptv.core.database.entity.TsiptvSourceEntity
import tss.t.tsiptv.core.database.entity.VodEpisodeEntity
import tss.t.tsiptv.core.database.entity.VodItemEntity

/** F3 (Room v6): TS IPTV Source tables. */
@Dao
interface TsiptvDao {
    @Query("SELECT * FROM tsiptv_sources WHERE playlistId = :playlistId")
    fun observeSource(playlistId: String): Flow<TsiptvSourceEntity?>

    @Query("SELECT * FROM tsiptv_sources WHERE playlistId = :playlistId")
    suspend fun getSource(playlistId: String): TsiptvSourceEntity?

    @Query("SELECT * FROM tsiptv_sources WHERE sourceId = :sourceId")
    suspend fun getSourceBySourceId(sourceId: String): TsiptvSourceEntity?

    @Query("SELECT * FROM tsiptv_sources")
    fun observeSources(): Flow<List<TsiptvSourceEntity>>

    @Upsert
    suspend fun upsertSource(entity: TsiptvSourceEntity)

    @Query("SELECT * FROM tsiptv_includes WHERE playlistId = :playlistId ORDER BY includePath")
    suspend fun getIncludes(playlistId: String): List<TsiptvIncludeEntity>

    @Query("SELECT * FROM tsiptv_includes WHERE playlistId = :playlistId ORDER BY includePath")
    fun observeIncludes(playlistId: String): Flow<List<TsiptvIncludeEntity>>

    @Query("DELETE FROM tsiptv_includes WHERE playlistId = :playlistId")
    suspend fun deleteIncludes(playlistId: String)

    @Upsert
    suspend fun upsertIncludes(entities: List<TsiptvIncludeEntity>)

    @Query("SELECT * FROM vod_items WHERE playlistId = :playlistId ORDER BY kind, sortIndex")
    fun observeVodItems(playlistId: String): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM vod_items WHERE playlistId = :playlistId ORDER BY kind, sortIndex")
    suspend fun getVodItems(playlistId: String): List<VodItemEntity>

    @Query("SELECT * FROM vod_items WHERE rowId = :rowId")
    suspend fun getVodItem(rowId: String): VodItemEntity?

    @Query("DELETE FROM vod_items WHERE playlistId = :playlistId")
    suspend fun deleteVodItems(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVodItems(entities: List<VodItemEntity>)

    @Query(
        "SELECT * FROM vod_episodes WHERE playlistId = :playlistId AND seriesItemId = :seriesItemId " +
            "ORDER BY seasonNumber, episodeNumber"
    )
    suspend fun getEpisodes(playlistId: String, seriesItemId: String): List<VodEpisodeEntity>

    @Query("SELECT * FROM vod_episodes WHERE playlistId = :playlistId ORDER BY seriesItemId, seasonNumber, episodeNumber")
    suspend fun getAllEpisodes(playlistId: String): List<VodEpisodeEntity>

    @Query("SELECT * FROM vod_episodes WHERE rowId = :rowId")
    suspend fun getEpisode(rowId: String): VodEpisodeEntity?

    @Query("SELECT COUNT(*) FROM vod_episodes WHERE playlistId = :playlistId")
    suspend fun countEpisodes(playlistId: String): Int

    @Query("DELETE FROM vod_episodes WHERE playlistId = :playlistId")
    suspend fun deleteEpisodes(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(entities: List<VodEpisodeEntity>)
}
