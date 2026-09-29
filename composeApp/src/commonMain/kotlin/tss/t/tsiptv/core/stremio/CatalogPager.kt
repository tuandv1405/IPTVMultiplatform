package tss.t.tsiptv.core.stremio

/**
 * Catalog paging (PRD §5, research §3.1), without any I/O:
 *
 * - The first request has no `skip`; each next one uses `skip = number of items received so far`.
 * - `skip` is only sent when the catalog declares it; otherwise there is exactly one page.
 * - Paging ends on an empty page or a page with no new ids. A short page (< 100 items) does **not**
 *   end paging by itself (the lenient rule, for addons that page by 20): the next request decides,
 *   and ends paging when it brings no new ids. So a 250-item catalogue is requested with no skip,
 *   `skip=100`, `skip=200`, then `skip=250`, which comes back empty.
 * - Items are de-duplicated by id across pages, so no card is shown twice.
 *
 * - A failed request is reported with [onFailure], never with `accept(emptyList())`.
 * - Items should come from [StremioClient.catalog], which already drops items without an id, so
 *   `skip` counts the valid items received.
 *
 * Not thread-safe: drive it from one coroutine (the screen's paging job).
 */
class CatalogPager(
    val catalog: ManifestCatalog,
    /** Genre/search/etc. selections; `skip` is managed here and ignored if present. */
    val selections: Map<String, List<String>> = emptyMap(),
) {
    private val seenIds = LinkedHashSet<String>()
    private var received = 0
    private var requests = 0

    /** No more requests should be made. */
    var isEnd: Boolean = false
        private set

    /** Whether the last accepted page was shorter than [PAGE_SIZE]; the next request is a probe. */
    var lastPageWasShort: Boolean = false
        private set

    val itemCount: Int get() = seenIds.size

    /**
     * The request for the next page, or null when paging has ended (or a required extra is
     * missing from [selections]).
     */
    fun nextRequest(): CatalogRequest? {
        if (isEnd) return null
        val skip = if (requests == 0) null else received
        val extra = ResourceMatcher.buildExtra(catalog, selections.filterKeys { it != StremioExtra.SKIP }, skip)
            ?: return null.also { isEnd = true }
        return CatalogRequest(catalog.type, catalog.id, extra)
    }

    /**
     * Records the page answering [nextRequest] and returns the items not shown before (invalid
     * items without an id are dropped). A failed request should not be passed here: the caller
     * may retry the same [nextRequest].
     */
    fun accept(page: List<StremioMeta>): List<StremioMeta> {
        if (isEnd) return emptyList()
        requests++
        received += page.size
        val fresh = page.filter { it.isValid && seenIds.add(it.id!!) }
        lastPageWasShort = page.size < PAGE_SIZE
        if (page.isEmpty() || fresh.isEmpty() || !catalog.supportsSkip) isEnd = true
        return fresh
    }

    /**
     * Records that the request from [nextRequest] failed. After a short page, the extra "probe" request
     * of the lenient rule often fails on static hosts (404 HTML for `skip=250.json`) or SDK addons
     * (500 "handler error" when a handler rejects on no results); that is the end
     * of the catalogue, not an addon failure: paging ends and [PageFailure.END_OF_CATALOG] is returned,
     * which the UI must **not** show as Retry or count in `addon_partial_failure`. Anything else is
     * [PageFailure.FAILED]: paging stays open and the same [nextRequest] can be retried.
     */
    fun onFailure(error: AddonError): PageFailure {
        if (isEnd) return PageFailure.END_OF_CATALOG
        val probeAfterShortPage = requests > 0 && lastPageWasShort
        // 500 too: SDK addons whose handler rejects on "no results" answer {"err":"handler error"}.
        // Timeouts, network errors and other 5xx (502/503/…) stay FAILED.
        val definiteAnswer = error.isClientError ||
            (error.kind == AddonError.Kind.HTTP && error.status == 500) ||
            error.kind == AddonError.Kind.INVALID_JSON || error.kind == AddonError.Kind.MISSING_ROOT_KEY
        if (probeAfterShortPage && definiteAnswer) {
            isEnd = true
            return PageFailure.END_OF_CATALOG
        }
        return PageFailure.FAILED
    }

    enum class PageFailure { END_OF_CATALOG, FAILED }

    companion object {
        /** Stremio's page size (core `CATALOG_PAGE_SIZE`). */
        const val PAGE_SIZE = 100
    }
}
