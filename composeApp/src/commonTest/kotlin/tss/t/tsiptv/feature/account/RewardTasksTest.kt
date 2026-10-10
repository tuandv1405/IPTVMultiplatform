package tss.t.tsiptv.feature.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit-test stand-in for AdMob (never shipped). */
class FakeRewardedAdGateway(
    var availability: RewardAvailability = RewardAvailability.AVAILABLE,
    var result: RewardedAdResult = RewardedAdResult.Earned,
) : RewardedAdGateway {
    var shown = 0
    var preloads = 0
    override fun availability() = availability
    override fun preload(placement: RewardPlacement) {
        preloads++
    }
    override suspend fun show(placement: RewardPlacement): RewardedAdResult {
        shown++
        return result
    }
}

class RewardTasksTest {

    @Test
    fun availabilityFollowsTheAdsRules() {
        assertEquals(RewardAvailability.UNSUPPORTED, RewardAvailability.of(false, false, true, true))
        assertEquals(RewardAvailability.TV_LAYOUT, RewardAvailability.of(true, true, true, true))
        assertEquals(RewardAvailability.AD_FREE_PERIOD, RewardAvailability.of(true, false, false, false))
        assertEquals(RewardAvailability.NO_CONSENT, RewardAvailability.of(true, false, true, false))
        assertEquals(RewardAvailability.AVAILABLE, RewardAvailability.of(true, false, true, true))
    }

    private suspend fun service(ads: FakeRewardedAdGateway, block: suspend (QuotaService, InMemoryAccountCloud) -> Unit) {
        val cloud = InMemoryAccountCloud()
        val storage = InMemoryKeyValueStorage()
        val auth = DeviceLimitTest.FakeAuthForTests("u1")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sessions = DeviceSessionManager(auth, cloud, LocalDevice(storage), storage, scope) { 0L }
        sessions.start()
        withTimeout(5_000) { sessions.uid.first { it != null } }
        val quotas = QuotaService(cloud, sessions, ads, clock = { 1_791_653_400_000L }, zone = { TimeZone.UTC })
        try {
            block(quotas, cloud)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun onlyAnEarnedRewardGrantsASend() = runBlocking<Unit> {
        val ads = FakeRewardedAdGateway(result = RewardedAdResult.Dismissed)
        service(ads) { quotas, cloud ->
            assertEquals(RewardResult.Dismissed, quotas.watchAd(RewardPlacement.EXTRA_SEND))
            assertEquals(null, cloud.quotas["u1"])
            ads.result = RewardedAdResult.Unavailable
            assertEquals(RewardResult.Unavailable, quotas.watchAd(RewardPlacement.EXTRA_SEND))
            ads.result = RewardedAdResult.Earned
            assertEquals(RewardResult.Granted, quotas.watchAd(RewardPlacement.EXTRA_SEND))
            assertEquals(1, cloud.quotas["u1"]!!.sendRewards)
        }
    }

    @Test
    fun noAdIsShownWhenTheAdsLayerSaysNo() = runBlocking<Unit> {
        val ads = FakeRewardedAdGateway(availability = RewardAvailability.AD_FREE_PERIOD)
        service(ads) { quotas, _ ->
            assertEquals(RewardResult.Unavailable, quotas.watchAd(RewardPlacement.EXTRA_SYNC))
            quotas.preloadReward(RewardPlacement.EXTRA_SYNC)
            assertEquals(0, ads.shown)
            assertEquals(0, ads.preloads)
            ads.availability = RewardAvailability.AVAILABLE
            quotas.preloadReward(RewardPlacement.EXTRA_SYNC)
            assertEquals(1, ads.preloads)
        }
    }

    @Test
    fun twoAdsGiveOneSyncAndTheCapsHold() = runBlocking<Unit> {
        val ads = FakeRewardedAdGateway()
        service(ads) { quotas, cloud ->
            assertTrue(quotas.watchAd(RewardPlacement.EXTRA_SYNC) is RewardResult.Progress)
            assertEquals(RewardResult.Granted, quotas.watchAd(RewardPlacement.EXTRA_SYNC))
            repeat(2) { quotas.watchAd(RewardPlacement.EXTRA_SYNC) }
            assertEquals(QuotaPolicy.MAX_SYNC_REWARDS, cloud.quotas["u1"]!!.syncRewards)
            val shownBefore = ads.shown
            assertEquals(RewardResult.Capped, quotas.watchAd(RewardPlacement.EXTRA_SYNC))
            // At the cap no ad is shown at all.
            assertEquals(shownBefore, ads.shown)
        }
    }
}
