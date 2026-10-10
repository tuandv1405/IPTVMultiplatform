package tss.t.tsiptv.core.database

import tss.t.tsiptv.core.model.Channel

/**
 * Channel ids are the table's primary key on their own (schema v6), and they come from the playlist
 * content (`tvg-id`, a slug of the name). Two playlists with the same entries would therefore share
 * ids, and storing the second one replaced (moved) the first one's rows. QC round 3 saw a playlist
 * lose 9,310 channels that way.
 *
 * Until the key becomes `(playlistId, id)` (a Room v7 migration, see the hand-off), an id that is
 * already used by **another** playlist is stored as `<id>@<playlist tag>`. Its guide id is kept in
 * [Channel.epgId] so the EPG still matches. The result is deterministic, so a refresh maps the
 * same entries to the same ids, and history, favourites and attributes continue.
 */
internal object ChannelIdNamespace {
    const val SEPARATOR = "@"

    /** A short, stable tag for [playlistId] (FNV-1a 32-bit, hex). */
    fun tag(playlistId: String): String {
        var h = 0x811C9DC5.toInt()
        for (c in playlistId) {
            h = h xor c.code
            h *= 0x01000193
        }
        return (h.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
    }

    /**
     * [channels] with the ids that [takenElsewhere] (ids owned by other playlists) would clash with
     * moved into this playlist's namespace. Returns the channels and old-id → new-id for the renamed
     * ones (to re-key attributes and legacy ids).
     */
    fun resolve(
        playlistId: String,
        channels: List<Channel>,
        takenElsewhere: Set<String>,
    ): Pair<List<Channel>, Map<String, String>> {
        if (takenElsewhere.isEmpty()) return channels to emptyMap()
        val suffix = SEPARATOR + tag(playlistId)
        val renamed = HashMap<String, String>()
        val out = channels.map { channel ->
            if (channel.id !in takenElsewhere) return@map channel
            val newId = channel.id + suffix
            renamed[channel.id] = newId
            channel.copy(id = newId, epgId = channel.epgId?.takeIf { it.isNotBlank() } ?: channel.guideId)
        }
        return out to renamed
    }

    /** The same for category ids (also a primary key on their own; group titles are slugged). */
    fun resolveCategories(
        playlistId: String,
        categories: List<tss.t.tsiptv.core.model.Category>,
        takenElsewhere: Set<String>,
    ): List<tss.t.tsiptv.core.model.Category> {
        if (takenElsewhere.isEmpty()) return categories
        val suffix = SEPARATOR + tag(playlistId)
        return categories.map { if (it.id in takenElsewhere) it.copy(id = it.id + suffix) else it }
    }
}
