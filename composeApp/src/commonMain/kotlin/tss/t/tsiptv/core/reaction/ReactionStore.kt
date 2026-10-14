package tss.t.tsiptv.core.reaction

import kotlinx.coroutines.flow.Flow

/** The viewer's own like or dislike of a channel or video; kept on this device only. */
enum class Reaction { LIKE, DISLIKE }

/**
 * One row of `media_reactions` (Room v7).
 *
 * @property contentKey the media item id: the channel id (from `tvg-id` or the name, the key of
 * favourites and history too, so it survives a playlist refresh), or `stremio:…` / `tsvod:…` for
 * addon and source videos. Never the stream URL, whose tokens change.
 */
data class ReactionRecord(
    val contentKey: String,
    val reaction: Reaction,
    val title: String?,
    val artworkUrl: String?,
    val updatedAt: Long,
)

interface ReactionStore {
    fun observe(contentKey: String): Flow<Reaction?>

    /** Newest first. */
    fun observeAll(reaction: Reaction): Flow<List<ReactionRecord>>

    suspend fun get(contentKey: String): ReactionRecord?
    suspend fun upsert(record: ReactionRecord)
    suspend fun delete(contentKey: String)
}

object Reactions {
    /** Like and dislike exclude each other; tapping the one that is on turns it off. */
    fun next(current: Reaction?, tapped: Reaction): Reaction? = if (current == tapped) null else tapped
}
