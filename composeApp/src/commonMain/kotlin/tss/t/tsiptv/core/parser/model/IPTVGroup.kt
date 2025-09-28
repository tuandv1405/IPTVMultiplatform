package tss.t.tsiptv.core.parser.model

/**
 * Data class representing an IPTV group.
 *
 * @property id The unique ID of the group
 * @property title The title of the group
 */
data class IPTVGroup(
    val id: String,
    val title: String,
)