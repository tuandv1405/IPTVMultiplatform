package tss.t.tsiptv.feature.account

/** Where a rewarded ad is shown, for the ad unit and for analytics. */
enum class RewardPlacement { EXTRA_SEND, EXTRA_SYNC }

sealed interface RewardedAdResult {
    /** The SDK reported `onUserEarnedReward`. Only this grants anything. */
    data object Earned : RewardedAdResult

    /** The user closed the ad before the reward. */
    data object Dismissed : RewardedAdResult

    /** No ad could be loaded or shown (no fill, offline, error). */
    data object Unavailable : RewardedAdResult
}

/** Whether the rewarded-ad tasks can be offered now, and if not, why (the UI explains it). */
enum class RewardAvailability {
    AVAILABLE,

    /** The first 24 h of use are ad-free (docs/prd-admob.md). */
    AD_FREE_PERIOD,

    /** UMP consent does not allow ad requests. */
    NO_CONSENT,

    /** The TV layout never shows ads. */
    TV_LAYOUT,

    /** No rewarded ads on this platform (iOS, desktop). */
    UNSUPPORTED;

    companion object {
        /**
         * The ads layer's rules applied to rewarded ads, in the order the user can act on them:
         * the TV layout, then the 24 h ad-free start, then consent.
         */
        fun of(supported: Boolean, tvLayout: Boolean, firstDayOver: Boolean, canRequestAds: Boolean): RewardAvailability = when {
            !supported -> UNSUPPORTED
            tvLayout -> TV_LAYOUT
            !firstDayOver -> AD_FREE_PERIOD
            !canRequestAds -> NO_CONSENT
            else -> AVAILABLE
        }
    }
}

/**
 * Rewarded ads for extra sends / syncs (PRD §3.3). Rewarded is the AdMob format made for "watch an
 * ad to earn something"; banner clicks and timed interstitials are never rewarded (AdMob policy).
 *
 * Android: `AdMobRewardedAdGateway` (AdMob `RewardedAd`, behind the ads layer's consent / 24 h / TV
 * rules). Other platforms: [UnavailableRewardedAdGateway].
 */
interface RewardedAdGateway {
    fun availability(): RewardAvailability

    /** Starts loading one ad so the task button shows it without waiting. No-op when not available. */
    fun preload(placement: RewardPlacement) = Unit

    /** Shows one rewarded ad (loading it first if needed). Call from the UI while an Activity is resumed. */
    suspend fun show(placement: RewardPlacement): RewardedAdResult
}

/** iOS / desktop: no rewarded ads yet. */
object UnavailableRewardedAdGateway : RewardedAdGateway {
    override fun availability() = RewardAvailability.UNSUPPORTED
    override suspend fun show(placement: RewardPlacement): RewardedAdResult = RewardedAdResult.Unavailable
}
