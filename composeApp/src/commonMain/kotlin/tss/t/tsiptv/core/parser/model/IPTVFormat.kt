package tss.t.tsiptv.core.parser.model

/**
 * Enum representing the format of an IPTV playlist.
 */
enum class IPTVFormat {
    M3U,
    XML,
    JSON,
    JSON_IPTV_ORG,
    XSPF,

    /** Kodi `.strm`: property lines and a single URL. */
    STRM,

    /** Bare list of stream URLs, one per line, without `#EXTINF`. */
    M3U_PLAIN,

    /** An HLS master or media playlist: one stream, not a list of channels. */
    HLS_MANIFEST,

    /** TS IPTV Source document (F3). Recognised so older builds can ask for an update. */
    TSIPTV_SOURCE,

    /** A web page, typically a "view file" page instead of the raw download. Not importable. */
    HTML,
    UNKNOWN
}
