package tss.t.tsiptv.feature.account

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The quota side of the subscription (docs/prd-subscriptions.md §2.2, §6).
 */
enum class QuotaPlan {
    /** Free and "No ads": 3 sends + rewards, 1 sync + rewards. */
    FREE,

    /**
     * Unlimited bought on this device but not (yet) confirmed by the billing server: the Firestore
     * rules still see a free account, so the client uses everything those rules allow (8 sends,
     * 3 syncs a day) without ads, raising the reward counter together with each extra use.
     */
    UNLIMITED_UNVERIFIED,

    /** Unlimited confirmed by the server: the rules allow the fair-use caps. */
    UNLIMITED_VERIFIED;

    val unlimited: Boolean get() = this != FREE

    companion object {
        fun of(entitlement: tss.t.tsiptv.core.billing.Entitlement): QuotaPlan = when {
            !entitlement.unlimited -> FREE
            entitlement.verified -> UNLIMITED_VERIFIED
            else -> UNLIMITED_UNVERIFIED
        }
    }
}

/**
 * The account's usage for one local day, as stored in `users/{uid}/quota/daily` (PRD §6).
 * Numbers are Long because Firestore integers decode as Long.
 */
@Serializable
data class QuotaState(
    /** Local date as yyyymmdd. */
    val day: Long = 0,
    val sends: Long = 0,
    val sendRewards: Long = 0,
    val syncs: Long = 0,
    val syncRewards: Long = 0,
    /** Rewarded ads watched toward the next sync reward (0..ADS_PER_SYNC_REWARD-1). */
    val syncAds: Long = 0,
    /** Hashed /24 network ids the sends went to (PRD §3.2), at most [QuotaPolicy.MAX_NETWORKS]. */
    val networks: List<String> = emptyList(),
    val updatedAt: Long = 0,
)

/** What a rewarded ad did to the quota. */
enum class RewardOutcome {
    /** One more use is available now. */
    GRANTED,

    /** Counted toward a reward that needs more ads (sync: 2 ads). */
    PROGRESS,

    /** The daily reward cap is reached; nothing changed. */
    CAPPED,
}

/**
 * Pure quota rules shared by the UI and the repository. The Firestore rules enforce the same caps
 * ([FREE_SENDS], [MAX_SEND_REWARDS], [FREE_SYNCS], [MAX_SYNC_REWARDS]); keep them in sync.
 */
class QuotaPolicy(val plan: QuotaPlan = QuotaPlan.FREE) {

    /** The UI shows "unlimited" and no rewarded tasks. */
    val unlimited: Boolean get() = plan.unlimited

    /** The state for [today]: unchanged on the same day, a fresh one on a later day. */
    fun rollover(state: QuotaState?, today: Long): QuotaState = when {
        state == null || state.day < today -> QuotaState(day = today, networks = emptyList())
        else -> state
    }

    fun sendLimit(state: QuotaState): Long = when (plan) {
        QuotaPlan.FREE -> FREE_SENDS + state.sendRewards
        QuotaPlan.UNLIMITED_UNVERIFIED -> FREE_SENDS + MAX_SEND_REWARDS
        QuotaPlan.UNLIMITED_VERIFIED -> FAIR_USE_SENDS
    }

    fun syncLimit(state: QuotaState): Long = when (plan) {
        QuotaPlan.FREE -> FREE_SYNCS + state.syncRewards
        QuotaPlan.UNLIMITED_UNVERIFIED -> FREE_SYNCS + MAX_SYNC_REWARDS
        QuotaPlan.UNLIMITED_VERIFIED -> FAIR_USE_SYNCS
    }

    fun remainingSends(state: QuotaState): Long = (sendLimit(state) - state.sends).coerceAtLeast(0)

    fun remainingSyncs(state: QuotaState): Long = (syncLimit(state) - state.syncs).coerceAtLeast(0)

    fun canSend(state: QuotaState): Boolean = remainingSends(state) > 0
    fun canSync(state: QuotaState): Boolean = remainingSyncs(state) > 0

    /** Rewarded tasks exist only on the free quota (Unlimited hides them). */
    fun canEarnSendReward(state: QuotaState): Boolean = !unlimited && state.sendRewards < MAX_SEND_REWARDS
    fun canEarnSyncReward(state: QuotaState): Boolean = !unlimited && state.syncRewards < MAX_SYNC_REWARDS

    /** Records one send (to the network [networkId], if known). Call only when [canSend]. */
    fun afterSend(state: QuotaState, networkId: String?, now: Long): QuotaState {
        check(canSend(state)) { "No sends left" }
        val networks = if (networkId == null || networkId in state.networks) state.networks
        else (state.networks + networkId).takeLast(MAX_NETWORKS)
        val sends = state.sends + 1
        // Unverified Unlimited: the rules require sends <= 3 + sendRewards, so pair the extra use
        // with one reward step in the same write (the rules allow +1 on each counter).
        val rewards = if (plan == QuotaPlan.UNLIMITED_UNVERIFIED && sends > FREE_SENDS + state.sendRewards) {
            state.sendRewards + 1
        } else state.sendRewards
        return state.copy(sends = sends, sendRewards = rewards, networks = networks, updatedAt = now)
    }

    fun afterSync(state: QuotaState, now: Long): QuotaState {
        check(canSync(state)) { "No syncs left" }
        val syncs = state.syncs + 1
        val rewards = if (plan == QuotaPlan.UNLIMITED_UNVERIFIED && syncs > FREE_SYNCS + state.syncRewards) {
            state.syncRewards + 1
        } else state.syncRewards
        return state.copy(syncs = syncs, syncRewards = rewards, updatedAt = now)
    }

    /** One rewarded ad watched for a send: +1 send, up to [MAX_SEND_REWARDS] a day. */
    fun afterSendReward(state: QuotaState, now: Long): Pair<QuotaState, RewardOutcome> =
        if (!canEarnSendReward(state)) state to RewardOutcome.CAPPED
        else state.copy(sendRewards = state.sendRewards + 1, updatedAt = now) to RewardOutcome.GRANTED

    /** One rewarded ad watched for a sync: every [ADS_PER_SYNC_REWARD] ads give +1 sync. */
    fun afterSyncAd(state: QuotaState, now: Long): Pair<QuotaState, RewardOutcome> {
        if (!canEarnSyncReward(state)) return state to RewardOutcome.CAPPED
        val ads = state.syncAds + 1
        return if (ads >= ADS_PER_SYNC_REWARD) {
            state.copy(syncAds = 0, syncRewards = state.syncRewards + 1, updatedAt = now) to RewardOutcome.GRANTED
        } else {
            state.copy(syncAds = ads, updatedAt = now) to RewardOutcome.PROGRESS
        }
    }

    companion object {
        const val FREE_SENDS = 3L
        const val MAX_SEND_REWARDS = 5L
        const val FREE_SYNCS = 1L
        const val MAX_SYNC_REWARDS = 2L
        const val ADS_PER_SYNC_REWARD = 2L
        const val MAX_NETWORKS = 10

        /** Verified Unlimited, fair use (firestore.rules `validQuota`; docs/prd-subscriptions.md Q4). */
        const val FAIR_USE_SENDS = 200L
        const val FAIR_USE_SYNCS = 50L

        /** The local date of [epochMs] in [zone] as yyyymmdd: the day resets at local midnight. */
        @OptIn(ExperimentalTime::class)
        fun dayKey(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long {
            val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date
            return date.year * 10_000L + date.month.ordinal.plus(1) * 100L + date.day
        }
    }
}
