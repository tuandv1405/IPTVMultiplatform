package tss.t.tsiptv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tss.t.tsiptv.core.reaction.Reaction
import tss.t.tsiptv.core.reaction.ReactionRecord

/** v7: the viewer's like/dislike per channel or video (see [ReactionRecord]). */
@Entity(
    tableName = "media_reactions",
    indices = [Index(value = ["reaction", "updatedAt"])],
)
data class MediaReactionEntity(
    @PrimaryKey val contentKey: String,
    /** [Reaction] name. */
    val reaction: String,
    val title: String?,
    val artworkUrl: String?,
    val updatedAt: Long,
)

/** Null for a reaction name this version does not know (a row written by a newer app). */
fun MediaReactionEntity.toRecord(): ReactionRecord? {
    val parsed = Reaction.entries.firstOrNull { it.name == reaction } ?: return null
    return ReactionRecord(contentKey, parsed, title, artworkUrl, updatedAt)
}

fun ReactionRecord.toEntity() = MediaReactionEntity(contentKey, reaction.name, title, artworkUrl, updatedAt)
