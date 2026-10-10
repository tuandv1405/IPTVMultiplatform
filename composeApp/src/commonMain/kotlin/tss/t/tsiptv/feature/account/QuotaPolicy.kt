package tss.t.tsiptv.feature.account

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Paid-phase hook (PRD §8). The MVP always passes [FREE]; a server-written entitlement document
 * will supply the others.
 */
data class Entitlement(
    val extraSendsPerDay: Int = 0,
    val extraSyncsPerDay: Int = 0,
    val unlimited: Boolean = false,
) {
    companion object {
        val FREE = Entitlement()
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
class QuotaPolicy(private val entitlement: Entitlement = Entitlement.FREE) {

    /** The state for [today]: unchanged on the same day, a fresh one on a later day. */
    fun rollover(state: QuotaState?, today: Long): QuotaState = when {
        state == null || state.day < today -> QuotaState(day = today, networks = emptyList())
        else -> state
    }

    fun sendLimit(state: QuotaState): Long = FREE_SENDS + entitlement.extraSendsPerDay + state.sendRewards
    fun syncLimit(state: QuotaState): Long = FREE_SYNCS + entitlement.extraSyncsPerDay + state.syncRewards

    fun remainingSends(state: QuotaState): Long =
        if (entitlement.unlimited) Long.MAX_VALUE else (sendLimit(state) - state.sends).coerceAtLeast(0)

    fun remainingSyncs(state: QuotaState): Long =
        if (entitlement.unlimited) Long.MAX_VALUE else (syncLimit(state) - state.syncs).coerceAtLeast(0)

    fun canSend(state: QuotaState): Boolean = remainingSends(state) > 0
    fun canSync(state: QuotaState): Boolean = remainingSyncs(state) > 0

    fun canEarnSendReward(state: QuotaState): Boolean = state.sendRewards < MAX_SEND_REWARDS
    fun canEarnSyncReward(state: QuotaState): Boolean = state.syncRewards < MAX_SYNC_REWARDS

    /** Records one send (to the network [networkId], if known). Call only when [canSend]. */
    fun afterSend(state: QuotaState, networkId: String?, now: Long): QuotaState {
        check(canSend(state)) { "No sends left" }
        val networks = if (networkId == null || networkId in state.networks) state.networks
        else (state.networks + networkId).takeLast(MAX_NETWORKS)
        return state.copy(sends = state.sends + 1, networks = networks, updatedAt = now)
    }

    fun afterSync(state: QuotaState, now: Long): QuotaState {
        check(canSync(state)) { "No syncs left" }
        return state.copy(syncs = state.syncs + 1, updatedAt = now)
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

        /** The local date of [epochMs] in [zone] as yyyymmdd: the day resets at local midnight. */
        @OptIn(ExperimentalTime::class)
        fun dayKey(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long {
            val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date
            return date.year * 10_000L + date.month.ordinal.plus(1) * 100L + date.day
        }
    }
}
