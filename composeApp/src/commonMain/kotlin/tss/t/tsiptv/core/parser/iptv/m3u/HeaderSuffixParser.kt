package tss.t.tsiptv.core.parser.iptv.m3u

/**
 * Kodi's `<url>|name=value&name=value` syntax, and the same `name=urlencoded&...` lists used by
 * `inputstream.adaptive.*_headers` and DRM licence headers.
 */
object HeaderSuffixParser {

    data class Split(
        val url: String,
        val headers: List<Pair<String, String>>,
    )

    /**
     * Splits a URL line at the first `|`. The part before it is the stream URL; players must
     * never see the suffix (Media3 would request a URL containing `|`).
     */
    fun split(line: String): Split {
        val pipe = line.indexOf('|')
        if (pipe < 0) return Split(line.trim(), emptyList())
        return Split(
            url = line.substring(0, pipe).trim(),
            headers = parseHeaderList(line.substring(pipe + 1)),
        )
    }

    /**
     * Parses `name=value&name=value`. Values are percent-decoded; lists often carry them
     * unencoded (`user-agent=Mozilla/5.0 (Windows NT 10.0)`), which decodes to itself.
     * Kodi's own fields are mapped: a leading `!` (Kodi's "send non-standard header" flag) is
     * dropped, `cookies` becomes `Cookie`, and `seekable` is a Kodi player option, not a header.
     */
    fun parseHeaderList(text: String): List<Pair<String, String>> =
        text.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val rawName = part.substring(0, eq).trim().removePrefix("!").trim()
            if (rawName.isEmpty() || rawName.equals("seekable", ignoreCase = true)) {
                return@mapNotNull null
            }
            val header = canonicalName(percentDecode(rawName)) to percentDecode(part.substring(eq + 1).trim())
            header.takeIf { isValidHeader(it.first, it.second) }
        }

    /**
     * Whether an HTTP stack can send this header. OkHttp throws on a name that is not an HTTP
     * token and on a value with control characters (CR/LF would also allow header injection) or
     * non-ASCII text; such a header is dropped rather than failing the whole channel.
     */
    fun isValidHeader(name: String, value: String): Boolean =
        name.isNotEmpty() && name.all { it in TOKEN_CHARS } &&
                value.all { it == '\t' || it in ' '..'~' }

    /** [isValidHeader] applied to a map. */
    fun sanitize(headers: Map<String, String>): Map<String, String> =
        headers.filter { (name, value) -> isValidHeader(name, value) }

    private val TOKEN_CHARS: Set<Char> =
        (('a'..'z') + ('A'..'Z') + ('0'..'9') + "!#$%&'*+-.^_`|~".toList()).toSet()

    /** Header names are case-insensitive; use the usual spelling for the common ones. */
    fun canonicalName(name: String): String = CANONICAL[name.lowercase()] ?: name

    /**
     * Decodes `%XX` escapes as UTF-8. `+` is left alone: tokens are often base64 and a `+`
     * there is data, not a space. A malformed escape keeps the whole value as written,
     * because guessing at a broken token is worse than sending it unchanged.
     */
    fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val out = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%') {
                val hi = value.getOrNull(i + 1)?.digitToIntOrNull(16) ?: return value
                val lo = value.getOrNull(i + 2)?.digitToIntOrNull(16) ?: return value
                out.add(((hi shl 4) or lo).toByte())
                i += 3
            } else {
                c.toString().encodeToByteArray().forEach { out.add(it) }
                i++
            }
        }
        // Escapes that are not valid UTF-8 would become U+FFFD; keep what was written instead.
        return runCatching { out.toByteArray().decodeToString(throwOnInvalidSequence = true) }
            .getOrDefault(value)
    }

    private val CANONICAL = mapOf(
        "user-agent" to "User-Agent",
        "referer" to "Referer",
        "referrer" to "Referer",
        "cookie" to "Cookie",
        "cookies" to "Cookie",
        "origin" to "Origin",
        "authorization" to "Authorization",
        "accept" to "Accept",
        "accept-language" to "Accept-Language",
        "content-type" to "Content-Type",
        "x-forwarded-for" to "X-Forwarded-For",
    )
}

/**
 * Headers from several playlist syntaxes, merged by name without regard to case. Layers are
 * added from lowest to highest priority; a later value for the same name replaces an earlier one.
 */
internal class HeaderMerger {
    private val entries = LinkedHashMap<String, Pair<String, String>>()

    fun put(name: String, value: String) {
        if (!HeaderSuffixParser.isValidHeader(name, value.trim())) return
        val key = name.lowercase()
        entries.remove(key)
        entries[key] = name to value.trim()
    }

    fun putAll(headers: Iterable<Pair<String, String>>) = headers.forEach { (n, v) -> put(n, v) }

    fun toMap(): Map<String, String> = entries.values.associate { it }
}
