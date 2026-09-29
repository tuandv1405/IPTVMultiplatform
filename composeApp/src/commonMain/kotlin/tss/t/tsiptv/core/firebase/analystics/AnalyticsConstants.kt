package tss.t.tsiptv.core.firebase.analystics

/**
 * Event names and parameters sent to Firebase Analytics.
 *
 * There is deliberately no URL parameter: playlist and stream links carry access tokens and
 * `|Authorization=` suffixes, and must never leave the device (play-store/data-safety.md).
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

    /**
     * F2: an addon stream started. Only the play hour and a known content type
     * (`movie`, `series`, `tv`, `channel`, else `other`); never the addon, title, host or URL.
     */
    const val EVENT_PLAY_ADDON_STREAM = "play_addon_stream"
    const val PARAMS_ADDON_CONTENT_TYPE = "content_type"

    /** F2: an addon was added. No parameters (the addon's name or host could identify it). */
    const val EVENT_ADD_ADDON = "add_addon"
}
