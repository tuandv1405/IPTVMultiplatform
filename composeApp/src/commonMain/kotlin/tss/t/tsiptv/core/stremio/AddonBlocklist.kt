package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * The remote kill switch (PRD "Policy guardrails"): `{"ids":[],"hosts":[]}`, used only for
 * takedown requests. A host entry blocks that host and its subdomains; an entry with a port
 * (`host:8080`) blocks only that port.
 */
@Serializable
data class AddonBlocklist(
    val ids: List<String> = emptyList(),
    val hosts: List<String> = emptyList(),
) {
    fun isBlocked(addonId: String, transport: StremioTransport): Boolean =
        isIdBlocked(addonId) || isHostBlocked(transport.host, transport.port, transport.scheme)

    fun isIdBlocked(addonId: String): Boolean = ids.any { it.trim().isNotEmpty() && it.trim() == addonId.trim() }

    /**
     * [port] null means the scheme's default port, so `host:443` matches `https://host/…`.
     * Entries are normalised (see [normaliseEntry]).
     */
    fun isHostBlocked(host: String, port: Int? = null, scheme: String = "https"): Boolean {
        val h = normaliseHost(host)
        val effectivePort = port ?: defaultPort(scheme)
        return hosts.any { raw ->
            val (entryHost, entryPort) = normaliseEntry(raw) ?: return@any false
            val hostMatches = h == entryHost || h.endsWith(".$entryHost")
            hostMatches && (entryPort == null || entryPort == effectivePort)
        }
    }

    companion object {
        val EMPTY = AddonBlocklist()

        private fun defaultPort(scheme: String): Int? = when (scheme.lowercase()) {
            "https" -> 443
            "http" -> 80
            else -> null
        }

        private fun normaliseHost(host: String): String = host.trim().lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')

        /**
         * `https://Evil.example:443/x` → (`evil.example`, 443); `*.evil.example` → `evil.example`;
         * `[2001:db8::1]:8080` → (`2001:db8::1`, 8080); a bare IPv6 address has no port. Blank → null.
         */
        internal fun normaliseEntry(raw: String): Pair<String, Int?>? {
            var e = raw.trim().lowercase()
            val schemeEnd = e.indexOf("://")
            if (schemeEnd > 0) {
                e = e.substring(schemeEnd + 3)
            }
            e = e.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
            e = e.removePrefix("*.").trimEnd('.')
            if (e.isEmpty()) return null
            val host: String
            var port: Int? = null
            if (e.startsWith("[")) {
                val close = e.indexOf(']')
                if (close < 0) return null
                host = e.substring(1, close)
                e.substring(close + 1).removePrefix(":").takeIf { it.isNotEmpty() }?.let { port = it.toIntOrNull() ?: return null }
            } else if (e.count { it == ':' } == 1) {
                host = e.substringBefore(':')
                port = e.substringAfter(':').toIntOrNull() ?: return null
            } else {
                host = e // a hostname, or a bare IPv6 address
            }
            return host.trimEnd('.').takeIf { it.isNotEmpty() }?.let { it to port }
        }
    }
}

/** Fetches the blocklist from TS IPTV's own site, at most once a day (the caller persists the time). */
class AddonBlocklistFetcher(
    private val transport: StremioHttpTransport,
    private val userAgent: String,
    private val url: String = BLOCKLIST_URL,
) {
    /** The list, or null on any failure (keep using the previous one). */
    suspend fun fetch(): AddonBlocklist? = try {
        val response = transport.get(
            url,
            mapOf("Accept" to "application/json", "User-Agent" to userAgent),
            StremioRequestKind.BLOCKLIST,
        )
        if (response.status !in 200..299) null
        else StremioJson.decodeFromString(AddonBlocklist.serializer(), response.body.withoutBom())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        /** TS IPTV's own host (F4 publishes `web/public/policy/addon-blocklist.json`). */
        const val BLOCKLIST_URL = "https://tsiptv-8bdd6.web.app/policy/addon-blocklist.json"
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** Whether a daily check is due; `lastCheckedAtMs` null = never checked. Clock going backwards → due. */
        fun isDue(lastCheckedAtMs: Long?, nowMs: Long): Boolean =
            lastCheckedAtMs == null || nowMs - lastCheckedAtMs >= CHECK_INTERVAL_MS || nowMs < lastCheckedAtMs
    }
}
