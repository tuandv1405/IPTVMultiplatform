package tss.t.tsiptv.core.parser.model

/**
 * Data class representing an IPTV playlist.
 *
 * @property name The name of the playlist
 * @property channels The channel in the playlist
 * @property groups The channel groups in the playlist
 * @property programs The program schedules in the playlist
 * @property epgUrl The URL of the EPG (Electronic Program Guide) for this playlist
 */
data class IPTVPlaylist(
    val name: String,
    val channels: List<IPTVChannel>,
    val groups: List<IPTVGroup>,
    val programs: List<IPTVProgram> = emptyList(),
    val epgUrl: String? = null,
)