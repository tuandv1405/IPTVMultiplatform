package tss.t.tsiptv.core.firebase.analystics

/**
 * Turns a link the user added into something that may be sent to Analytics.
 *
 * Playlist links often carry credentials: `user:pass@host`, `?username=&password=`
 * (Xtream Codes), tokens in the path, and Kodi `|Authorization=` suffixes. What is kept:
 * scheme, host, port and the path, with secret-looking path segments replaced by `*`.
 * Userinfo, query, fragment and the `|` suffix are always dropped. The result is capped at
 * [MAX_VALUE_LENGTH], Analytics' limit for a parameter value.
 */
object AnalyticsLinks {
    const val MAX_VALUE_LENGTH = 100

    private val SCHEME_AND_REST = Regex("^(https?)://([^/?#]*)([^?#]*)", RegexOption.IGNORE_CASE)

    /** `https://host[:port]/path` with secrets removed, or null for non-http(s) links. */
    fun sanitized(raw: String): String? {
        val parts = parse(raw) ?: return null
        val path = parts.path.split('/').joinToString("/") { if (looksSecret(it)) "*" else it }
        return "${parts.scheme}://${parts.hostPort}$path".take(MAX_VALUE_LENGTH)
    }

    /** Lower-case host without port or userinfo, or null for non-http(s) links. */
    fun host(raw: String): String? = parse(raw)?.hostPort?.substringBefore(':')?.takeIf { it.isNotEmpty() }

    /** `https` / `http`, or null for other links. */
    fun scheme(raw: String): String? = parse(raw)?.scheme

    private class Parts(val scheme: String, val hostPort: String, val path: String)

    private fun parse(raw: String): Parts? {
        val link = raw.trim().substringBefore('|')
        val m = SCHEME_AND_REST.find(link) ?: return null
        val hostPort = m.groupValues[2].substringAfterLast('@').lowercase()
        if (hostPort.isEmpty()) return null
        return Parts(m.groupValues[1].lowercase(), hostPort, m.groupValues[3])
    }

    /**
     * A path segment that is probably a token, key or password: long and mixing letters and
     * digits, or containing `=`, or a long run of hex. Ordinary names ("playlist.m3u",
     * "get.php", "live") are kept.
     */
    internal fun looksSecret(segment: String): Boolean {
        if (segment.isEmpty()) return false
        if ('=' in segment || '@' in segment || ':' in segment) return true
        val name = segment.substringBeforeLast('.')
        val hasDigit = name.any { it.isDigit() }
        val hasLetter = name.any { it.isLetter() }
        if (name.length >= 16 && hasDigit && hasLetter) return true
        if (name.length >= 24) return true
        return name.length >= 12 && name.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } && hasLetter
    }
}
