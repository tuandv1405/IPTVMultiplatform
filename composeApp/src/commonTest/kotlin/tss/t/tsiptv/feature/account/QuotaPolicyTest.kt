package tss.t.tsiptv.feature.account

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuotaPolicyTest {
    private val policy = QuotaPolicy()
    private val today = 20261010L

    @Test
    fun threeFreeSendsADay() {
        var s = policy.rollover(null, today)
        repeat(3) {
            assertTrue(policy.canSend(s))
            s = policy.afterSend(s, "net1", 0)
        }
        assertFalse(policy.canSend(s))
        assertEquals(0, policy.remainingSends(s))
        assertFailsWith<IllegalStateException> { policy.afterSend(s, null, 0) }
        assertEquals(listOf("net1"), s.networks)
    }

    @Test
    fun rewardGivesExactlyOneMoreSendUpToTheCap() {
        var s = QuotaState(day = today, sends = 3)
        val (rewarded, outcome) = policy.afterSendReward(s, 1)
        assertEquals(RewardOutcome.GRANTED, outcome)
        assertEquals(1, policy.remainingSends(rewarded))
        s = policy.afterSend(rewarded, null, 2)
        assertFalse(policy.canSend(s))

        var capped = QuotaState(day = today, sendRewards = QuotaPolicy.MAX_SEND_REWARDS)
        val (same, result) = policy.afterSendReward(capped, 3)
        assertEquals(RewardOutcome.CAPPED, result)
        assertEquals(capped, same)
        capped = capped.copy(sends = QuotaPolicy.FREE_SENDS + QuotaPolicy.MAX_SEND_REWARDS)
        assertFalse(policy.canSend(capped))
    }

    @Test
    fun rolloverResetsOnALaterDayOnly() {
        val used = QuotaState(day = today, sends = 3, syncs = 1, sendRewards = 2, networks = listOf("a"))
        assertEquals(used, policy.rollover(used, today))
        val next = policy.rollover(used, today + 1)
        assertEquals(QuotaState(day = today + 1), next)
        // A stored day in the future (other device's time zone) is kept, not reset.
        assertEquals(used, policy.rollover(used, today - 1))
    }

    @Test
    fun oneSyncADayAndTwoAdsPerExtraSync() {
        var s = QuotaState(day = today)
        assertTrue(policy.canSync(s))
        s = policy.afterSync(s, 1)
        assertFalse(policy.canSync(s))

        val (one, first) = policy.afterSyncAd(s, 2)
        assertEquals(RewardOutcome.PROGRESS, first)
        assertEquals(1, one.syncAds)
        assertFalse(policy.canSync(one))
        val (two, second) = policy.afterSyncAd(one, 3)
        assertEquals(RewardOutcome.GRANTED, second)
        assertEquals(0, two.syncAds)
        assertEquals(1, two.syncRewards)
        assertTrue(policy.canSync(two))

        val capped = QuotaState(day = today, syncRewards = QuotaPolicy.MAX_SYNC_REWARDS)
        assertEquals(RewardOutcome.CAPPED, policy.afterSyncAd(capped, 4).second)
    }

    @Test
    fun networksAreDistinctAndBounded() {
        var s = QuotaState(day = today, sendRewards = 5)
        val big = QuotaPolicy(Entitlement(extraSendsPerDay = 20))
        for (i in 0 until 15) s = big.afterSend(s, "n$i", 0)
        s = big.afterSend(s, "n14", 0)
        assertEquals(QuotaPolicy.MAX_NETWORKS, s.networks.size)
        assertEquals("n14", s.networks.last())
    }

    @Test
    fun entitlementPlugsIn() {
        val paid = QuotaPolicy(Entitlement(extraSendsPerDay = 7, extraSyncsPerDay = 4))
        val s = QuotaState(day = today)
        assertEquals(10, paid.remainingSends(s))
        assertEquals(5, paid.remainingSyncs(s))
        val unlimited = QuotaPolicy(Entitlement(unlimited = true))
        assertTrue(unlimited.canSend(QuotaState(day = today, sends = 1000)))
    }

    @Test
    fun dayKeyFollowsTheLocalZone() {
        // 2026-10-10T17:30Z is still the 10th in UTC but already the 11th in Ho Chi Minh City (UTC+7).
        val ms = 1_791_653_400_000L
        assertEquals(20261010, QuotaPolicy.dayKey(ms, TimeZone.UTC))
        assertEquals(20261011, QuotaPolicy.dayKey(ms, TimeZone.of("Asia/Ho_Chi_Minh")))
    }
}
