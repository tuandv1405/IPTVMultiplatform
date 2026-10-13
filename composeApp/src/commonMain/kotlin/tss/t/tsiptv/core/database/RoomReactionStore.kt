package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.database.entity.toEntity
import tss.t.tsiptv.core.database.entity.toRecord
import tss.t.tsiptv.core.reaction.Reaction
import tss.t.tsiptv.core.reaction.ReactionRecord
import tss.t.tsiptv.core.reaction.ReactionStore

/** Room-backed `media_reactions` (v7). */
class RoomReactionStore(private val database: AppDatabase) : ReactionStore {
    private val dao get() = database.mediaReactionDao()

    override fun observe(contentKey: String): Flow<Reaction?> =
        dao.observe(contentKey).map { it?.toRecord()?.reaction }.distinctUntilChanged()

    override fun observeAll(reaction: Reaction): Flow<List<ReactionRecord>> =
        dao.observeAll(reaction.name).map { rows -> rows.mapNotNull { it.toRecord() } }

    override suspend fun get(contentKey: String): ReactionRecord? = dao.get(contentKey)?.toRecord()
    override suspend fun upsert(record: ReactionRecord) = dao.upsert(record.toEntity())
    override suspend fun delete(contentKey: String) = dao.delete(contentKey)
}
