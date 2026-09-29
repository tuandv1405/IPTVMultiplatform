package tss.t.tsiptv.core.parser.model

import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSpec

/**
 * Data class representing an IPTV channel.
 *
 * @property id The unique ID of the channel within its playlist
 * @property name The name of the channel
 * @property url The URL of the channel, without any `|header=value` suffix
 * @property logoUrl The URL of the channel's logo
 * @property groupTitle The primary (first) group of the channel
 * @property epgId The ID of the channel in the EPG (Electronic Program Guide): the raw `tvg-id`
 * @property attributes Raw attributes, plus `kodiprop:<key>` and `vlcopt:<key>` entries
 * @property groups Every group the channel belongs to, primary first
 * @property number `tvg-chno`, when numeric
 * @property headers HTTP headers every request for this channel must carry
 * @property mimeType Container hint, one of [tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes]
 * @property epgShiftHours `tvg-shift`: hours added to this channel's guide times
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
    val groups: List<String> = listOfNotNull(groupTitle),
    val number: Int? = null,
    val isRadio: Boolean = false,
    val isVod: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val drm: DrmSpec? = null,
    val catchup: CatchupSpec? = null,
    val epgShiftHours: Double? = null,
    /**
     * The id the pre-F1 parser would have given this channel, when it differs; used once to
     * find the channel's existing history and favourite. Not stored.
     */
    val legacyId: String? = null,
)
