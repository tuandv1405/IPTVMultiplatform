package tss.t.tsiptv.core.stremio

import tss.t.tsiptv.player.models.DashRouting
import tss.t.tsiptv.player.models.PlaybackCapabilities
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import tss.t.tsiptv.player.models.MediaItem

/**
 * What the app can do with a stream (PRD §7). Decided by field presence:
 * `url` > `externalUrl` > everything else.
 */
sealed interface StreamKind {
    /** An `http(s)` `url` stream: play it in TS IPTV with [headers] on every request. */
    data class Playable(
        val url: String,
        /** `behaviorHints.proxyHeaders.request`. */
        val headers: Map<String, String>,
        /** From `filename`/URL extension; null lets the player sniff (progressive files). */
        val mimeType: String?,
    ) : StreamKind {
        /** Never the URL (tokens) or header values (cookies). */
        override fun toString(): String = "Playable(host=${UrlParts.split(url)?.host}, headers=${headers.keys}, mimeType=$mimeType)"
    }

    /** `externalUrl` with `http(s)`: confirm (showing [host]) and open the system browser. */
    data class ExternalBrowser(val url: String, val host: String) : StreamKind {
        override fun toString(): String = "ExternalBrowser(host=$host)"
    }

    /** `externalUrl` `stremio:///…`: internal navigation to a link the app can route. */
    data class Internal(val url: String, val link: StremioDeepLink) : StreamKind {
        /** [link]'s own toString is redacted (Discover shows the host only); never [url]. */
        override fun toString(): String = "Internal(link=$link)"
    }

    /**
     * Everything else (torrent, Usenet, archives, YouTube, iframe players, other `url` schemes,
     * Stremio local-server URLs). [rawKind] is for diagnostics/tests only and **must never be shown**
     * in the UI (no kind label, no count — PRD policy "No torrent advertising").
     */
    data class Unsupported(val rawKind: String) : StreamKind
}

/** A stream with its kind and display fields, ready for the picker. */
data class ClassifiedStream(
    val stream: StremioStream,
    val kind: StreamKind,
) {
    /**
     * Unsupported rows show no addon text at all (addons often put "Torrent"/"P2P" in `name`), only
     * `stream_unsupported`: name is "" and description null for them.
     */
    val name: String get() = if (!isSelectable) "" else stream.name?.takeIf { it.isNotBlank() }.orEmpty()
    /** `description ?: title`; null for unsupported rows. */
    val description: String? get() = if (!isSelectable) null else stream.displayDescription
    val isSelectable: Boolean get() = kind !is StreamKind.Unsupported
    /** Null for unsupported rows (nothing addon-provided is exposed for them). */
    val bingeGroup: String? get() = if (!isSelectable) null else stream.hints.bingeGroup
    /** Show `stream_badge_region`; never for unsupported rows. */
    val isRegionLimited: Boolean get() = isSelectable && stream.hints.countryWhitelist.isNotEmpty()
    /**
     * Show `stream_badge_may_not_play` on iOS: MKV (AVPlayer cannot play it), and DASH when no DASH
     * engine is registered or the stream has DRM (see [DashRouting.dashMayNotPlayOnIos]).
     */
    val mayNotPlayOnIos: Boolean
        get() = (kind as? StreamKind.Playable)?.let {
            (it.mimeType == StreamMimeTypes.DASH &&
                DashRouting.dashMayNotPlayOnIos(PlaybackCapabilities.iosDashEngineAvailable, stream.hints.hasDrm)) ||
                StreamClassifier.extensionOf(stream.hints.filename ?: it.url) == "mkv"
        } ?: false

    /** Never the stream itself (URLs with tokens, headers). */
    override fun toString(): String = "ClassifiedStream(kind=$kind)"
}

object StreamClassifier {
    private const val LOCAL_SERVER_PORT = 11470

    fun classify(stream: StremioStream): StreamKind {
        stream.url?.trim()?.takeIf { it.isNotEmpty() }?.let { return classifyUrl(it, stream) }
        (stream.externalUrl ?: stream.androidTvUrl)?.trim()?.takeIf { it.isNotEmpty() }?.let { return classifyExternal(it) }
        return StreamKind.Unsupported(
            when {
                !stream.ytId.isNullOrBlank() -> "ytId"
                !stream.infoHash.isNullOrBlank() -> "infoHash"
                !stream.nzbUrl.isNullOrBlank() || stream.nzbUrls.isPresent() -> "nzb"
                stream.rarUrls.isPresent() || stream.zipUrls.isPresent() || stream.sevenZipUrls.isPresent() ||
                    stream.tgzUrls.isPresent() || stream.tarUrls.isPresent() -> "archive"
                !stream.playerFrameUrl.isNullOrBlank() -> "playerFrame"
                else -> "unknown"
            }
        )
    }

    private fun classifyUrl(url: String, stream: StremioStream): StreamKind {
        val parts = UrlParts.split(url) ?: return StreamKind.Unsupported("url:invalid")
        if (parts.scheme != "http" && parts.scheme != "https") return StreamKind.Unsupported("url:${parts.scheme}")
        if (isLocalServer(parts)) return StreamKind.Unsupported("url:localServer")
        return StreamKind.Playable(
            url = url,
            headers = stream.hints.requestHeaders,
            mimeType = mimeTypeFor(stream.hints.filename, url, stream.hints.responseHeaders),
        )
    }

    private fun classifyExternal(url: String): StreamKind {
        if (url.startsWith(StremioDeepLink.PREFIX, ignoreCase = true)) {
            // A page link the app cannot route (board, library, …) is not selectable.
            val link = StremioDeepLink.parse(url) ?: return StreamKind.Unsupported("internal:unrouted")
            return StreamKind.Internal(url, link)
        }
        val parts = UrlParts.split(url) ?: return StreamKind.Unsupported("external:invalid")
        if (parts.scheme != "http" && parts.scheme != "https") return StreamKind.Unsupported("external:${parts.scheme}")
        return StreamKind.ExternalBrowser(url, parts.host)
    }

    private fun isLocalServer(parts: UrlParts): Boolean =
        parts.port == LOCAL_SERVER_PORT && (parts.host == "127.0.0.1" || parts.host == "localhost" || parts.host == "[::1]")

    fun classifyAll(streams: List<StremioStream>): List<ClassifiedStream> =
        streams.map { ClassifiedStream(it, classify(it)) }

    /**
     * The rows the picker shows: selectable streams only by default; with [showUnsupported] (off by
     * default, not remembered) the unsupported ones are included, in their original order, to be
     * shown greyed as `stream_unsupported`.
     */
    fun visible(streams: List<ClassifiedStream>, showUnsupported: Boolean = false): List<ClassifiedStream> =
        if (showUnsupported) streams else streams.filter { it.isSelectable }

    /**
     * MIME hint from `filename`, then the URL path, then `proxyHeaders.response` `Content-Type`.
     * Only adaptive/TS containers get a hint; progressive files are left to the player.
     */
    fun mimeTypeFor(filename: String?, url: String, responseHeaders: Map<String, String> = emptyMap()): String? {
        val byExtension = mimeForExtension(extensionOf(filename)) ?: mimeForExtension(extensionOf(url))
        if (byExtension != null) return byExtension
        val contentType = responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        return StreamMimeTypes.fromMimeType(contentType?.substringBefore(';'))
    }

    private fun mimeForExtension(ext: String?): String? = when (ext) {
        "m3u8" -> StreamMimeTypes.HLS // `.m3u` is usually a plain playlist, not HLS
        "mpd" -> StreamMimeTypes.DASH
        "ism", "isml" -> StreamMimeTypes.SMOOTH_STREAMING
        "ts" -> StreamMimeTypes.MPEG_TS
        else -> null
    }

    internal fun extensionOf(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val path = pathOrUrl.substringBefore('#').substringBefore('?')
        val last = path.substringAfterLast('/')
        if ('.' !in last) return null
        return last.substringAfterLast('.').lowercase().takeIf { it.isNotEmpty() }
    }
}

/**
 * Playback mapping (PRD §7): `MediaItem(uri = url, headers = proxyHeaders.request, mimeType, title,
 * artwork)`. `notWebReady` and `proxyHeaders.response` are ignored. Returns null for streams that
 * are not playable in-app.
 *
 * [id] must be unique per addon + video, e.g. `stremio:{addonId}:{videoId}`; it is not a URL.
 */
fun ClassifiedStream.toMediaItem(
    id: String,
    title: String,
    subtitle: String = "",
    artworkUri: String? = null,
): MediaItem? {
    val playable = kind as? StreamKind.Playable ?: return null
    return MediaItem(
        id = id,
        uri = playable.url,
        title = title,
        description = subtitle,
        artist = subtitle,
        artworkUri = artworkUri ?: stream.thumbnail,
        mimeType = playable.mimeType,
        headers = playable.headers,
    )
}
