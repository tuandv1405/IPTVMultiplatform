package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Why one addon gave no result. [code] is short and URL-free, suitable for `stremio_addons.lastError`
 * and release logs (together with the host only).
 */
data class AddonError(val kind: Kind, val status: Int? = null, val rootKey: String? = null) {
    enum class Kind { HTTP, TIMEOUT, NETWORK, INVALID_JSON, MISSING_ROOT_KEY, TOO_LARGE }

    val code: String
        get() = when (kind) {
            Kind.HTTP -> "http_${status ?: 0}"
            Kind.TIMEOUT -> "timeout"
            Kind.NETWORK -> "network"
            Kind.INVALID_JSON -> "invalid_json"
            Kind.MISSING_ROOT_KEY -> "missing_${rootKey ?: "root"}"
            Kind.TOO_LARGE -> "too_large"
        }

    /** A 4xx answer: the addon has nothing at this URL (no stale-if-error for it, RFC 5861). */
    val isClientError: Boolean get() = kind == Kind.HTTP && status != null && status in 400..499

    /** 404 on meta/stream usually just means "not here" (research §3.2, §3.7). */
    val isNotFound: Boolean get() = kind == Kind.HTTP && status == 404
}

enum class ResultSource { NETWORK, CACHE, STALE_REVALIDATING, STALE_ON_ERROR }

/**
 * One addon's answer. A [Failure] is **an empty result from that addon**, never a screen failure
 * (PRD §8): use [valueOr] and count failures for `addon_partial_failure`.
 */
sealed interface AddonResult<out T> {
    data class Success<T>(val value: T, val source: ResultSource = ResultSource.NETWORK) : AddonResult<T>
    data class Failure(val error: AddonError) : AddonResult<Nothing>

    val valueOrNull: T? get() = (this as? Success<T>)?.value
    val isFailure: Boolean get() = this is Failure
}

fun <T> AddonResult<T>.valueOr(fallback: T): T = (this as? AddonResult.Success<T>)?.value ?: fallback

sealed interface ManifestFetchResult {
    data class Success(val manifest: StremioManifest, val rawJson: String) : ManifestFetchResult
    /** `addon_error_invalid_manifest`; [field] is the missing field (null: not JSON / not an object). */
    data class Invalid(val field: String?) : ManifestFetchResult
    /** `addon_error_unreachable`. */
    data class Unreachable(val error: AddonError) : ManifestFetchResult
}

/**
 * Stremio addon protocol client (PRD §8). Stateless apart from the in-memory response cache and
 * the concurrency limiter; installed addons, ordering and fan-out live in the repository (step 3).
 *
 * Never logs: URLs may contain secrets. Errors carry codes only.
 */
@OptIn(ExperimentalTime::class)
class StremioClient(
    private val transport: StremioHttpTransport,
    /** e.g. `TSIPTV/1.0.1 (Stremio-addon-client)`; see [userAgentFor]. */
    private val userAgent: String,
    private val cache: StremioResponseCache = StremioResponseCache(),
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Scope for stale-while-revalidate refreshes; null disables serving stale data while refreshing. */
    private val refreshScope: CoroutineScope? = null,
    maxConcurrentRequests: Int = 16,
    private val maxRequestsPerHost: Int = 6,
    /** Total time budget per request (PRD §8); a backstop over the engine's own timeouts. */
    private val totalTimeoutMs: (StremioRequestKind) -> Long = { it.totalTimeoutMs },
) {
    private val globalPermits = Semaphore(maxConcurrentRequests)
    private val hostPermitsLock = Mutex()
    private val hostPermits = HashMap<String, Semaphore>()
    private val refreshing = HashSet<String>()
    private val refreshingLock = Mutex()

    private val requestHeaders: Map<String, String>
        get() = mapOf("Accept" to "application/json", "User-Agent" to userAgent)

    // -----------------------------------------------------------------------------------------
    // Resources
    // -----------------------------------------------------------------------------------------

    /** Fetches and validates a manifest. Never cached here (persisted by the repository). */
    suspend fun fetchManifest(transport: StremioTransport): ManifestFetchResult {
        val response = when (val r = request(transport.manifestUrl, transport.displayHost, StremioRequestKind.MANIFEST)) {
            is RawResult.Error -> return ManifestFetchResult.Unreachable(r.error)
            is RawResult.Ok -> r.response
        }
        if (response.status !in 200..299) {
            return ManifestFetchResult.Unreachable(AddonError(AddonError.Kind.HTTP, response.status))
        }
        return when (val parsed = StremioManifestParser.parse(response.body)) {
            is ManifestParseResult.Valid -> ManifestFetchResult.Success(parsed.manifest, response.body)
            is ManifestParseResult.Invalid -> ManifestFetchResult.Invalid(parsed.field)
        }
    }

    /** `{ "metas": [...] }` (EPG addons: `metasDetailed`). Items without an id are dropped. */
    suspend fun catalog(transport: StremioTransport, request: CatalogRequest): AddonResult<List<StremioMeta>> =
        fetchResource(request.url(transport), transport.displayHost, StremioRequestKind.CATALOG) { root ->
            val list = root["metas"] ?: root["metasDetailed"]
            // Ids are list keys in the UI: an addon repeating an item in one page must not crash it.
            list.decodeEach { StremioJson.decodeFromJsonElement(StremioMeta.serializer(), it).takeIf { m -> m.isValid } }
                .distinctBy { it.id }
        }

    /** `{ "meta": {...} }`; `null` or `{}` → Success(null) ("not found here", try the next addon). */
    suspend fun meta(transport: StremioTransport, type: String, id: String): AddonResult<StremioMeta?> =
        fetchResource(transport.resourceUrl(StremioResource.META, type, id), transport.displayHost, StremioRequestKind.META) { root ->
            val element = root["meta"]
            if (element !is JsonObject) null
            else runCatching { StremioJson.decodeFromJsonElement(StremioMeta.serializer(), element) }
                .getOrNull()?.takeIf { it.isValid }
        }

    /** `{ "streams": [...] }`. Classify with [StreamClassifier]. */
    suspend fun streams(transport: StremioTransport, type: String, videoId: String): AddonResult<List<StremioStream>> =
        fetchResource(transport.resourceUrl(StremioResource.STREAM, type, videoId), transport.displayHost, StremioRequestKind.STREAM) { root ->
            root["streams"].decodeEach { StremioJson.decodeFromJsonElement(StremioStream.serializer(), it) }
        }

    /**
     * `{ "subtitles": [...] }` (phase F2b). [extra] are `videoHash`/`videoSize`/`filename` when known.
     * Subtitles without a URL, or pointing at Stremio's local server, are dropped.
     */
    suspend fun subtitles(
        transport: StremioTransport,
        type: String,
        videoId: String,
        extra: List<Pair<String, String>> = emptyList(),
    ): AddonResult<List<StremioSubtitle>> =
        fetchResource(transport.resourceUrl(StremioResource.SUBTITLES, type, videoId, extra), transport.displayHost, StremioRequestKind.SUBTITLES) { root ->
            root["subtitles"].decodeEach { StremioJson.decodeFromJsonElement(StremioSubtitle.serializer(), it) }
                .filter { s -> s.url?.let { isUsableSubtitleUrl(it) } == true }
        }

    /** `{ "addons": [...] }` — format only; only for addon lists the user added themselves. */
    suspend fun addonCatalog(transport: StremioTransport, type: String, id: String): AddonResult<List<AddonDescriptor>> =
        fetchResource(transport.resourceUrl(StremioResource.ADDON_CATALOG, type, id), transport.displayHost, StremioRequestKind.ADDON_CATALOG) { root ->
            root["addons"].decodeEach { StremioJson.decodeFromJsonElement(AddonDescriptor.serializer(), it) }
        }

    /** Drops every cached response of this addon (after removal, reconfiguration or "Retry"). */
    suspend fun invalidate(transport: StremioTransport) {
        cache.removeByPrefix("${transport.scheme}://${transport.authority}${transport.basePath}/")
    }

    // -----------------------------------------------------------------------------------------
    // Plumbing
    // -----------------------------------------------------------------------------------------

    private sealed interface RawResult {
        data class Ok(val response: StremioHttpResponse) : RawResult
        data class Error(val error: AddonError) : RawResult
    }

    private suspend fun request(url: String, host: String, kind: StremioRequestKind): RawResult = try {
        val response = withLimits(host) {
            withTimeout(totalTimeoutMs(kind)) { transport.get(url, requestHeaders, kind) }
        }
        RawResult.Ok(response)
    } catch (_: TimeoutCancellationException) {
        RawResult.Error(AddonError(AddonError.Kind.TIMEOUT))
    } catch (e: CancellationException) {
        throw e
    } catch (_: StremioBodyTooLargeException) {
        RawResult.Error(AddonError(AddonError.Kind.TOO_LARGE))
    } catch (e: Exception) {
        val isTimeout = e::class.simpleName?.contains("Timeout", ignoreCase = true) == true
        RawResult.Error(AddonError(if (isTimeout) AddonError.Kind.TIMEOUT else AddonError.Kind.NETWORK))
    }

    private suspend fun <T> withLimits(host: String, block: suspend () -> T): T {
        val hostSemaphore = hostPermitsLock.withLock { hostPermits.getOrPut(host) { Semaphore(maxRequestsPerHost) } }
        // Host permit first: requests queued behind one slow host must not hold global permits and
        // starve other addons. Always this order (host, then global), so no lock-order inversion.
        return hostSemaphore.withPermit { globalPermits.withPermit { block() } }
    }

    /** Parses a body into its root object, checking the root key; null body → error. */
    private fun parseRoot(body: String, kind: StremioRequestKind): Pair<JsonObject?, AddonError?> {
        val element: JsonElement = try {
            StremioJson.parseToJsonElement(body.withoutBom())
        } catch (_: Exception) {
            return null to AddonError(AddonError.Kind.INVALID_JSON)
        }
        val obj = element as? JsonObject ?: return null to AddonError(AddonError.Kind.INVALID_JSON)
        val key = kind.rootKey
        val hasRoot = key == null || obj.containsKey(key) ||
            (kind == StremioRequestKind.CATALOG && obj.containsKey("metasDetailed"))
        if (!hasRoot) return null to AddonError(AddonError.Kind.MISSING_ROOT_KEY, rootKey = key)
        // `{"meta": null}` is a valid "not found"; other roots must not be null.
        if (key != null && key != "meta" && obj[key] is JsonNull) {
            return null to AddonError(AddonError.Kind.MISSING_ROOT_KEY, rootKey = key)
        }
        return obj to null
    }

    private suspend fun <T> fetchResource(
        url: String,
        host: String,
        kind: StremioRequestKind,
        decode: (JsonObject) -> T,
    ): AddonResult<T> {
        val now = nowMs()
        val cached = cache.get(url)
        if (cached != null) {
            if (cached.isFresh(now)) {
                parseRoot(cached.body, kind).first?.let { return AddonResult.Success(decode(it), ResultSource.CACHE) }
            } else if (cached.canRevalidateInBackground(now) && refreshScope != null) {
                parseRoot(cached.body, kind).first?.let { root ->
                    scheduleRefresh(url, host, kind)
                    return AddonResult.Success(decode(root), ResultSource.STALE_REVALIDATING)
                }
            }
        }
        return when (val fetched = fetchAndStore(url, host, kind)) {
            is FetchOutcome.Ok -> AddonResult.Success(decode(fetched.root), ResultSource.NETWORK)
            is FetchOutcome.Failed -> {
                val stale = cached?.takeIf { !fetched.error.isClientError && it.canServeOnError(nowMs()) }?.let { parseRoot(it.body, kind).first }
                if (stale != null) AddonResult.Success(decode(stale), ResultSource.STALE_ON_ERROR)
                else AddonResult.Failure(fetched.error)
            }
        }
    }

    private sealed interface FetchOutcome {
        data class Ok(val root: JsonObject) : FetchOutcome
        data class Failed(val error: AddonError) : FetchOutcome
    }

    private suspend fun fetchAndStore(url: String, host: String, kind: StremioRequestKind): FetchOutcome {
        val response = when (val r = request(url, host, kind)) {
            is RawResult.Error -> return FetchOutcome.Failed(r.error)
            is RawResult.Ok -> r.response
        }
        if (response.status !in 200..299) return FetchOutcome.Failed(AddonError(AddonError.Kind.HTTP, response.status))
        val (root, error) = parseRoot(response.body, kind)
        if (root == null) return FetchOutcome.Failed(error ?: AddonError(AddonError.Kind.INVALID_JSON))
        val policy = CachePolicy.resolve(response.header("cache-control"), root, kind)
        val worthStoring = policy.maxAgeSec > 0 || policy.staleWhileRevalidateSec > 0 || policy.staleIfErrorSec > 0
        if (!policy.noStore && worthStoring) cache.put(url, CacheEntry.of(response.body, nowMs(), policy))
        return FetchOutcome.Ok(root)
    }

    private suspend fun scheduleRefresh(url: String, host: String, kind: StremioRequestKind) {
        val scope = refreshScope ?: return
        val start = refreshingLock.withLock { refreshing.add(url) }
        if (!start) return
        scope.launch {
            try {
                fetchAndStore(url, host, kind)
            } finally {
                refreshingLock.withLock { refreshing.remove(url) }
            }
        }
    }

    companion object {
        /** `TSIPTV/{version} (Stremio-addon-client)` (PRD §1). */
        fun userAgentFor(appVersion: String): String = "TSIPTV/$appVersion (Stremio-addon-client)"

        /** Drops Stremio local-server URLs (`127.0.0.1:11470`, research §3.6) and non-http(s) URLs. */
        fun isUsableSubtitleUrl(url: String): Boolean {
            val parts = UrlParts.split(url.trim()) ?: return false
            if (parts.scheme != "http" && parts.scheme != "https") return false
            return !(parts.port == 11470 && (parts.host == "127.0.0.1" || parts.host == "localhost" || parts.host == "[::1]"))
        }
    }
}
