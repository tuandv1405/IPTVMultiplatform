package tss.t.tsiptv.feature.account

import kotlinx.datetime.TimeZone
import tss.t.tsiptv.core.billing.Plan
import tss.t.tsiptv.core.billing.Entitlement as Ent
import tss.t.tsiptv.core.billing.EntitlementSource as Src
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
        val big = QuotaPolicy(QuotaPlan.UNLIMITED_VERIFIED)
        for (i in 0 until 15) s = big.afterSend(s, "n$i", 0)
        s = big.afterSend(s, "n14", 0)
        assertEquals(QuotaPolicy.MAX_NETWORKS, s.networks.size)
        assertEquals("n14", s.networks.last())
    }

    // --- Subscriptions (docs/prd-subscriptions.md §2.2, §6) ---------------------------------------

    @Test
    fun planFollowsTheEntitlement() {
        assertEquals(QuotaPlan.FREE, QuotaPlan.of(Ent.FREE))
        assertEquals(QuotaPlan.FREE, QuotaPlan.of(Ent(plan = Plan.NO_ADS, source = Src.SERVER)))
        assertEquals(QuotaPlan.UNLIMITED_UNVERIFIED, QuotaPlan.of(Ent(plan = Plan.UNLIMITED, source = Src.PLAY)))
        assertEquals(QuotaPlan.UNLIMITED_UNVERIFIED, QuotaPlan.of(Ent(plan = Plan.UNLIMITED, source = Src.CACHE)))
        assertEquals(QuotaPlan.UNLIMITED_VERIFIED, QuotaPlan.of(Ent(plan = Plan.UNLIMITED, source = Src.SERVER)))
    }

    @Test
    fun verifiedUnlimitedHasTheFairUseCapsAndNoTasks() {
        val unlimited = QuotaPolicy(QuotaPlan.UNLIMITED_VERIFIED)
        val s = QuotaState(day = today)
        assertTrue(unlimited.unlimited)
        assertEquals(QuotaPolicy.FAIR_USE_SENDS, unlimited.remainingSends(s))
        assertEquals(QuotaPolicy.FAIR_USE_SYNCS, unlimited.remainingSyncs(s))
        assertTrue(unlimited.canSend(s.copy(sends = 199)))
        assertFalse(unlimited.canSend(s.copy(sends = 200)))
        assertFalse(unlimited.canSync(s.copy(syncs = 50)))
        // Rewarded tasks are hidden and never needed.
        assertFalse(unlimited.canEarnSendReward(s))
        assertFalse(unlimited.canEarnSyncReward(s))
        // No reward counter is touched: the rules check sends <= 200 for verified Unlimited.
        val after = unlimited.afterSend(s.copy(sends = 3), null, 1)
        assertEquals(4, after.sends)
        assertEquals(0, after.sendRewards)
    }

    @Test
    fun unverifiedUnlimitedUsesWhatTheFreeRulesAllowWithoutAds() {
        val p = QuotaPolicy(QuotaPlan.UNLIMITED_UNVERIFIED)
        var s = QuotaState(day = today)
        var sends = 0
        while (p.canSend(s)) {
            val next = p.afterSend(s, null, 0)
            // Every write stays valid for the Free rules: +<=1 per counter, sends <= 3 + sendRewards.
            assertTrue(next.sends - s.sends == 1L && next.sendRewards - s.sendRewards in 0L..1L)
            assertTrue(next.sends <= QuotaPolicy.FREE_SENDS + next.sendRewards)
            assertTrue(next.sendRewards <= QuotaPolicy.MAX_SEND_REWARDS)
            s = next
            sends++
        }
        assertEquals(8, sends)
        var syncs = 0
        while (p.canSync(s)) {
            val next = p.afterSync(s, 0)
            assertTrue(next.syncs <= QuotaPolicy.FREE_SYNCS + next.syncRewards)
            assertTrue(next.syncRewards <= QuotaPolicy.MAX_SYNC_REWARDS)
            s = next
            syncs++
        }
        assertEquals(3, syncs)
        assertFalse(p.canEarnSendReward(QuotaState(day = today)))
    }

    @Test
    fun freeAndNoAdsKeepTheRewardRules() {
        val free = QuotaPolicy(QuotaPlan.FREE)
        assertFalse(free.unlimited)
        val s = QuotaState(day = today, sends = 3)
        assertEquals(0, free.remainingSends(s))
        assertTrue(free.canEarnSendReward(s))
        // A free send never raises the reward counter.
        assertEquals(0, free.afterSend(QuotaState(day = today), null, 0).sendRewards)
    }

    @Test
    fun dayKeyFollowsTheLocalZone() {
        // 2026-10-10T17:30Z is still the 10th in UTC but already the 11th in Ho Chi Minh City (UTC+7).
        val ms = 1_791_653_400_000L
        assertEquals(20261010, QuotaPolicy.dayKey(ms, TimeZone.UTC))
        assertEquals(20261011, QuotaPolicy.dayKey(ms, TimeZone.of("Asia/Ho_Chi_Minh")))
    }
}
