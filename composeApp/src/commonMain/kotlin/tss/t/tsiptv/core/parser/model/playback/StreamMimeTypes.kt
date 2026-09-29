package tss.t.tsiptv.core.parser.model.playback

/**
 * Container hints a playlist can give for a stream. The values are Media3's `MimeTypes`
 * constants, so Android can pass them to `MediaItem.Builder.setMimeType` unchanged; this
 * matters for manifests whose URL has no `.mpd` / `.m3u8` extension.
 */
object StreamMimeTypes {
    const val DASH = "application/dash+xml"
    const val HLS = "application/x-mpegURL"
    const val SMOOTH_STREAMING = "application/vnd.ms-sstr+xml"
    const val MPEG_TS = "video/mp2t"

    /** `#KODIPROP:mimetype=` values (several spellings are common in the wild). */
    fun fromMimeType(value: String?): String? = when (value?.trim()?.lowercase()) {
        "application/dash+xml" -> DASH
        "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> HLS
        "application/vnd.ms-sstr+xml" -> SMOOTH_STREAMING
        "video/mp2t" -> MPEG_TS
        else -> null
    }

    /** `#KODIPROP:inputstream.adaptive.manifest_type=` values. */
    fun fromManifestType(value: String?): String? = when (value?.trim()?.lowercase()) {
        "mpd" -> DASH
        "hls" -> HLS
        "ism" -> SMOOTH_STREAMING
        else -> null
    }

    /** Whether a stream is HLS, from its hint or, failing that, its URL path. */
    fun isHls(url: String, mimeType: String?): Boolean {
        if (mimeType != null) return mimeType == HLS
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }
}
