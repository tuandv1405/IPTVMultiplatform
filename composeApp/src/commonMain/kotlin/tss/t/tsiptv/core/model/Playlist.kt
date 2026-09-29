package tss.t.tsiptv.core.model

/**
 * Where a playlist's content comes from, which decides whether it can be refreshed.
 */
enum class PlaylistSourceType {
    /** Downloaded from [Playlist.url]; refreshed daily and on request. */
    URL,

    /** Read once from a file the user picked; the file is not kept, so it cannot be refreshed. */
    FILE;

    companion object {
        fun fromStored(value: String?): PlaylistSourceType =
            entries.firstOrNull { it.name == value } ?: URL
    }
}

/**
 * Data class representing a playlist.
 *
 * @property id The unique ID of the playlist
 * @property name The name of the playlist
 * @property url The URL of the playlist (`file:<display name>` for [PlaylistSourceType.FILE])
 * @property lastUpdated The timestamp when the playlist was last updated
 * @property epgUrl The first guide URL, kept for callers that read only one
 * @property epgUrls Every guide URL the playlist declares
 * @property format The detected [tss.t.tsiptv.core.parser.model.IPTVFormat] name
 */
data class Playlist(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long,
    val epgUrl: String? = null,
    val sourceType: PlaylistSourceType = PlaylistSourceType.URL,
    val epgUrls: List<String> = listOfNotNull(epgUrl?.takeIf { it.isNotBlank() }),
    val format: String = "UNKNOWN",
)
