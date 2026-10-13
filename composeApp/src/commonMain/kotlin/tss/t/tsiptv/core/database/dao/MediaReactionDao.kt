package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.MediaReactionEntity

@Dao
interface MediaReactionDao {
    @Query("SELECT * FROM media_reactions WHERE contentKey = :contentKey LIMIT 1")
    fun observe(contentKey: String): Flow<MediaReactionEntity?>

    @Query("SELECT * FROM media_reactions WHERE reaction = :reaction ORDER BY updatedAt DESC")
    fun observeAll(reaction: String): Flow<List<MediaReactionEntity>>

    @Query("SELECT * FROM media_reactions WHERE contentKey = :contentKey LIMIT 1")
    suspend fun get(contentKey: String): MediaReactionEntity?

    @Upsert
    suspend fun upsert(entity: MediaReactionEntity)

    @Query("DELETE FROM media_reactions WHERE contentKey = :contentKey")
    suspend fun delete(contentKey: String)
}
