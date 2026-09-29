package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StremioCacheTest {
    private fun body(json: String) = StremioJson.parseToJsonElement(json).jsonObject

    @Test
    fun headerWinsOverBodyFieldByField() {
        val p = CachePolicy.resolve(
            "public, max-age=600, stale-if-error=60",
            body("""{"cacheMaxAge":3600,"staleRevalidate":120,"staleError":999}"""),
            StremioRequestKind.CATALOG,
        )
        assertEquals(CachePolicy(600, 120, 60), p)
    }

    @Test
    fun bodyFieldsWhenHeaderIsMissingOrHasNoMaxAge() {
        assertEquals(
            CachePolicy(3600, 10, 20),
            CachePolicy.resolve("public", body("""{"cacheMaxAge":"3600","staleRevalidate":10,"staleError":20}"""), StremioRequestKind.META),
        )
    }

    @Test
    fun defaultsPerKind() {
        assertEquals(3_600, CachePolicy.resolve(null, null, StremioRequestKind.CATALOG).maxAgeSec)
        assertEquals(21_600, CachePolicy.resolve(null, null, StremioRequestKind.META).maxAgeSec)
        assertEquals(300, CachePolicy.resolve(null, null, StremioRequestKind.STREAM).maxAgeSec)
        assertTrue(CachePolicy.resolve("max-age=999", null, StremioRequestKind.MANIFEST).noStore)
    }

    @Test
    fun streamsAreNeverUsedLongerThanFiveMinutes() {
        val sdk = CachePolicy.resolve(
            "max-age=604800, stale-while-revalidate=7776000, stale-if-error=31104000, public",
            body("""{"cacheMaxAge":604800,"staleRevalidate":7776000,"staleError":31104000}"""),
            StremioRequestKind.STREAM,
        )
        assertEquals(CachePolicy(300, 0, 0), sdk)
        val short = CachePolicy.resolve("max-age=60, stale-if-error=1000", null, StremioRequestKind.STREAM)
        assertEquals(CachePolicy(60, 0, 240), short)
    }

    @Test
    fun noStoreAndNoCache() {
        assertTrue(CachePolicy.resolve("no-store, max-age=100", null, StremioRequestKind.CATALOG).noStore)
        val noCache = CachePolicy.resolve("no-cache, stale-if-error=50", body("""{"cacheMaxAge":100}"""), StremioRequestKind.CATALOG)
        assertEquals(CachePolicy(0, 0, 50), noCache)
        assertEquals(mapOf("max-age" to "5", "public" to ""), CachePolicy.parseCacheControl(" Max-Age=\"5\" , public ,"))
    }

    @Test
    fun entryWindows() {
        val e = CacheEntry.of("{}", 1_000, CachePolicy(10, 5, 20))
        assertTrue(e.isFresh(10_999))
        assertFalse(e.isFresh(11_000))
        assertTrue(e.canRevalidateInBackground(15_999))
        assertFalse(e.canRevalidateInBackground(16_000))
        assertTrue(e.canServeOnError(30_999))
        assertFalse(e.canServeOnError(31_000))
    }

    @Test
    fun lruEvictsTheLeastRecentlyUsedBySize() = runBlocking {
        val entrySize = CacheEntry.of("x".repeat(100), 0, CachePolicy(1, 0, 0)).sizeBytes
        val cache = StremioResponseCache(maxBytes = entrySize * 2)
        cache.put("a", CacheEntry.of("x".repeat(100), 0, CachePolicy(1, 0, 0)))
        cache.put("b", CacheEntry.of("y".repeat(100), 0, CachePolicy(1, 0, 0)))
        assertNotNull(cache.get("a")) // a is now most recent
        cache.put("c", CacheEntry.of("z".repeat(100), 0, CachePolicy(1, 0, 0)))
        assertNull(cache.get("b"))
        assertNotNull(cache.get("a"))
        assertNotNull(cache.get("c"))
        assertEquals(entrySize * 2, cache.sizeBytes())

        // Replacing an entry does not double count; too-large entries are not stored.
        cache.put("a", CacheEntry.of("x".repeat(100), 0, CachePolicy(1, 0, 0)))
        assertEquals(entrySize * 2, cache.sizeBytes())
        cache.put("huge", CacheEntry.of("h".repeat(1000), 0, CachePolicy(1, 0, 0)))
        assertNull(cache.get("huge"))
        assertEquals(2, cache.count())

        cache.removeByPrefix("a")
        assertEquals(1, cache.count())
        cache.clear()
        assertEquals(0L, cache.sizeBytes())
    }
}
