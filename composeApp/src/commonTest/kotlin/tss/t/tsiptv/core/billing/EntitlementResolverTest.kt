package tss.t.tsiptv.core.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/prd-subscriptions.md §2.1: server, Play and cache precedence. */
class EntitlementResolverTest {
    private val catalog = ProductCatalog()
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun purchase(product: String, state: OwnedPurchaseState = OwnedPurchaseState.PURCHASED, ack: Boolean = true, renew: Boolean = true) =
        OwnedPurchase(product, "token-$product", state, ack, renew)

    private fun server(plan: Plan, active: Boolean = true, expires: Long? = now + day, status: SubscriptionStatus = SubscriptionStatus.ACTIVE) =
        ServerEntitlement(plan, active, status, catalog.productOf(plan), "monthly", expires, true)

    private fun resolve(server: ServerState, play: PlayPurchases, cache: CachedEntitlement? = null) =
        EntitlementResolver.resolve(server, play, cache, catalog, now)

    @Test
    fun nothingKnownAndNoCacheIsFree() {
        assertEquals(Entitlement.FREE, resolve(ServerState.Unknown, PlayPurchases.Unknown))
        assertEquals(Entitlement.FREE, resolve(ServerState.SignedOut, PlayPurchases.Loaded(emptyList())))
    }

    @Test
    fun playPurchaseCountsOnlyWhenPurchasedAndAcknowledged() {
        val noAds = resolve(ServerState.SignedOut, PlayPurchases.Loaded(listOf(purchase(catalog.noAdsId))))
        assertEquals(Plan.NO_ADS, noAds.plan)
        assertEquals(EntitlementSource.PLAY, noAds.source)
        assertTrue(noAds.noAds)
        assertFalse(noAds.unlimited)
        assertFalse(noAds.verified)

        val pending = resolve(ServerState.SignedOut, PlayPurchases.Loaded(listOf(purchase(catalog.unlimitedId, OwnedPurchaseState.PENDING))))
        assertEquals(Plan.FREE, pending.plan)
        val unacknowledged = resolve(ServerState.SignedOut, PlayPurchases.Loaded(listOf(purchase(catalog.unlimitedId, ack = false))))
        assertEquals(Plan.FREE, unacknowledged.plan)
        val unknownProduct = resolve(ServerState.SignedOut, PlayPurchases.Loaded(listOf(purchase("other"))))
        assertEquals(Plan.FREE, unknownProduct.plan)
    }

    @Test
    fun highestPlanWinsAndCanceledStillCountsUntilPlayDropsIt() {
        val both = resolve(
            ServerState.SignedOut,
            PlayPurchases.Loaded(listOf(purchase(catalog.noAdsId), purchase(catalog.unlimitedId, renew = false))),
        )
        assertEquals(Plan.UNLIMITED, both.plan)
        assertEquals(SubscriptionStatus.CANCELED, both.status)
        assertEquals(false, both.autoRenewing)
    }

    @Test
    fun serverEntitlementIsVerifiedAndWinsTies() {
        val e = resolve(ServerState.Loaded(server(Plan.UNLIMITED)), PlayPurchases.Loaded(listOf(purchase(catalog.unlimitedId))))
        assertEquals(EntitlementSource.SERVER, e.source)
        assertTrue(e.verified)
        assertEquals(now + day, e.expiresAtMs)
    }

    @Test
    fun theHigherOfServerAndPlayWins() {
        // Server says No ads (another Play account), this device's Play account has Unlimited.
        val e = resolve(ServerState.Loaded(server(Plan.NO_ADS)), PlayPurchases.Loaded(listOf(purchase(catalog.unlimitedId))))
        assertEquals(Plan.UNLIMITED, e.plan)
        assertEquals(EntitlementSource.PLAY, e.source)
        // Server Unlimited from another device, nothing on this Play account.
        val other = resolve(ServerState.Loaded(server(Plan.UNLIMITED)), PlayPurchases.Loaded(emptyList()))
        assertEquals(Plan.UNLIMITED, other.plan)
        assertTrue(other.verified)
    }

    @Test
    fun expiredOrInactiveServerDocumentsGrantNothing() {
        assertNull(EntitlementResolver.fromServer(server(Plan.UNLIMITED, expires = now - 1), now))
        assertNull(EntitlementResolver.fromServer(server(Plan.UNLIMITED, active = false), now))
        assertNull(EntitlementResolver.fromServer(server(Plan.UNLIMITED, expires = null), now))
        assertNull(EntitlementResolver.fromServer(server(Plan.FREE), now))
        // Grace period: still active with a later expiry.
        val grace = EntitlementResolver.fromServer(server(Plan.NO_ADS, status = SubscriptionStatus.IN_GRACE_PERIOD), now)
        assertEquals(SubscriptionStatus.IN_GRACE_PERIOD, grace?.status)
    }

    @Test
    fun cacheBridgesTheStartUpOnly() {
        val cache = CachedEntitlement(Plan.NO_ADS, catalog.noAdsId, now - day)
        // Nothing answered yet: the cached plan, so no ad flashes for a subscriber.
        val early = resolve(ServerState.Unknown, PlayPurchases.Unknown, cache)
        assertEquals(Plan.NO_ADS, early.plan)
        assertEquals(EntitlementSource.CACHE, early.source)
        assertFalse(early.verified)
        // Play answered "nothing" but a signed-in account's server document is not read yet: keep it.
        assertEquals(Plan.NO_ADS, resolve(ServerState.Unknown, PlayPurchases.Loaded(emptyList()), cache).plan)
        // Play failed (offline): keep it.
        assertEquals(Plan.NO_ADS, resolve(ServerState.SignedOut, PlayPurchases.Failed, cache).plan)
        // Both answered: the cache no longer counts.
        assertEquals(Plan.FREE, resolve(ServerState.SignedOut, PlayPurchases.Loaded(emptyList()), cache).plan)
    }

    /** QC BB1: Play briefly unable to bill (code 3 → Failed) or even missing must not show a subscriber ads. */
    @Test
    fun unavailableStoreKeepsAValidCache() {
        val cache = CachedEntitlement(Plan.NO_ADS, catalog.noAdsId, now - day)
        for (play in listOf(PlayPurchases.Failed, PlayPurchases.Unavailable)) {
            assertEquals(Plan.NO_ADS, resolve(ServerState.SignedOut, play, cache).plan)
            assertEquals(Plan.NO_ADS, resolve(ServerState.Loaded(null), play, cache).plan)
            assertFalse(EntitlementResolver.settled(ServerState.Loaded(null), play, cache, now))
        }
        // Without a (valid) cache an unavailable store settles to Free.
        assertTrue(EntitlementResolver.settled(ServerState.Loaded(null), PlayPurchases.Unavailable, null, now))
        assertEquals(Plan.FREE, resolve(ServerState.Loaded(null), PlayPurchases.Unavailable, null).plan)
        val old = CachedEntitlement(Plan.NO_ADS, catalog.noAdsId, now - EntitlementResolver.CACHE_MAX_AGE_MS - 1)
        assertEquals(Plan.FREE, resolve(ServerState.Loaded(null), PlayPurchases.Unavailable, old).plan)
    }

    @Test
    fun cacheOlderThan72HoursIsIgnored() {
        val old = CachedEntitlement(Plan.UNLIMITED, catalog.unlimitedId, now - EntitlementResolver.CACHE_MAX_AGE_MS - 1)
        assertEquals(Plan.FREE, resolve(ServerState.Unknown, PlayPurchases.Failed, old).plan)
        val future = CachedEntitlement(Plan.UNLIMITED, catalog.unlimitedId, now + day)
        assertEquals(Plan.FREE, resolve(ServerState.Unknown, PlayPurchases.Failed, future).plan)
    }

    @Test
    fun whatIsCached() {
        val play = resolve(ServerState.SignedOut, PlayPurchases.Loaded(listOf(purchase(catalog.noAdsId))))
        val store = EntitlementResolver.cacheFor(play, settled = true, nowMs = now)
        assertEquals(EntitlementResolver.CacheUpdate.Store(CachedEntitlement(Plan.NO_ADS, catalog.noAdsId, now)), store)
        assertEquals(EntitlementResolver.CacheUpdate.Clear, EntitlementResolver.cacheFor(Entitlement.FREE, settled = true, nowMs = now))
        assertEquals(EntitlementResolver.CacheUpdate.Keep, EntitlementResolver.cacheFor(Entitlement.FREE, settled = false, nowMs = now))
    }

    @Test
    fun serverStateNames() {
        assertEquals(SubscriptionStatus.IN_GRACE_PERIOD, SubscriptionStatus.fromWire("SUBSCRIPTION_STATE_IN_GRACE_PERIOD"))
        assertEquals(SubscriptionStatus.ON_HOLD, SubscriptionStatus.fromWire("ON_HOLD"))
        assertEquals(SubscriptionStatus.EXPIRED, SubscriptionStatus.fromWire("REVOKED"))
        assertEquals(SubscriptionStatus.NONE, SubscriptionStatus.fromWire(null))
        assertEquals(Plan.UNLIMITED, Plan.fromWire("unlimited"))
        assertEquals(Plan.FREE, Plan.fromWire("gold"))
    }
}
