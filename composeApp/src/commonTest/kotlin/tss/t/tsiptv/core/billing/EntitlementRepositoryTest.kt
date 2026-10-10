package tss.t.tsiptv.core.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntitlementRepositoryTest {
    private val now = 1_800_000_000_000L

    private class FakeBilling : BillingGateway {
        override val isSupported = true
        override val catalog = ProductCatalog()
        override val purchases = MutableStateFlow<PlayPurchases>(PlayPurchases.Unknown)
        override val offers: StateFlow<List<PlanOffer>> = MutableStateFlow(emptyList())
        override val events: Flow<BillingEvent> = emptyFlow()
        var refreshed = 0
        override fun refresh() {
            refreshed++
        }
        override suspend fun restore(): Boolean = true
        override suspend fun launchPurchase(offer: PlanOffer, accountHash: String?, replace: ReplaceFrom?) = PurchaseLaunch.STARTED
    }

    private class FakeServer : ServerEntitlementSource {
        val docs = MutableStateFlow<ServerEntitlement?>(null)
        override fun observe(uid: String): Flow<ServerEntitlement?> = docs
    }

    private class FakeVerifier : PurchaseVerifier {
        override val enabled = true
        val calls = mutableListOf<String>()
        override suspend fun verify(productId: String, purchaseToken: String): Boolean {
            calls += purchaseToken
            return true
        }
    }

    private fun <T> withRepo(
        storage: InMemoryKeyValueStorage = InMemoryKeyValueStorage(),
        uid: MutableStateFlow<String?> = MutableStateFlow(null),
        block: suspend (EntitlementRepository, FakeBilling, FakeServer, FakeVerifier) -> T,
    ): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val billing = FakeBilling()
            val server = FakeServer()
            val verifier = FakeVerifier()
            val repo = EntitlementRepository(billing, server, verifier, uid, storage, scope) { now }
            repo.start()
            withTimeout(5_000) { block(repo, billing, server, verifier) }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun EntitlementRepository.await(predicate: (Entitlement) -> Boolean): Entitlement =
        state.first { it != null && predicate(it) }!!

    @Test
    fun freeAtOnceWithoutACacheThenPlayDecides() = withRepo { repo, billing, _, _ ->
        assertEquals(Plan.FREE, repo.await { true }.plan)
        assertTrue(billing.refreshed > 0)
        billing.purchases.value = PlayPurchases.Loaded(listOf(OwnedPurchase(billing.catalog.noAdsId, "t1", OwnedPurchaseState.PURCHASED, true, true)))
        assertEquals(EntitlementSource.PLAY, repo.await { it.plan == Plan.NO_ADS }.source)
    }

    @Test
    fun cachedPlanIsKnownBeforePlayAnswersAndIsClearedWhenAllAnswered() {
        val storage = InMemoryKeyValueStorage()
        runBlocking {
            storage.putString(EntitlementRepository.KEY_PLAN, "no_ads")
            storage.putLong(EntitlementRepository.KEY_CONFIRMED_AT, now - 1000)
        }
        withRepo(storage) { repo, billing, _, _ ->
            val first = repo.await { true }
            assertEquals(Plan.NO_ADS, first.plan)
            assertEquals(EntitlementSource.CACHE, first.source)
            billing.purchases.value = PlayPurchases.Loaded(emptyList())
            repo.await { it.plan == Plan.FREE }
        }
        assertEquals("", runBlocking { storage.getString(EntitlementRepository.KEY_PLAN, "") })
    }

    @Test
    fun serverEntitlementFollowsTheSignedInAccount() {
        val uid = MutableStateFlow<String?>("u1")
        withRepo(uid = uid) { repo, billing, server, _ ->
            billing.purchases.value = PlayPurchases.Loaded(emptyList())
            server.docs.value = ServerEntitlement(Plan.UNLIMITED, true, SubscriptionStatus.ACTIVE, "tsiptv_unlimited", "yearly", now + 1_000_000, true)
            val e = repo.await { it.plan == Plan.UNLIMITED }
            assertTrue(e.verified)
            uid.value = null
            repo.await { it.plan == Plan.FREE }
        }
    }

    @Test
    fun confirmedPurchasesAreSentToTheServerOncePerAccount() {
        val uid = MutableStateFlow<String?>("u1")
        withRepo(uid = uid) { repo, billing, _, verifier ->
            val bought = OwnedPurchase(billing.catalog.unlimitedId, "tok", OwnedPurchaseState.PURCHASED, true, true)
            val pending = OwnedPurchase(billing.catalog.noAdsId, "pend", OwnedPurchaseState.PENDING, false, true)
            billing.purchases.value = PlayPurchases.Loaded(listOf(bought, pending))
            repo.await { it.plan == Plan.UNLIMITED }
            while (verifier.calls.isEmpty()) kotlinx.coroutines.yield()
            billing.purchases.value = PlayPurchases.Loaded(listOf(bought, pending.copy()))
            kotlinx.coroutines.delay(100)
            assertEquals(listOf("tok"), verifier.calls)
            // Restore sends them again.
            repo.restore()
            billing.purchases.value = PlayPurchases.Loaded(listOf(bought))
            while (verifier.calls.size < 2) kotlinx.coroutines.yield()
        }
    }
}
