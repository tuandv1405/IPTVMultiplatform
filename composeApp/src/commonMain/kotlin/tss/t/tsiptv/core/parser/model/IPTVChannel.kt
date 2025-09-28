package tss.t.tsiptv.core.parser.model

/**
 * Data class representing an IPTV channel.
 *
 * @property id The unique ID of the channel
 * @property name The name of the channel
 * @property url The URL of the channel
 * @property logoUrl The URL of the channel's logo
 * @property groupTitle The title of the group the channel belongs to
 * @property epgId The ID of the channel in the EPG (Electronic Program Guide)
 * @property attributes Additional attributes of the channel
 */
data class IPTVChannel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val groupId: String? = null,
    val epgId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)