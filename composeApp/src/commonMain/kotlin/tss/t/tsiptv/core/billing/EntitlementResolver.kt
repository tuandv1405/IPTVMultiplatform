package tss.t.tsiptv.core.billing

/** The server entitlement document for the signed-in account, as far as it is known. */
sealed interface ServerState {
    /** Not signed in: there is no account document to read. */
    data object SignedOut : ServerState

    /** Signed in, document not read yet (or the read failed). */
    data object Unknown : ServerState

    /** Read; [doc] is null when the server never wrote one (no purchase, or server not deployed). */
    data class Loaded(val doc: ServerEntitlement?) : ServerState
}

/**
 * Pure rules of docs/prd-subscriptions.md §2.1: the highest plan among the server document and this
 * device's Play purchases; the cached plan only until both have answered (so a subscriber's app open
 * ad and banners never flash at start-up) and for at most [CACHE_MAX_AGE_MS].
 */
object EntitlementResolver {
    const val CACHE_MAX_AGE_MS = 72L * 60 * 60 * 1000

    /** The server document, when it grants something now. */
    fun fromServer(doc: ServerEntitlement?, nowMs: Long): Entitlement? {
        if (doc == null || !doc.active || doc.plan == Plan.FREE) return null
        val expires = doc.expiresAtMs ?: return null
        if (expires <= nowMs) return null
        return Entitlement(
            plan = doc.plan,
            source = EntitlementSource.SERVER,
            status = doc.status.takeIf { it != SubscriptionStatus.NONE } ?: SubscriptionStatus.ACTIVE,
            productId = doc.productId,
            basePlanId = doc.basePlanId,
            expiresAtMs = expires,
            autoRenewing = doc.autoRenewing,
        )
    }

    /**
     * The highest plan among purchases that are PURCHASED **and** acknowledged. Pending purchases
     * never entitle; an unacknowledged one is acknowledged by the client first, then counts.
     */
    fun fromPlay(purchases: List<OwnedPurchase>, catalog: ProductCatalog): Entitlement? =
        purchases
            .filter { it.state == OwnedPurchaseState.PURCHASED && it.acknowledged }
            .map { it to catalog.planOf(it.productId) }
            .filter { it.second != Plan.FREE }
            .maxByOrNull { it.second.rank }
            ?.let { (purchase, plan) ->
                Entitlement(
                    plan = plan,
                    source = EntitlementSource.PLAY,
                    status = if (purchase.autoRenewing) SubscriptionStatus.ACTIVE else SubscriptionStatus.CANCELED,
                    productId = purchase.productId,
                    autoRenewing = purchase.autoRenewing,
                )
            }

    fun fromCache(cache: CachedEntitlement?, nowMs: Long): Entitlement? {
        if (cache == null || cache.plan == Plan.FREE) return null
        val age = nowMs - cache.confirmedAtMs
        if (age < 0 || age > CACHE_MAX_AGE_MS) return null
        return Entitlement(plan = cache.plan, source = EntitlementSource.CACHE, status = SubscriptionStatus.ACTIVE, productId = cache.productId)
    }

    /** Both sources have answered: the cache is no longer needed. */
    fun settled(server: ServerState, play: PlayPurchases): Boolean =
        server !is ServerState.Unknown && (play is PlayPurchases.Loaded || play is PlayPurchases.Unavailable)

    fun resolve(
        server: ServerState,
        play: PlayPurchases,
        cache: CachedEntitlement?,
        catalog: ProductCatalog,
        nowMs: Long,
    ): Entitlement {
        val fromServer = (server as? ServerState.Loaded)?.let { fromServer(it.doc, nowMs) }
        val fromPlay = (play as? PlayPurchases.Loaded)?.let { fromPlay(it.purchases, catalog) }
        val fresh = best(fromServer, fromPlay)
        if (settled(server, play)) return fresh ?: Entitlement.FREE
        return best(fresh, fromCache(cache, nowMs)) ?: Entitlement.FREE
    }

    /** The higher plan; on a tie the earlier argument wins (server before Play before cache). */
    private fun best(a: Entitlement?, b: Entitlement?): Entitlement? = when {
        a == null -> b
        b == null -> a
        b.plan.rank > a.plan.rank -> b
        else -> a
    }

    /** What to keep for the next start: the confirmed plan, or null to forget the cache. */
    fun cacheFor(resolved: Entitlement, settled: Boolean, nowMs: Long): CacheUpdate = when {
        resolved.source == EntitlementSource.SERVER || resolved.source == EntitlementSource.PLAY ->
            CacheUpdate.Store(CachedEntitlement(resolved.plan, resolved.productId, nowMs))
        settled && resolved.plan == Plan.FREE -> CacheUpdate.Clear
        else -> CacheUpdate.Keep
    }

    sealed interface CacheUpdate {
        data class Store(val value: CachedEntitlement) : CacheUpdate
        data object Clear : CacheUpdate
        data object Keep : CacheUpdate
    }
}
