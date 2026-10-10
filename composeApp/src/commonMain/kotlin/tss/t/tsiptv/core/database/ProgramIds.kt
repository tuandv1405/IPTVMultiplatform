package tss.t.tsiptv.core.database

/**
 * Programme ids are the `programs` primary key on their own (schema v6). An XMLTV programme's id is
 * `<channel>_<start>`, the same in every playlist that uses the same guide, so storing playlist B's
 * guide replaced playlist A's rows ("EPG ping-pong", QC round 4). Stored ids are therefore scoped by
 * the playlist: `p:<playlistId>|<id>`. TS IPTV Source ids (`tsg:<playlistId>#…`) are scoped already.
 *
 * Rows stored before this change (unscoped) are still read: every per-channel query filters by the
 * `playlistId` column, not by the id, and the next EPG parse of that playlist deletes and rewrites
 * them.
 */
internal object ProgramIds {
    private const val PREFIX = "p:"

    fun prefix(playlistId: String) = "$PREFIX$playlistId|"

    fun scoped(playlistId: String, id: String): String =
        if (id.startsWith("tsg:") || id.startsWith(prefix(playlistId))) id else prefix(playlistId) + id

    /** Whether [id] was stored for [playlistId] (scoped, or a TS IPTV Source id of it). */
    fun belongsTo(id: String, playlistId: String): Boolean =
        id.startsWith(prefix(playlistId)) || id.startsWith("tsg:$playlistId#")
}
