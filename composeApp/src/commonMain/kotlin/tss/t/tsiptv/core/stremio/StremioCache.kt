package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** Resource kinds with their timeouts and cache defaults (PRD §8, research §8.2). */
enum class StremioRequestKind(
    /** Total timeout for one request. */
    val totalTimeoutMs: Long,
    val connectTimeoutMs: Long,
    /** Freshness when neither `Cache-Control` nor the body says anything. */
    val defaultMaxAgeSec: Long,
    /** Hard cap on how long a response may be used at all (fresh or stale), or null. */
    val capSec: Long?,
    /** Root key of the response body, null for the manifest. */
    val rootKey: String?,
) {
    MANIFEST(15_000, 10_000, 0, 0, null),
    CATALOG(15_000, 10_000, 3_600, null, "metas"),
    META(15_000, 10_000, 21_600, null, "meta"),
    STREAM(20_000, 10_000, 300, 300, "streams"),
    SUBTITLES(15_000, 10_000, 86_400, null, "subtitles"),
    ADDON_CATALOG(15_000, 10_000, 3_600, null, "addons"),
    BLOCKLIST(15_000, 10_000, 0, 0, null),
}

/** How long a response may be used: fresh, then stale while revalidating, then stale on error. */
data class CachePolicy(
    val maxAgeSec: Long,
    val staleWhileRevalidateSec: Long,
    val staleIfErrorSec: Long,
    val noStore: Boolean = false,
) {
    companion object {
        val NONE = CachePolicy(0, 0, 0, noStore = true)

        /**
         * `Cache-Control` `max-age` / `stale-while-revalidate` / `stale-if-error` win field by field;
         * else the body fields `cacheMaxAge` / `staleRevalidate` / `staleError`; else the kind's
         * default max-age and no stale windows. `no-store` disables caching; `no-cache` means
         * max-age 0. Streams are never used longer than 5 minutes (the kind's cap).
         */
        fun resolve(cacheControl: String?, body: JsonObject?, kind: StremioRequestKind): CachePolicy {
            if (kind.capSec == 0L) return NONE
            val directives = parseCacheControl(cacheControl)
            if ("no-store" in directives) return NONE
            val noCache = "no-cache" in directives
            val maxAge = if (noCache) 0L else directives["max-age"]?.toLongOrNull()
                ?: body?.get("cacheMaxAge").lenientLong()
                ?: kind.defaultMaxAgeSec
            val swr = directives["stale-while-revalidate"]?.toLongOrNull() ?: body?.get("staleRevalidate").lenientLong() ?: 0L
            val sie = directives["stale-if-error"]?.toLongOrNull() ?: body?.get("staleError").lenientLong() ?: 0L
            var policy = CachePolicy(maxAge.coerceAtLeast(0), swr.coerceAtLeast(0), sie.coerceAtLeast(0))
            kind.capSec?.let { cap ->
                val fresh = policy.maxAgeSec.coerceAtMost(cap)
                policy = CachePolicy(
                    maxAgeSec = fresh,
                    staleWhileRevalidateSec = policy.staleWhileRevalidateSec.coerceAtMost(cap - fresh),
                    staleIfErrorSec = policy.staleIfErrorSec.coerceAtMost(cap - fresh),
                )
            }
            return policy
        }

        internal fun parseCacheControl(header: String?): Map<String, String> {
            if (header.isNullOrBlank()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            header.split(',').forEach { part ->
                val key = part.substringBefore('=').trim().lowercase()
                if (key.isEmpty()) return@forEach
                val value = part.substringAfter('=', "").trim().trim('"')
                if (key !in out) out[key] = value
            }
            return out
        }
    }
}

/** One cached body. Times are epoch milliseconds. */
data class CacheEntry(
    val body: String,
    val fetchedAtMs: Long,
    val freshUntilMs: Long,
    val staleWhileRevalidateUntilMs: Long,
    val staleIfErrorUntilMs: Long,
) {
    val sizeBytes: Long get() = body.length * 2L + 64
    fun isFresh(nowMs: Long) = nowMs < freshUntilMs
    fun canRevalidateInBackground(nowMs: Long) = nowMs < staleWhileRevalidateUntilMs
    fun canServeOnError(nowMs: Long) = nowMs < staleIfErrorUntilMs

    companion object {
        fun of(body: String, nowMs: Long, policy: CachePolicy): CacheEntry {
            val fresh = nowMs + policy.maxAgeSec * 1000
            return CacheEntry(
                body = body,
                fetchedAtMs = nowMs,
                freshUntilMs = fresh,
                staleWhileRevalidateUntilMs = fresh + policy.staleWhileRevalidateSec * 1000,
                staleIfErrorUntilMs = fresh + policy.staleIfErrorSec * 1000,
            )
        }
    }
}

/**
 * In-memory response cache keyed by full URL, LRU by approximate size (default 20 MiB, PRD §8).
 * Keys are URLs that may contain secrets: this cache is never persisted or logged.
 */
class StremioResponseCache(private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, CacheEntry>()
    private var totalBytes = 0L

    suspend fun get(url: String): CacheEntry? = mutex.withLock {
        val entry = entries.remove(url) ?: return@withLock null
        entries[url] = entry // most recently used last
        entry
    }

    suspend fun put(url: String, entry: CacheEntry) = mutex.withLock {
        entries.remove(url)?.let { totalBytes -= it.sizeBytes }
        val size = entry.sizeBytes
        if (size > maxBytes) return@withLock
        entries[url] = entry
        totalBytes += size
        while (totalBytes > maxBytes && entries.isNotEmpty()) {
            val eldest = entries.keys.first()
            totalBytes -= entries.remove(eldest)!!.sizeBytes
        }
    }

    suspend fun remove(url: String) = mutex.withLock {
        entries.remove(url)?.let { totalBytes -= it.sizeBytes }
    }

    /** Drops every entry whose URL starts with [prefix] (e.g. all of one addon after removal). */
    suspend fun removeByPrefix(prefix: String) = mutex.withLock {
        val keys = entries.keys.filter { it.startsWith(prefix) }
        keys.forEach { totalBytes -= entries.remove(it)!!.sizeBytes }
    }

    suspend fun clear() = mutex.withLock {
        entries.clear()
        totalBytes = 0
    }

    suspend fun sizeBytes(): Long = mutex.withLock { totalBytes }
    suspend fun count(): Int = mutex.withLock { entries.size }

    companion object {
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
    }
}
