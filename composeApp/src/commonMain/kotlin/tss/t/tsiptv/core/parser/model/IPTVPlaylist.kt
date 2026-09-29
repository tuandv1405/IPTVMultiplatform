package tss.t.tsiptv.core.parser.model

/**
 * Data class representing an IPTV playlist.
 *
 * @property name The name of the playlist
 * @property channels The channel in the playlist
 * @property groups The channel groups in the playlist
 * @property programs The program schedules in the playlist
 * @property epgUrl The first EPG (Electronic Program Guide) URL, for callers that read only one
 * @property epgUrls Every EPG URL the playlist declares, in file order
 * @property skipped Entries that were left out, and why
 */
data class IPTVPlaylist(
    val name: String,
    val channels: List<IPTVChannel>,
    val groups: List<IPTVGroup>,
    val programs: List<IPTVProgram> = emptyList(),
    val epgUrl: String? = null,
    val epgUrls: List<String> = listOfNotNull(epgUrl),
    val skipped: List<SkippedEntry> = emptyList(),
)

/**
 * Why an entry of a playlist was not imported.
 *
 * DRM that TS IPTV cannot handle is deliberately not a reason: such channels are imported and
 * explain themselves when played.
 */
enum class SkipReason {
    /** `plugin://` URL: needs a Kodi add-on. */
    KODI_ADDON,

    /** `#WEBPROP` or an `@` URL: Kodi scrapes a web page for the stream. */
    WEB_SCRAPING,

    /** `#EXTINF` without a stream URL. */
    NO_URL,
}

/**
 * @property lineNumber 1-based line of the entry's `#EXTINF` (or URL line when there is none)
 */
data class SkippedEntry(
    val reason: SkipReason,
    val lineNumber: Int,
)
