package tss.t.tsiptv.feature.account

import kotlinx.coroutines.delay

/** Where a rewarded ad is shown, for the ad unit and for analytics. */
enum class RewardPlacement { EXTRA_SEND, EXTRA_SYNC }

sealed interface RewardedAdResult {
    /** The SDK reported `onUserEarnedReward`. Only this grants anything. */
    data object Earned : RewardedAdResult

    /** The user closed the ad before the reward. */
    data object Dismissed : RewardedAdResult

    /** No ad could be loaded or shown (no fill, offline, consent, not wired yet). */
    data object Unavailable : RewardedAdResult
}

/**
 * Rewarded ads for extra sends / syncs (PRD §3.3). Rewarded is the AdMob format made for "watch an
 * ad to earn something"; banner clicks and timed interstitials are never rewarded (AdMob policy).
 *
 * This branch ships [FakeRewardedAdGateway]. The ads branch provides the AdMob `RewardedAd`
 * implementation and binds it in Koin in place of the fake (see docs/handoff-tv-cast-and-sync.md).
 */
interface RewardedAdGateway {
    /** Whether an ad can probably be shown now (the UI hides the task otherwise). */
    val isAvailable: Boolean

    /** Loads (if needed) and shows one rewarded ad. Must be called from the UI (needs an Activity). */
    suspend fun show(placement: RewardPlacement): RewardedAdResult
}

/**
 * Stand-in until AdMob is wired: in debug builds it "shows" an ad by waiting [delayMs] and grants the
 * reward; in release builds it reports [RewardedAdResult.Unavailable], so no free rewards ship.
 */
class FakeRewardedAdGateway(
    private val grants: Boolean,
    private val delayMs: Long = 1_500,
) : RewardedAdGateway {
    override val isAvailable: Boolean get() = grants

    override suspend fun show(placement: RewardPlacement): RewardedAdResult {
        if (!grants) return RewardedAdResult.Unavailable
        delay(delayMs)
        return RewardedAdResult.Earned
    }
}
