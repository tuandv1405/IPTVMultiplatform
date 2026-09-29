package tss.t.tsiptv.core.model

import kotlinx.serialization.Serializable
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSpec

/**
 * Data class representing a channel.
 *
 * @property id The unique ID of the channel
 * @property name The name of the channel
 * @property url The URL of the channel
 * @property logoUrl The URL of the channel's logo
 * @property categoryId The primary group of the channel (its title)
 * @property playlistId The ID of the playlist the channel belongs to
 * @property isFavorite Whether the channel is a favorite
 * @property lastWatched The timestamp when the channel was last watched, or null if never watched
 * @property number Channel number from the playlist (`tvg-chno`)
 * @property groups Every group title the channel belongs to, primary first
 * @property headers HTTP headers the player must send for this channel
 * @property mimeType Container hint for the player
 * @property drm DRM setup, when the playlist declares one
 * @property catchup Catch-up template (stored for F1b, not played yet)
 * @property epgShiftHours Hours added to this channel's guide times
 * @property sortIndex Position in the playlist file
 */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val playlistId: String,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null,
    val number: Int? = null,
    val groups: List<String> = listOfNotNull(categoryId),
    val isRadio: Boolean = false,
    val isVod: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val drm: DrmSpec? = null,
    val catchup: CatchupSpec? = null,
    val epgShiftHours: Double? = null,
    val sortIndex: Int = 0,
    /**
     * F3: the XMLTV channel id when it differs from [id]. TS IPTV Source channels are stored under
     * namespaced ids (`ts:{playlistId}:{itemId}`), so the guide is matched by this instead.
     */
    val epgId: String? = null,
    /** F3: localized name (a serialized `LocalizedText`); [name] keeps the resolved default. */
    val nameJson: String? = null,
    /** F3: include path the channel came from; null for the source's own channels. */
    val originIncludePath: String? = null,
    /** F3: JSON array of tags. */
    val tagsJson: String? = null,
    /** F3: localized description (a serialized `LocalizedText`). */
    val descriptionJson: String? = null,
    /**
     * F3: every stream of a TS IPTV Source channel (JSON `TsiptvStream[]`, 2–10 entries, best first)
     * when it has more than one; [url]/[headers]/[drm] hold the first. Null otherwise.
     */
    val streamsJson: String? = null,
) {
    /**
     * The id the guide knows this channel by. A second channel with the same `tvg-id` in one
     * playlist is stored as `<tvg-id>~2` so both survive, but it still matches the same XMLTV
     * `<channel id>`. TS IPTV Source channels use their [epgId].
     */
    val guideId: String
        get() = epgId?.takeIf { it.isNotBlank() } ?: id.replace(DUPLICATE_ID_SUFFIX, "")
}

private val DUPLICATE_ID_SUFFIX = Regex("~\\d+$")
