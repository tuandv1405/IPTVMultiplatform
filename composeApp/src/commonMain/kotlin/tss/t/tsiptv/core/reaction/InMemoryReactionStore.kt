package tss.t.tsiptv.core.reaction

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory `media_reactions`, same semantics as the Room table. Used by `InMemoryIPTVDatabase` and tests. */
class InMemoryReactionStore : ReactionStore {
    private val rows = MutableStateFlow<Map<String, ReactionRecord>>(emptyMap())

    override fun observe(contentKey: String): Flow<Reaction?> = rows.map { it[contentKey]?.reaction }.distinctUntilChanged()

    override fun observeAll(reaction: Reaction): Flow<List<ReactionRecord>> =
        rows.map { all -> all.values.filter { it.reaction == reaction }.sortedByDescending { it.updatedAt } }

    override suspend fun get(contentKey: String): ReactionRecord? = rows.value[contentKey]
    override suspend fun upsert(record: ReactionRecord) = rows.update { it + (record.contentKey to record) }
    override suspend fun delete(contentKey: String) = rows.update { it - contentKey }
}
