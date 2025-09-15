package tss.t.tsiptv.core.model

/**
 * Data class representing a playlist.
 *
 * @property id The unique ID of the playlist
 * @property name The name of the playlist
 * @property url The URL of the playlist
 * @property lastUpdated The timestamp when the playlist was last updated
 */
data class Playlist(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long,
    val epgUrl: String? = null
)