package tss.t.tsiptv.core.stremio

/**
 * Percent-encoding exactly like JavaScript `encodeURIComponent` / core's `URI_COMPONENT_ENCODE_SET`
 * (research §1.3): every UTF-8 byte except ASCII alphanumerics and `- _ . ! ~ * ' ( )` is escaped.
 * Space is `%20`, never `+`.
 */
object StremioUrlEncoding {
    private const val UNRESERVED_MARKS = "-_.!~*'()"
    private val HEX = "0123456789ABCDEF".toCharArray()

    fun encodeComponent(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (byte in value.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in UNRESERVED_MARKS)) {
                sb.append(c)
            } else {
                sb.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return sb.toString()
    }

    /** `extra` segment: `enc(k)=enc(v)` joined by `&`, in the given order; repeated keys stay repeated. */
    fun encodeExtra(extra: List<Pair<String, String>>): String =
        extra.joinToString("&") { (k, v) -> encodeComponent(k) + "=" + encodeComponent(v) }

    /** Percent-decoding (UTF-8). Invalid escapes are kept literally. [plusAsSpace] for query strings. */
    fun decodeComponent(value: String, plusAsSpace: Boolean = false): String {
        if ('%' !in value && !(plusAsSpace && '+' in value)) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 < value.length &&
                    hexValue(value[i + 1]) >= 0 && hexValue(value[i + 2]) >= 0 -> {
                    bytes += ((hexValue(value[i + 1]) shl 4) or hexValue(value[i + 2])).toByte()
                    i += 3
                    continue
                }
                c == '+' && plusAsSpace -> bytes += ' '.code.toByte()
                c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> {
                    value.substring(i, i + 2).encodeToByteArray().forEach { bytes += it }
                    i += 2
                    continue
                }
                else -> c.toString().encodeToByteArray().forEach { bytes += it }
            }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /**
     * Escapes characters that cannot appear in a URL as typed (space, controls, non-ASCII, `"<>\^`{|}`)
     * while leaving everything else, including existing `%XX` escapes, untouched.
     */
    internal fun escapeIllegal(value: String): String {
        if (value.all { it.code in 0x21..0x7E && it !in ILLEGAL }) return value
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch.code in 0x21..0x7E && ch !in ILLEGAL) {
                sb.append(ch)
                i++
                continue
            }
            val end = if (ch.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate()) i + 2 else i + 1
            value.substring(i, end).encodeToByteArray().forEach { b ->
                val v = b.toInt() and 0xFF
                sb.append('%').append(HEX[v shr 4]).append(HEX[v and 0x0F])
            }
            i = end
        }
        return sb.toString()
    }

    private const val ILLEGAL = "\"<>\\^`{|}"
}

/**
 * An addon's transport URL, split so resource URLs can be built from it (research §1.1):
 * `scheme://authority{basePath}/{resource}/{type}/{id}[/{extra}].json[?{query}]`, where
 * `basePath` is the manifest path without its **last** `/manifest.json`, kept verbatim (configured
 * addons keep user data there) and the manifest's query string is kept after `.json`.
 *
 * The transport URL can contain secrets. Never log [manifestUrl]; show [displayHost] instead.
 */
class StremioTransport private constructor(
    val scheme: String,
    /** `[userinfo@]host[:port]` exactly as given, host lower-cased. */
    val authority: String,
    /** Host without port (IPv6 hosts keep their brackets), lower-case. */
    val host: String,
    val port: Int?,
    val basePath: String,
    val query: String?,
) {
    val manifestUrl: String get() = "$scheme://$authority$basePath$MANIFEST_SUFFIX" + (query?.let { "?$it" } ?: "")

    /** Host and port for display and for the blocklist; never the path. */
    val displayHost: String get() = if (port != null) "$host:$port" else host

    val isHttps: Boolean get() = scheme == "https"

    /** `{base}/configure`, opened in the browser for `configurable` addons. No query string. */
    val configureUrl: String get() = "$scheme://$authority$basePath/configure"

    fun resourceUrl(
        resource: String,
        type: String,
        id: String,
        extra: List<Pair<String, String>> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append(scheme).append("://").append(authority).append(basePath)
        sb.append('/').append(StremioUrlEncoding.encodeComponent(resource))
        sb.append('/').append(StremioUrlEncoding.encodeComponent(type))
        sb.append('/').append(StremioUrlEncoding.encodeComponent(id))
        if (extra.isNotEmpty()) sb.append('/').append(StremioUrlEncoding.encodeExtra(extra))
        sb.append(".json")
        if (query != null) sb.append('?').append(query)
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean = other is StremioTransport && other.manifestUrl == manifestUrl
    override fun hashCode(): Int = manifestUrl.hashCode()
    override fun toString(): String = "StremioTransport($displayHost)" // never the path: it may hold secrets

    companion object {
        const val MANIFEST_SUFFIX = "/manifest.json"

        /**
         * Parses an `http(s)://…/manifest.json` URL (already normalised). Returns null for anything
         * else. Use [ManifestUrlNormalizer] for user input.
         */
        fun parse(url: String): StremioTransport? {
            val parts = UrlParts.split(url) ?: return null
            if (parts.scheme != "http" && parts.scheme != "https") return null
            if (!parts.path.endsWith(MANIFEST_SUFFIX)) return null
            return StremioTransport(
                scheme = parts.scheme,
                authority = parts.authority,
                host = parts.host,
                port = parts.port,
                basePath = parts.path.removeSuffix(MANIFEST_SUFFIX),
                query = parts.query,
            )
        }
    }
}

/** Minimal URL splitter: keeps path and query verbatim (Ktor's `Url` re-encodes). */
internal data class UrlParts(
    val scheme: String,
    val authority: String,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
) {
    companion object {
        fun split(raw: String): UrlParts? {
            val schemeEnd = raw.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = raw.substring(0, schemeEnd).lowercase()
            if (!scheme.all { it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' }) return null
            var rest = raw.substring(schemeEnd + 3).substringBefore('#')
            val authorityEnd = rest.indexOfAny(charArrayOf('/', '?')).let { if (it < 0) rest.length else it }
            val rawAuthority = rest.substring(0, authorityEnd)
            rest = rest.substring(authorityEnd)
            val userInfo = rawAuthority.substringBeforeLast('@', missingDelimiterValue = "")
            val hostPort = rawAuthority.substringAfterLast('@')
            val host: String
            val portText: String?
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                host = hostPort.substring(0, close + 1).lowercase()
                val after = hostPort.substring(close + 1)
                portText = if (after.startsWith(":")) after.substring(1) else if (after.isEmpty()) null else return null
            } else {
                host = hostPort.substringBefore(':').lowercase()
                portText = if (':' in hostPort) hostPort.substringAfter(':') else null
            }
            if (host.isEmpty() || host.any { it.isWhitespace() || it == '%' }) return null
            val port = when {
                portText == null -> null
                portText.isEmpty() -> null
                portText.all { it.isDigit() } && portText.length <= 5 -> portText.toInt().takeIf { it in 1..65535 } ?: return null
                else -> return null
            }
            val path = rest.substringBefore('?')
            val query = if ('?' in rest) rest.substringAfter('?') else null
            val authority = buildString {
                if (userInfo.isNotEmpty()) append(userInfo).append('@')
                append(host)
                if (port != null) append(':').append(port)
            }
            return UrlParts(scheme, authority, host, port, path, query?.takeIf { it.isNotEmpty() })
        }
    }
}

/** Why a pasted addon link was rejected; each maps to one string key (PRD "Error states"). */
enum class ManifestUrlError(val messageKey: String) {
    NOT_MANIFEST("addon_error_not_manifest"),
    LEGACY("addon_error_legacy"),
    LOCAL_SERVER("addon_error_local_server"),
}

sealed interface ManifestUrlResult {
    data class Ok(val transport: StremioTransport) : ManifestUrlResult
    data class Error(val error: ManifestUrlError) : ManifestUrlResult
}

/** Normalises what the user typed or pasted into a transport URL (PRD §1 "Input normalisation"). */
object ManifestUrlNormalizer {
    private const val LOCAL_SERVER_PORT = 11470

    fun normalize(input: String): ManifestUrlResult {
        var url = StremioUrlEncoding.escapeIllegal(input.trim())
        if (url.isEmpty()) return ManifestUrlResult.Error(ManifestUrlError.NOT_MANIFEST)
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return ManifestUrlResult.Error(ManifestUrlError.NOT_MANIFEST)
        val scheme = url.substring(0, schemeEnd).lowercase()
        when (scheme) {
            "stremio" -> {
                val rest = url.substring(schemeEnd + 3)
                // `stremio:///…` page links have no host: they are not install links.
                if (rest.startsWith("/")) return ManifestUrlResult.Error(ManifestUrlError.NOT_MANIFEST)
                val host = UrlParts.split("https://$rest")?.host
                    ?: return ManifestUrlResult.Error(ManifestUrlError.NOT_MANIFEST)
                url = (if (isLocalNetworkHost(host)) "http://" else "https://") + rest
            }
            "http", "https" -> url = scheme + url.substring(schemeEnd)
            else -> return ManifestUrlResult.Error(ManifestUrlError.NOT_MANIFEST) // ipfs://, ipns://, ftp://, …
        }
        val parts = UrlParts.split(url) ?: return ManifestUrlResult.Error(ManifestUrlError.NOT_MANIFEST)
        val lowerPath = parts.path.lowercase().trimEnd('/')
        if (lowerPath.endsWith("/stremio/v1") || "/stremio/v1/" in parts.path.lowercase()) {
            return ManifestUrlResult.Error(ManifestUrlError.LEGACY)
        }
        if (parts.port == LOCAL_SERVER_PORT && parts.host in LOOPBACK_HOSTS) {
            return ManifestUrlResult.Error(ManifestUrlError.LOCAL_SERVER)
        }
        val transport = StremioTransport.parse(url) ?: return ManifestUrlResult.Error(ManifestUrlError.NOT_MANIFEST)
        return ManifestUrlResult.Ok(transport)
    }

    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "[::1]")

    /** `localhost`, `127.x`, `10.x`, `192.168.x`, `172.16–31.x` (and `[::1]`): `stremio://` becomes `http://`. */
    fun isLocalNetworkHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "[::1]") return true
        val octets = h.split('.')
        if (octets.size != 4) return false
        val n = octets.map { o -> o.toIntOrNull()?.takeIf { it in 0..255 && o.isNotEmpty() && o.all(Char::isDigit) } ?: return false }
        return n[0] == 127 || n[0] == 10 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31)
    }
}

/**
 * `stremio:///…` page links (research §1.6), found in `meta.links[].url` and `externalUrl`.
 * Only discover, detail and search are routed; everything else → null (not shown).
 */
sealed interface StremioDeepLink {
    data class Search(val query: String) : StremioDeepLink
    data class Discover(
        /** Decoded transport URL of the addon the catalog belongs to (may be one that is not installed). */
        val transportUrl: String,
        val type: String,
        val catalogId: String,
        val extra: List<Pair<String, String>>,
    ) : StremioDeepLink {
        /** The transport URL can hold secrets: host only. */
        override fun toString(): String =
            "Discover(host=${UrlParts.split(transportUrl)?.host}, type=$type, catalogId=$catalogId, extra=${extra.map { it.first }})"
    }
    data class Detail(val type: String, val id: String, val videoId: String?, val autoPlay: Boolean) : StremioDeepLink

    companion object {
        const val PREFIX = "stremio:///"

        fun parse(link: String): StremioDeepLink? {
            val trimmed = link.trim()
            if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return null
            val rest = trimmed.substring(PREFIX.length).substringBefore('#')
            val path = rest.substringBefore('?')
            val query = parseQuery(if ('?' in rest) rest.substringAfter('?') else "")
            val segments = path.split('/').filter { it.isNotEmpty() }.map { StremioUrlEncoding.decodeComponent(it) }
            return when (segments.firstOrNull()) {
                "search" -> query.firstOrNull { it.first == "search" }?.second?.takeIf { it.isNotBlank() }?.let { Search(it) }
                "discover" -> if (segments.size >= 4) Discover(segments[1], segments[2], segments[3], query) else null
                "detail" -> if (segments.size >= 3) Detail(
                    type = segments[1],
                    id = segments[2],
                    videoId = segments.getOrNull(3),
                    autoPlay = query.any { it.first == "autoPlay" && it.second == "true" },
                ) else null
                else -> null
            }
        }

        private fun parseQuery(query: String): List<Pair<String, String>> =
            query.split('&').filter { it.isNotEmpty() }.map {
                StremioUrlEncoding.decodeComponent(it.substringBefore('='), plusAsSpace = true) to
                    StremioUrlEncoding.decodeComponent(it.substringAfter('=', ""), plusAsSpace = true)
            }
    }
}
