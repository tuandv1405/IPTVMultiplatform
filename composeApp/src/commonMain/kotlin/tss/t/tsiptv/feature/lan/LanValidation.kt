package tss.t.tsiptv.feature.lan

/**
 * Checks on everything that arrives from the LAN before the TV acts on it (PRD §2.5).
 * Pure functions: no logging, no network.
 */
object LanValidation {
    const val MAX_URL_LENGTH = 4096
    const val MAX_HEADERS = 32
    const val MAX_HEADER_NAME = 128
    const val MAX_HEADER_VALUE = 2048
    const val MAX_TITLE = 300
    const val MAX_NAME = 120
    const val MAX_MIME = 100
    const val MAX_SUBTITLES = 10
    const val MAX_EPG_URLS = 5

    private val TOKEN = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

    /** http(s) only, a host, a sane length and no whitespace or control characters. */
    fun isHttpUrl(url: String?): Boolean {
        if (url == null || url.isEmpty() || url.length > MAX_URL_LENGTH) return false
        if (url.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) return false
        val lower = url.lowercase()
        val rest = when {
            lower.startsWith("https://") -> url.substring(8)
            lower.startsWith("http://") -> url.substring(7)
            else -> return false
        }
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        return host.isNotEmpty() && !host.startsWith(":")
    }

    fun headersOk(headers: Map<String, String>): Boolean =
        headers.size <= MAX_HEADERS && headers.all { (name, value) ->
            name.length in 1..MAX_HEADER_NAME && TOKEN.matches(name) &&
                value.length <= MAX_HEADER_VALUE && value.none { it == '\r' || it == '\n' || it.code == 0 }
        }

    /** null when the stream may be played, else a [LanErrorCode]. */
    fun checkCast(stream: CastStream): String? {
        if (!isHttpUrl(stream.url)) return LanErrorCode.BAD_URL
        if (stream.logo != null && !isHttpUrl(stream.logo)) return LanErrorCode.BAD_URL
        if (stream.title.length > MAX_TITLE) return LanErrorCode.BAD_REQUEST
        if ((stream.mimeType?.length ?: 0) > MAX_MIME) return LanErrorCode.BAD_REQUEST
        if (!headersOk(stream.headers)) return LanErrorCode.BAD_REQUEST
        if (stream.positionMs != null && stream.positionMs < 0) return LanErrorCode.BAD_REQUEST
        if (stream.subtitles.size > MAX_SUBTITLES || stream.subtitles.any { !isHttpUrl(it.url) }) {
            return LanErrorCode.BAD_URL
        }
        stream.drm?.let { drm ->
            if (drm.licenseUrl != null && !isHttpUrl(drm.licenseUrl)) return LanErrorCode.BAD_URL
            if (!headersOk(drm.licenseHeaders)) return LanErrorCode.BAD_REQUEST
            if (drm.clearKeys.size > 32) return LanErrorCode.BAD_REQUEST
        }
        return null
    }

    /** null when the offer may be shown to the user, else a [LanErrorCode]. */
    fun checkPlaylist(playlist: SharedPlaylist): String? {
        if (playlist.name.isBlank() || playlist.name.length > MAX_NAME) return LanErrorCode.BAD_REQUEST
        val hasUrl = playlist.url != null
        val hasContent = playlist.content != null
        if (hasUrl == hasContent) return LanErrorCode.BAD_REQUEST
        if (hasUrl && !isHttpUrl(playlist.url)) return LanErrorCode.BAD_URL
        if (hasContent) {
            if (playlist.content!!.encodeToByteArray().size > LanProtocol.MAX_FILE_CONTENT_BYTES) return LanErrorCode.TOO_LARGE
            val file = playlist.fileName ?: return LanErrorCode.BAD_REQUEST
            if (file.isBlank() || file.length > MAX_NAME || file.any { it == '/' || it == '\\' || it.code < 0x20 }) {
                return LanErrorCode.BAD_REQUEST
            }
        }
        if (playlist.epgUrls.size > MAX_EPG_URLS || playlist.epgUrls.any { !isHttpUrl(it) }) return LanErrorCode.BAD_URL
        if (!headersOk(playlist.headers)) return LanErrorCode.BAD_REQUEST
        return null
    }

    /** "192.168.1.20:47123" → host and port; null when it is not an IPv4/hostname with a port. */
    fun parseHostPort(text: String): Pair<String, Int>? {
        val trimmed = text.trim()
        val host = trimmed.substringBeforeLast(':', "")
        val port = trimmed.substringAfterLast(':', "").toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        if (!Regex("^[A-Za-z0-9.-]{1,253}$").matches(host)) return null
        return host to port
    }

    /**
     * A short, non-reversible id of the /24 network of [host] (an IPv4 address), recorded with each
     * send (PRD §3.2). null for anything else.
     */
    fun networkId(host: String): String? {
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { p -> p.toIntOrNull()?.let { it in 0..255 } != true }) return null
        val prefix = parts.take(3).joinToString(".")
        return LanCrypto.sha256("tsiptv-net|$prefix.0/24".encodeToByteArray())
            .take(6).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
