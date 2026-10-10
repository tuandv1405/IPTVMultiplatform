package tss.t.tsiptv.core.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.tsiptv.core.storage.KeyValueStorage

/**
 * The user's subscription, for every gate (docs/prd-subscriptions.md §2): the server document of the
 * signed-in account, this device's store purchases and a short-lived local cache, resolved by
 * [EntitlementResolver]. Also sends confirmed purchases to the billing server ([PurchaseVerifier]) so
 * they follow the account to other devices.
 *
 * [state] is null only until the cache has been read (a few ms after [start]); the ads gate waits for
 * it so a subscriber never sees an ad, and the ad SDK is never started for them.
 */
class EntitlementRepository(
    private val billing: BillingGateway,
    private val server: ServerEntitlementSource,
    private val verifier: PurchaseVerifier,
    /** The signed-in uid, null when signed out. */
    private val uid: Flow<String?>,
    private val storage: KeyValueStorage,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val _state = MutableStateFlow<Entitlement?>(null)
    val state: StateFlow<Entitlement?> = _state.asStateFlow()

    /** The entitlement once known. */
    val entitlement: Flow<Entitlement> = state.filterNotNull()

    /** The current value without waiting (Free until known). */
    val current: Entitlement get() = _state.value ?: Entitlement.FREE

    private val _uid = MutableStateFlow<String?>(null)

    /** The signed-in uid as last seen (null when signed out). */
    val signedInUid: StateFlow<String?> = _uid.asStateFlow()

    private val verified = mutableSetOf<String>()
    private val verifyLock = Mutex()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val cache = readCache()
            // Known at once from the cache (or Free): the ads gate can decide without waiting for Play.
            _state.value = EntitlementResolver.resolve(ServerState.Unknown, PlayPurchases.Unknown, cache, billing.catalog, nowMs())
            var lastCache = cache
            combine(serverState(), billing.purchases) { s, p -> s to p }
                .transformLatest { (s, p) ->
                    // Re-resolve when a server entitlement or the cache runs out, without waiting for an event.
                    while (true) {
                        val now = nowMs()
                        val resolved = EntitlementResolver.resolve(s, p, lastCache, billing.catalog, now)
                        emit(Triple(resolved, EntitlementResolver.settled(s, p), now))
                        val until = nextChange(resolved, lastCache, now) ?: break
                        delay(until)
                    }
                }
                .collect { (resolved, settled, now) ->
                    // The cache is updated first, so what the gates see is never newer than what is kept.
                    when (val update = EntitlementResolver.cacheFor(resolved, settled, now)) {
                        is EntitlementResolver.CacheUpdate.Store -> if (update.value.plan != lastCache?.plan || update.value.productId != lastCache?.productId || now - (lastCache?.confirmedAtMs ?: 0) > CACHE_REFRESH_MS) {
                            lastCache = update.value
                            writeCache(update.value)
                        }
                        EntitlementResolver.CacheUpdate.Clear -> if (lastCache != null) {
                            lastCache = null
                            clearCache()
                        }
                        EntitlementResolver.CacheUpdate.Keep -> Unit
                    }
                    _state.value = resolved
                }
        }
        // Confirmed purchases go to the billing server (when configured) to follow the account.
        scope.launch {
            combine(uid.distinctUntilChanged(), billing.purchases) { u, p -> u to p }.collect { (u, p) ->
                if (u != null && p is PlayPurchases.Loaded) verifyNew(u, p.purchases)
            }
        }
        billing.refresh()
    }

    /** "Restore purchases": re-reads the store and re-sends every confirmed purchase to the server. */
    suspend fun restore(): Boolean {
        verifyLock.withLock { verified.clear() }
        return billing.restore()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun serverState(): Flow<ServerState> =
        uid.distinctUntilChanged().flatMapLatest { u ->
            _uid.value = u
            if (u == null) flowOf(ServerState.SignedOut)
            else flow<ServerState> {
                emitAll(server.observe(u).map { ServerState.Loaded(it) })
            }
                // Offline or no permission: "unknown" (the cache and Play still decide), then try
                // again with a growing delay instead of staying unknown (QC B9).
                .retryWhen { cause, attempt ->
                    if (cause is kotlinx.coroutines.CancellationException) return@retryWhen false
                    emit(ServerState.Unknown)
                    delay(serverRetryDelayMs(attempt))
                    true
                }
                .onStart { emit(ServerState.Unknown) }
        }

    private suspend fun verifyNew(uid: String, purchases: List<OwnedPurchase>) {
        if (!verifier.enabled) return
        for (p in purchases) {
            if (p.state != OwnedPurchaseState.PURCHASED || !p.acknowledged) continue
            val key = "$uid|${p.purchaseToken}"
            val todo = verifyLock.withLock { key !in verified }
            if (!todo) continue
            if (verifier.verify(p.productId, p.purchaseToken)) verifyLock.withLock { verified += key }
        }
    }

    /** Milliseconds until the resolved value can change by itself (an expiry), or null. */
    private fun nextChange(resolved: Entitlement, cache: CachedEntitlement?, now: Long): Long? {
        val candidates = buildList {
            resolved.expiresAtMs?.let { add(it - now + 1) }
            if (resolved.source == EntitlementSource.CACHE && cache != null) {
                add(cache.confirmedAtMs + EntitlementResolver.CACHE_MAX_AGE_MS - now + 1)
            }
        }.filter { it > 0 }
        return candidates.minOrNull()
    }

    /** The cached plan, or null; a storage failure must never block the gates (QC B8). */
    private suspend fun readCache(): CachedEntitlement? = try {
        val plan = Plan.fromWire(storage.getString(KEY_PLAN, ""))
        if (plan == Plan.FREE) null
        else CachedEntitlement(plan, storage.getString(KEY_PRODUCT, "").ifEmpty { null }, storage.getLong(KEY_CONFIRMED_AT, 0L))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    // Storage failures only lose the cache; they never stop the entitlement from updating.
    private suspend fun writeCache(value: CachedEntitlement) = safely {
        storage.putString(KEY_PLAN, value.plan.wireName)
        storage.putString(KEY_PRODUCT, value.productId.orEmpty())
        storage.putLong(KEY_CONFIRMED_AT, value.confirmedAtMs)
    }

    private suspend fun clearCache() = safely {
        storage.remove(KEY_PLAN)
        storage.remove(KEY_PRODUCT)
        storage.remove(KEY_CONFIRMED_AT)
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    companion object {
        /** 2 s, 4 s, 8 s … at most 5 min between attempts to read the server document. */
        fun serverRetryDelayMs(attempt: Long): Long = (2_000L shl attempt.coerceAtMost(8).toInt()).coerceAtMost(5 * 60_000L)

        const val KEY_PLAN = "billing_cached_plan"
        const val KEY_PRODUCT = "billing_cached_product"
        const val KEY_CONFIRMED_AT = "billing_cached_at_ms"

        /** A still-valid cache is re-stamped at most this often (fewer storage writes). */
        private const val CACHE_REFRESH_MS = 60L * 60 * 1000
    }
}
