package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogPagerTest {
    private val pagedCatalog = ManifestCatalog(
        "movie", "big",
        extras = listOf(CatalogExtra("genre", options = listOf("Comedy")), CatalogExtra("search"), CatalogExtra("skip")),
    )

    private fun items(from: Int, count: Int) = (from until from + count).map { StremioMeta(id = "m$it", type = "movie", name = "M$it") }

    /** A 250-item addon that pages by [pageSize] from `skip`. */
    private fun serve(request: CatalogRequest, total: Int = 250, pageSize: Int = 100): List<StremioMeta> {
        val skip = request.extra.firstOrNull { it.first == "skip" }?.second?.toInt() ?: 0
        return items(skip, (total - skip).coerceIn(0, pageSize))
    }

    @Test
    fun acceptanceS10PagesThroughTwoHundredFiftyItems() {
        val pager = CatalogPager(pagedCatalog)
        val skips = mutableListOf<String?>()
        val shown = mutableListOf<StremioMeta>()
        while (true) {
            val request = pager.nextRequest() ?: break
            skips += request.extra.firstOrNull { it.first == "skip" }?.second
            shown += pager.accept(serve(request))
        }
        assertEquals(listOf(null, "100", "200", "250"), skips)
        assertEquals(250, shown.size)
        assertEquals(250, shown.map { it.id }.toSet().size)
        assertTrue(pager.isEnd)
    }

    @Test
    fun lenientRuleKeepsPagingAddonsThatReturnTwentyPerPage() {
        val pager = CatalogPager(pagedCatalog)
        val skips = mutableListOf<String?>()
        var shown = 0
        while (true) {
            val request = pager.nextRequest() ?: break
            skips += request.extra.firstOrNull { it.first == "skip" }?.second
            shown += pager.accept(serve(request, total = 45, pageSize = 20)).size
            if (!pager.isEnd) assertTrue(pager.lastPageWasShort)
        }
        assertEquals(listOf(null, "20", "40", "45"), skips)
        assertEquals(45, shown)
    }

    @Test
    fun pageOfOnlyKnownIdsEndsPagingAndShowsNoDuplicates() {
        val pager = CatalogPager(pagedCatalog)
        assertEquals(100, pager.accept(items(0, 100)).size)
        assertEquals("100", pager.nextRequest()!!.extra.single { it.first == "skip" }.second)
        // An addon that ignores skip returns the first page again.
        assertEquals(emptyList(), pager.accept(items(0, 100)))
        assertTrue(pager.isEnd)
        assertNull(pager.nextRequest())
        assertEquals(100, pager.itemCount)
    }

    @Test
    fun partiallyNewPageContinues() {
        val pager = CatalogPager(pagedCatalog)
        pager.accept(items(0, 100))
        val fresh = pager.accept(items(50, 100))
        assertEquals(50, fresh.size)
        assertFalse(pager.isEnd)
        assertEquals("200", pager.nextRequest()!!.extra.single { it.first == "skip" }.second)
    }

    @Test
    fun catalogWithoutSkipHasOnePage() {
        val catalog = ManifestCatalog("movie", "tspd-movie", extras = listOf(CatalogExtra("genre", options = listOf("Comedy"))))
        val pager = CatalogPager(catalog, mapOf("genre" to listOf("Comedy"), "skip" to listOf("999")))
        val first = pager.nextRequest()!!
        assertEquals(listOf("genre" to "Comedy"), first.extra)
        pager.accept(items(0, 100))
        assertTrue(pager.isEnd)
        assertNull(pager.nextRequest())
    }

    @Test
    fun selectionsAreKeptOnEveryPage() {
        val pager = CatalogPager(pagedCatalog, mapOf("genre" to listOf("Comedy"), "search" to listOf("his")))
        assertEquals(listOf("genre" to "Comedy", "search" to "his"), pager.nextRequest()!!.extra)
        pager.accept(items(0, 100))
        assertEquals(listOf("genre" to "Comedy", "search" to "his", "skip" to "100"), pager.nextRequest()!!.extra)
    }

    @Test
    fun emptyFirstPageAndMissingRequiredExtraEnd() {
        val pager = CatalogPager(pagedCatalog)
        assertEquals(emptyList(), pager.accept(emptyList()))
        assertTrue(pager.isEnd)

        val required = ManifestCatalog("movie", "year", extras = listOf(CatalogExtra("genre", isRequired = true, options = listOf("2026"))))
        val p2 = CatalogPager(required)
        assertNull(p2.nextRequest())
        assertTrue(p2.isEnd)
    }

    @Test
    fun invalidItemsAreDroppedByTheClientBeforeThePager() = runBlocking {
        // Production path: StremioClient.catalog drops items without an id, so skip counts valid items.
        val http = FakeTransport { FakeTransport.ok(StremioFixtures.read("messy-catalog.json")) }
        val client = StremioClient(http, "ua")
        val t = StremioFixtures.transport("https://a.example.org/manifest.json")
        val pager = CatalogPager(pagedCatalog)
        val page = client.catalog(t, pager.nextRequest()!!).valueOr(emptyList())
        assertEquals(4, pager.accept(page).size)
        assertEquals("4", pager.nextRequest()!!.extra.single { it.first == "skip" }.second)
    }

    @Test
    fun failedProbeAfterAShortPageEndsPagingSilently() {
        val pager = CatalogPager(pagedCatalog)
        pager.accept(items(0, 37))
        assertEquals("37", pager.nextRequest()!!.extra.single { it.first == "skip" }.second)
        // Static host: skip=37.json does not exist → 404 HTML.
        assertEquals(CatalogPager.PageFailure.END_OF_CATALOG, pager.onFailure(AddonError(AddonError.Kind.HTTP, 404)))
        assertTrue(pager.isEnd)
        assertNull(pager.nextRequest())
        assertEquals(37, pager.itemCount)

        val htmlOk = CatalogPager(pagedCatalog)
        htmlOk.accept(items(0, 50))
        assertEquals(CatalogPager.PageFailure.END_OF_CATALOG, htmlOk.onFailure(AddonError(AddonError.Kind.INVALID_JSON)))

        // SDK addon whose handler rejects on "no results": 500 {"err":"handler error"}.
        val sdk = CatalogPager(pagedCatalog)
        sdk.accept(items(0, 20))
        assertEquals(CatalogPager.PageFailure.END_OF_CATALOG, sdk.onFailure(AddonError(AddonError.Kind.HTTP, 500)))
        assertTrue(sdk.isEnd)

        // A 500 on the first page is still a real failure.
        val firstPage500 = CatalogPager(pagedCatalog)
        assertEquals(CatalogPager.PageFailure.FAILED, firstPage500.onFailure(AddonError(AddonError.Kind.HTTP, 500)))
    }

    @Test
    fun otherFailuresKeepPagingOpenForRetry() {
        val first = CatalogPager(pagedCatalog)
        // First page failing is always a real failure.
        assertEquals(CatalogPager.PageFailure.FAILED, first.onFailure(AddonError(AddonError.Kind.HTTP, 404)))
        assertFalse(first.isEnd)
        assertNull(first.nextRequest()!!.extra.firstOrNull { it.first == "skip" })

        val full = CatalogPager(pagedCatalog)
        full.accept(items(0, 100))
        assertEquals(CatalogPager.PageFailure.FAILED, full.onFailure(AddonError(AddonError.Kind.HTTP, 404)))
        assertFalse(full.isEnd)

        val shortThenTimeout = CatalogPager(pagedCatalog)
        shortThenTimeout.accept(items(0, 20))
        assertEquals(CatalogPager.PageFailure.FAILED, shortThenTimeout.onFailure(AddonError(AddonError.Kind.TIMEOUT)))
        assertEquals(CatalogPager.PageFailure.FAILED, shortThenTimeout.onFailure(AddonError(AddonError.Kind.HTTP, 503)))
        assertEquals(CatalogPager.PageFailure.FAILED, shortThenTimeout.onFailure(AddonError(AddonError.Kind.HTTP, 502)))
        assertEquals(CatalogPager.PageFailure.FAILED, shortThenTimeout.onFailure(AddonError(AddonError.Kind.NETWORK)))
        assertFalse(shortThenTimeout.isEnd)
        // Retry gets the same request.
        assertEquals("20", shortThenTimeout.nextRequest()!!.extra.single { it.first == "skip" }.second)
    }
}
