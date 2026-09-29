package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StremioClientTest {
    private val base = "https://tsiptv-8bdd6.web.app/examples/stremio-sampler"
    private val sampler = StremioFixtures.transport("stremio://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json")
    private val ua = StremioClient.userAgentFor("1.0.1")

    private var now = 1_000_000L
    private fun client(transport: StremioHttpTransport, scope: kotlinx.coroutines.CoroutineScope? = null) =
        StremioClient(transport, ua, StremioResponseCache(), nowMs = { now }, refreshScope = scope)

    // --- Against the static sampler (AC-S2, AC-S13, AC-S14 protocol side) ------------------

    @Test
    fun samplerEndToEnd() = runBlocking {
        val http = FakeTransport.samplerHost(base)
        val c = client(http)

        val manifest = assertIs<ManifestFetchResult.Success>(c.fetchManifest(sampler)).manifest
        assertEquals("Public Domain Sampler (static)", manifest.name)
        assertEquals("$base/manifest.json", http.requests.last())
        assertEquals(mapOf("Accept" to "application/json", "User-Agent" to "TSIPTV/1.0.1 (Stremio-addon-client)"), http.requestHeaders.last())
        assertEquals(StremioRequestKind.MANIFEST, http.kinds.last())

        val rows = ResourceMatcher.boardCatalogs(manifest)
        val movies = assertIs<AddonResult.Success<List<StremioMeta>>>(c.catalog(sampler, rows[0])).value
        assertEquals(listOf("His Girl Friday"), movies.map { it.name })

        val comedy = CatalogRequest("movie", "tspd-movie", listOf("genre" to "Comedy"))
        assertEquals(1, c.catalog(sampler, comedy).valueOr(emptyList()).size)
        assertEquals("$base/catalog/movie/tspd-movie/genre=Comedy.json", http.requests.last())

        val series = assertIs<AddonResult.Success<StremioMeta?>>(c.meta(sampler, "series", "tspd_superman")).value!!
        val episode = series.effectiveVideos.single { it.season == 1 }
        assertEquals("The Mechanical Monsters", episode.displayTitle)

        val streams = c.streams(sampler, "series", episode.id!!).valueOr(emptyList())
        assertEquals("tspd-archive-512kb", streams.single().hints.bingeGroup)
        assertEquals(StremioRequestKind.STREAM, http.kinds.last())

        // Static host 404 for an unknown id: a failure from this addon, flagged as not-found.
        val missing = assertIs<AddonResult.Failure>(c.meta(sampler, "movie", "tspd_nope"))
        assertTrue(missing.error.isNotFound)
        assertEquals("http_404", missing.error.code)
    }

    // --- Manifest errors (AC-S4) --------------------------------------------------------------

    @Test
    fun manifestErrors() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        assertEquals(
            ManifestFetchResult.Invalid("resources"),
            client(FakeTransport { FakeTransport.ok("""{"id":"a","name":"A","version":"1","types":[]}""") }).fetchManifest(t),
        )
        assertEquals(ManifestFetchResult.Invalid(null), client(FakeTransport { FakeTransport.ok("<html/>") }).fetchManifest(t))
        assertEquals(
            ManifestFetchResult.Unreachable(AddonError(AddonError.Kind.HTTP, 500)),
            client(FakeTransport { FakeTransport.status(500, """{"err":"handler error"}""") }).fetchManifest(t),
        )
        assertEquals(
            ManifestFetchResult.Unreachable(AddonError(AddonError.Kind.NETWORK)),
            client(FakeTransport { throw IllegalStateException("boom https://a.example.org/secret") }).fetchManifest(t),
        )
        val configRequired = assertIs<ManifestFetchResult.Success>(
            client(FakeTransport {
                FakeTransport.ok("""{"id":"a","name":"A","version":"1","types":[],"resources":[],"behaviorHints":{"configurationRequired":true}}""")
            }).fetchManifest(t)
        )
        assertTrue(configRequired.manifest.hints.isConfigurationRequired)
    }

    // --- Error mapping: every failure is an empty result from that addon --------------------

    @Test
    fun errorMapping() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        suspend fun streamsWith(response: StremioHttpResponse) =
            client(FakeTransport { response }).streams(t, "movie", "x")

        assertEquals("http_500", (streamsWith(FakeTransport.status(500)) as AddonResult.Failure).error.code)
        assertEquals("invalid_json", (streamsWith(FakeTransport.ok("<html>404</html>")) as AddonResult.Failure).error.code)
        assertEquals("invalid_json", (streamsWith(FakeTransport.ok("[]")) as AddonResult.Failure).error.code)
        assertEquals("missing_streams", (streamsWith(FakeTransport.ok("""{"err":"x"}""")) as AddonResult.Failure).error.code)
        assertEquals("missing_streams", (streamsWith(FakeTransport.ok("""{"streams":null}""")) as AddonResult.Failure).error.code)
        assertEquals(emptyList(), streamsWith(FakeTransport.ok("""{"streams":[]}""")).valueOrNull)
        assertEquals(emptyList(), streamsWith(FakeTransport.status(404)).valueOr(emptyList()))

        // meta null / {} are "not found here", not failures.
        val c = client(FakeTransport { FakeTransport.ok("""{"meta":null}""") })
        assertNull(assertIs<AddonResult.Success<StremioMeta?>>(c.meta(t, "movie", "a")).value)
        val c2 = client(FakeTransport { FakeTransport.ok("""{"meta":{}}""") })
        assertNull(assertIs<AddonResult.Success<StremioMeta?>>(c2.meta(t, "movie", "b")).value)
        val c3 = client(FakeTransport { FakeTransport.ok("""{"metas":[]}""") })
        assertEquals("missing_meta", (c3.meta(t, "movie", "c") as AddonResult.Failure).error.code)
    }

    @Test
    fun timeoutIsMapped() = runBlocking {
        val t = StremioFixtures.transport("https://slow.example.org/manifest.json")
        // The engine's own timeout exceptions (Ktor HttpRequestTimeoutException etc.) map to TIMEOUT.
        class HttpRequestTimeoutException : Exception()
        val engineTimeout = FakeTransport { throw HttpRequestTimeoutException() }
        assertEquals("timeout", (client(engineTimeout).catalog(t, CatalogRequest("movie", "x")) as AddonResult.Failure).error.code)
        assertEquals(20_000, StremioRequestKind.STREAM.totalTimeoutMs)
        assertEquals(15_000, StremioRequestKind.CATALOG.totalTimeoutMs)
        assertEquals(15_000, StremioRequestKind.META.totalTimeoutMs)
        assertEquals(15_000, StremioRequestKind.MANIFEST.totalTimeoutMs)
        assertEquals(10_000, StremioRequestKind.MANIFEST.connectTimeoutMs)
    }

    @Test
    fun clientTotalTimeoutFiresWhenTheTransportHangs() = runBlocking {
        val t = StremioFixtures.transport("https://slow.example.org/manifest.json")
        val hang = FakeTransport { delay(Long.MAX_VALUE); FakeTransport.ok("{}") }
        val budgets = mutableListOf<StremioRequestKind>()
        val c = StremioClient(hang, ua, nowMs = { now }, totalTimeoutMs = { budgets += it; 50 })
        assertEquals(ManifestFetchResult.Unreachable(AddonError(AddonError.Kind.TIMEOUT)), c.fetchManifest(t))
        assertEquals("timeout", (c.streams(t, "movie", "x") as AddonResult.Failure).error.code)
        assertEquals(listOf(StremioRequestKind.MANIFEST, StremioRequestKind.STREAM), budgets)
    }

    @Test
    fun cancellationIsNotSwallowed() = runBlocking {
        val t = StremioFixtures.transport("https://slow.example.org/manifest.json")
        val started = CompletableDeferred<Unit>()
        val hang = FakeTransport { started.complete(Unit); delay(Long.MAX_VALUE); FakeTransport.ok("{}") }
        val job = async { client(hang).streams(t, "movie", "x") }
        started.await()
        job.cancel()
        assertTrue(runCatching { job.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    // --- Cache (AC-S19) ---------------------------------------------------------------------

    @Test
    fun catalogIsServedFromCacheWithinMaxAge() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        val http = FakeTransport { FakeTransport.ok("""{"metas":[{"id":"a","type":"movie","name":"A"}]}""", "max-age=600") }
        val c = client(http)
        val request = CatalogRequest("movie", "top")
        assertEquals(ResultSource.NETWORK, (c.catalog(t, request) as AddonResult.Success).source)
        now += 599_000
        assertEquals(ResultSource.CACHE, (c.catalog(t, request) as AddonResult.Success).source)
        assertEquals(1, http.requests.size)
        now += 2_000
        assertEquals(ResultSource.NETWORK, (c.catalog(t, request) as AddonResult.Success).source)
        assertEquals(2, http.requests.size)
        // A different extra is a different URL.
        c.catalog(t, CatalogRequest("movie", "top", listOf("genre" to "Comedy")))
        assertEquals(3, http.requests.size)
    }

    @Test
    fun streamsAreRefetchedAfterFiveMinutesWhateverTheAddonSays() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        val body = StremioFixtures.read("mixed-streams.json") // cacheMaxAge 604800
        val http = FakeTransport { FakeTransport.ok(body, "max-age=604800, stale-while-revalidate=7776000, stale-if-error=31104000, public") }
        val c = client(http, scope = this)
        c.streams(t, "movie", "tt1")
        now += 299_000
        assertEquals(ResultSource.CACHE, (c.streams(t, "movie", "tt1") as AddonResult.Success).source)
        now += 2_000
        assertEquals(ResultSource.NETWORK, (c.streams(t, "movie", "tt1") as AddonResult.Success).source)
        assertEquals(2, http.requests.size)
    }

    @Test
    fun staleIfErrorServesCachedDataOnFailure() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        var fail = false
        val http = FakeTransport {
            if (fail) FakeTransport.status(503) else FakeTransport.ok("""{"meta":{"id":"tt1","type":"movie","name":"A"}}""", "max-age=60, stale-if-error=600")
        }
        val c = client(http)
        c.meta(t, "movie", "tt1")
        fail = true
        now += 120_000
        val stale = assertIs<AddonResult.Success<StremioMeta?>>(c.meta(t, "movie", "tt1"))
        assertEquals(ResultSource.STALE_ON_ERROR, stale.source)
        assertEquals("A", stale.value?.name)
        now += 600_000
        assertEquals("http_503", (c.meta(t, "movie", "tt1") as AddonResult.Failure).error.code)
    }

    @Test
    fun bodyCacheFieldsAreUsedWithoutHeaderAndFailuresAreNotCached() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        var calls = 0
        val http = FakeTransport {
            calls++
            if (calls == 1) FakeTransport.status(500) else FakeTransport.ok("""{"metas":[],"cacheMaxAge":30}""")
        }
        val c = client(http)
        assertTrue(c.catalog(t, CatalogRequest("movie", "x")).isFailure)
        c.catalog(t, CatalogRequest("movie", "x"))
        now += 29_000
        assertEquals(ResultSource.CACHE, (c.catalog(t, CatalogRequest("movie", "x")) as AddonResult.Success).source)
        now += 2_000
        c.catalog(t, CatalogRequest("movie", "x"))
        assertEquals(3, http.requests.size)
    }

    @Test
    fun staleWhileRevalidateReturnsStaleAndRefreshesInBackground() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        var version = 1
        val http = FakeTransport { FakeTransport.ok("""{"metas":[{"id":"v$version","type":"movie"}]}""", "max-age=10, stale-while-revalidate=100") }
        coroutineScope {
            val c = client(http, scope = this)
            val r = CatalogRequest("movie", "x")
            c.catalog(t, r)
            version = 2
            now += 20_000
            val stale = c.catalog(t, r) as AddonResult.Success
            assertEquals(ResultSource.STALE_REVALIDATING, stale.source)
            assertEquals("v1", stale.value.single().id)
            repeat(5) { yield() }
            assertEquals(2, http.requests.size)
            val fresh = c.catalog(t, r) as AddonResult.Success
            assertEquals(ResultSource.CACHE, fresh.source)
            assertEquals("v2", fresh.value.single().id)
        }
    }

    @Test
    fun invalidateDropsOnlyThatAddon() = runBlocking {
        val a = StremioFixtures.transport("https://a.example.org/cfg1/manifest.json")
        val b = StremioFixtures.transport("https://a.example.org/cfg2/manifest.json")
        val http = FakeTransport { FakeTransport.ok("""{"metas":[]}""", "max-age=600") }
        val c = client(http)
        c.catalog(a, CatalogRequest("movie", "x"))
        c.catalog(b, CatalogRequest("movie", "x"))
        c.invalidate(a)
        c.catalog(a, CatalogRequest("movie", "x"))
        c.catalog(b, CatalogRequest("movie", "x"))
        assertEquals(3, http.requests.size)
    }

    // --- Concurrency limits -------------------------------------------------------------------

    @Test
    fun atMostSixParallelRequestsPerHost() = runBlocking {
        var inFlight = 0
        var peak = 0
        val gate = CompletableDeferred<Unit>()
        val http = FakeTransport {
            inFlight++
            peak = maxOf(peak, inFlight)
            gate.await()
            inFlight--
            FakeTransport.ok("""{"streams":[]}""")
        }
        val c = client(http)
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        coroutineScope {
            val jobs = (1..10).map { i -> async { c.streams(t, "movie", "id$i") } }
            repeat(20) { yield() }
            assertEquals(6, inFlight)
            gate.complete(Unit)
            jobs.awaitAll()
        }
        assertEquals(6, peak)
        assertEquals(10, http.requests.size)
    }

    @Test
    fun aSlowHostDoesNotStarveOtherHosts() = runBlocking {
        val slowGate = CompletableDeferred<Unit>()
        val http = FakeTransport { url ->
            if ("slow.example.org" in url) slowGate.await()
            FakeTransport.ok("""{"streams":[]}""")
        }
        // Global limit 4, per host 2: 20 queued requests to the slow host may hold at most 2 permits.
        val c = StremioClient(http, ua, nowMs = { now }, maxConcurrentRequests = 4, maxRequestsPerHost = 2)
        val slow = StremioFixtures.transport("https://slow.example.org/manifest.json")
        val fast = StremioFixtures.transport("https://fast.example.org/manifest.json")
        coroutineScope {
            val slowJobs = (1..20).map { i -> async { c.streams(slow, "movie", "s$i") } }
            repeat(20) { yield() }
            val fastResults = (1..5).map { i -> async { c.streams(fast, "movie", "f$i") } }.awaitAll()
            assertTrue(fastResults.all { !it.isFailure })
            assertEquals(5, http.requests.count { "fast.example.org" in it })
            assertEquals(2, http.requests.count { "slow.example.org" in it })
            slowGate.complete(Unit)
            slowJobs.awaitAll()
        }
        assertEquals(20, http.requests.count { "slow.example.org" in it })
    }

    @Test
    fun bomTooLargeAndNoStaleForClientErrors() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        val bom = client(FakeTransport { FakeTransport.ok("\uFEFF{\"streams\":[{\"url\":\"https://a/b.mp4\"}]}") })
        assertEquals(1, bom.streams(t, "movie", "x").valueOr(emptyList()).size)

        val tooLarge = client(FakeTransport { throw StremioBodyTooLargeException() })
        assertEquals("too_large", (tooLarge.catalog(t, CatalogRequest("movie", "x")) as AddonResult.Failure).error.code)

        var status = 200
        val http = FakeTransport {
            if (status == 200) FakeTransport.ok("""{"meta":{"id":"tt1","type":"movie"}}""", "max-age=60, stale-if-error=6000")
            else FakeTransport.status(status)
        }
        val c = client(http)
        c.meta(t, "movie", "tt1")
        now += 120_000
        status = 404 // the addon says it is gone: no stale copy
        val gone = assertIs<AddonResult.Failure>(c.meta(t, "movie", "tt1"))
        assertTrue(gone.error.isClientError)
        status = 502 // server error: the stale copy is used
        assertEquals(ResultSource.STALE_ON_ERROR, (c.meta(t, "movie", "tt1") as AddonResult.Success).source)
    }

    @Test
    fun duplicateCatalogIdsAreDroppedBecauseTheyAreListKeys() = runBlocking {
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        val http = FakeTransport {
            FakeTransport.ok("""{"metas":[{"id":"a","type":"movie","name":"A"},{"id":"a","type":"movie","name":"A again"},{"id":"b","type":"movie"}]}""")
        }
        val items = client(http).catalog(t, CatalogRequest("movie", "x")).valueOr(emptyList())
        assertEquals(listOf("a", "b"), items.map { it.id })
        assertEquals("A", items.first().name)
    }

    // --- Subtitles ------------------------------------------------------------------------------

    @Test
    fun subtitlesDropLocalServerUrls() = runBlocking {
        val t = StremioFixtures.transport("https://subs.example.org/manifest.json")
        val http = FakeTransport {
            FakeTransport.ok(
                """{"subtitles":[{"id":"1","url":"https://subs.example.org/a.srt","lang":"eng","label":"English [CC]"},
                   {"id":"2","url":"http://127.0.0.1:11470/subtitles.vtt?from=x","lang":"eng"},{"id":"3","lang":"fre"},
                   {"id":"4","url":"http://[::1]:11470/subtitles.vtt","lang":"eng"}]}"""
            )
        }
        val subs = client(http).subtitles(t, "movie", "tt1", listOf("filename" to "a b.mkv")).valueOr(emptyList())
        assertEquals(listOf("English [CC]"), subs.map { it.displayLabel })
        assertEquals("https://subs.example.org/subtitles/movie/tt1/filename=a%20b.mkv.json", http.requests.single())
    }
}
