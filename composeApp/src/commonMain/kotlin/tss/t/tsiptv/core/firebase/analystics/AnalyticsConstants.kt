package tss.t.tsiptv.core.firebase.analystics

/**
 * Event names and parameters sent to Firebase Analytics.
 *
 * Links are only sent through [AnalyticsLinks]: never the raw link, which can carry
 * credentials, tokens and `|Authorization=` suffixes (play-store/data-safety.md).
 */
object AnalyticsConstants {
    const val EVENT_ADD_IPTV = "add_iptv_playlist"
    const val EVENT_PLAY_IPTV_CHANNEL = "play_iptv_channel"

    const val PARAMS_IPTV_NAME = "iptv_name"
    const val PARAMS_IPTV_FORMAT = "iptv_format"
    const val PARAMS_IPTV_CHANNEL_COUNT = "channel_count"
    const val PARAMS_IPTV_CHANNEL_NAME = "channel_name"
    const val PARAMS_IPTV_CHANNEL_TITLE = "channel_title"
    const val PARAMS_IPTV_CHANNEL_PLAY_HOUR = "channel_play_hour"

    /** `add_iptv_playlist`: `link` or `file`. */
    const val PARAMS_SOURCE_KIND = "source_kind"
    /** `https` / `http` (link sources only). */
    const val PARAMS_URL_SCHEME = "url_scheme"
    /** Whether the playlist or source declares a programme guide. */
    const val PARAMS_HAS_EPG = "has_epg"
    /** Includes of a TS IPTV Source. */
    const val PARAMS_INCLUDE_COUNT = "include_count"
    /** Host of the added link, lower-case, without userinfo or port. */
    const val PARAMS_LINK_HOST = "link_host"
    /** The added link as [AnalyticsLinks.sanitized] returns it (no credentials, query or secret-looking path parts). */
    const val PARAMS_LINK = "link"

    /**
     * F2: an addon stream started. Only the play hour and a known content type
     * (`movie`, `series`, `tv`, `channel`, else `other`); never the addon, title, host or URL.
     */
    const val EVENT_PLAY_ADDON_STREAM = "play_addon_stream"
    const val PARAMS_ADDON_CONTENT_TYPE = "content_type"

    /**
     * F2: an addon was added, with [PARAMS_LINK_HOST] and [PARAMS_URL_SCHEME] only: addon
     * paths hold the user's configuration (often service keys), so no path is sent.
     */
    const val EVENT_ADD_ADDON = "add_addon"

    /** Parameters of `add_iptv_playlist` for a playlist or source added from [rawLink] (null for a file). */
    fun addSourceParams(rawLink: String?, hasEpg: Boolean, includeCount: Int? = null): Map<String, Any> = buildMap {
        val link = rawLink?.takeIf { !it.startsWith("file:") }
        put(PARAMS_SOURCE_KIND, if (link != null) "link" else "file")
        put(PARAMS_HAS_EPG, hasEpg.toString())
        includeCount?.let { put(PARAMS_INCLUDE_COUNT, it) }
        if (link != null) {
            AnalyticsLinks.scheme(link)?.let { put(PARAMS_URL_SCHEME, it) }
            AnalyticsLinks.host(link)?.let { put(PARAMS_LINK_HOST, it.take(AnalyticsLinks.MAX_VALUE_LENGTH)) }
            AnalyticsLinks.sanitized(link)?.let { put(PARAMS_LINK, it) }
        }
    }
}
