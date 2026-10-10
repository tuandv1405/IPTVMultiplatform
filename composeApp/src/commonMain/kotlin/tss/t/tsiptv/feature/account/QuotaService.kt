package tss.t.tsiptv.feature.account

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone

/** Result of watching a rewarded ad for more uses. */
sealed interface RewardResult {
    data object Granted : RewardResult
    /** Counted; [watched] of [needed] ads for the next sync. */
    data class Progress(val watched: Long, val needed: Long) : RewardResult
    data object Capped : RewardResult
    data object Unavailable : RewardResult
    data object Dismissed : RewardResult
    data object SignedOut : RewardResult
    data object Failed : RewardResult
}

/**
 * Daily send / sync quotas of the signed-in account, stored server-side (`users/{uid}/quota/daily`)
 * so a reinstall does not reset them (PRD §6). The day is the device's local date.
 */
class QuotaService(
    private val cloud: AccountCloud,
    private val sessions: DeviceSessionManager,
    private val rewarded: RewardedAdGateway,
    private val clock: () -> Long,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    /** The subscription's quota side, read on every use (a plan can change while a sheet is open). */
    private val plan: () -> QuotaPlan = { QuotaPlan.FREE },
) {
    /** The rules for the current plan (docs/prd-subscriptions.md §2.2). */
    val policy: QuotaPolicy get() = QuotaPolicy(plan())

    fun today(): Long = QuotaPolicy.dayKey(clock(), zone())

    /** Whether the rewarded-ad tasks can be offered now (else the UI explains why). */
    fun rewardAvailability(): RewardAvailability = rewarded.availability()

    /** Loads one rewarded ad ahead of the task button (quota screens call it when they open). */
    fun preloadReward(placement: RewardPlacement) {
        if (rewarded.availability() == RewardAvailability.AVAILABLE) rewarded.preload(placement)
    }

    /** Today's quota (not written), or null when signed out. Throws on network failure. */
    suspend fun current(): QuotaState? {
        val uid = sessions.uid.value ?: return null
        return policy.rollover(cloud.loadQuota(uid), today())
    }

    /** Counts one send after the TV accepted it. false when none was left or it could not be written. */
    suspend fun recordSend(networkId: String?): Boolean {
        val uid = sessions.uid.value ?: return false
        return try {
            cloud.updateQuota(uid) { stored ->
                val state = policy.rollover(stored, today())
                if (policy.canSend(state)) policy.afterSend(state, networkId, clock()) else null
            } != null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Shows one rewarded ad and, on `Earned` only, adds the reward. */
    suspend fun watchAd(placement: RewardPlacement): RewardResult {
        val uid = sessions.uid.value ?: return RewardResult.SignedOut
        val before = try {
            current() ?: return RewardResult.SignedOut
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return RewardResult.Failed
        }
        val policy = policy
        val capped = when (placement) {
            RewardPlacement.EXTRA_SEND -> !policy.canEarnSendReward(before)
            RewardPlacement.EXTRA_SYNC -> !policy.canEarnSyncReward(before)
        }
        if (capped) return RewardResult.Capped
        if (rewarded.availability() != RewardAvailability.AVAILABLE) return RewardResult.Unavailable
        when (rewarded.show(placement)) {
            RewardedAdResult.Earned -> Unit
            RewardedAdResult.Dismissed -> return RewardResult.Dismissed
            RewardedAdResult.Unavailable -> return RewardResult.Unavailable
        }
        var outcome = RewardOutcome.CAPPED
        return try {
            val after = cloud.updateQuota(uid) { stored ->
                val state = policy.rollover(stored, today())
                val (next, result) = when (placement) {
                    RewardPlacement.EXTRA_SEND -> policy.afterSendReward(state, clock())
                    RewardPlacement.EXTRA_SYNC -> policy.afterSyncAd(state, clock())
                }
                outcome = result
                next.takeIf { result != RewardOutcome.CAPPED }
            }
            when {
                after == null -> RewardResult.Capped
                outcome == RewardOutcome.PROGRESS -> RewardResult.Progress(after.syncAds, QuotaPolicy.ADS_PER_SYNC_REWARD)
                else -> RewardResult.Granted
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RewardResult.Failed
        }
    }
}
