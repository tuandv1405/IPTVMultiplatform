package tss.t.tsiptv.core.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tss.t.tsiptv.R
import tss.t.tsiptv.feature.account.RewardAvailability
import tss.t.tsiptv.feature.account.RewardPlacement
import tss.t.tsiptv.feature.account.RewardedAdGateway
import tss.t.tsiptv.feature.account.RewardedAdResult
import kotlin.coroutines.resume

/**
 * AdMob rewarded ads for the extra send / sync tasks (docs/prd-tv-cast-and-sync.md §3.3).
 *
 * - Follows the ads layer: nothing on the TV layout, during the 24 h ad-free start, or without UMP
 *   consent ([availability]); the SDK is started by [AdsGate] only once ads may show.
 * - The reward is granted only from `OnUserEarnedRewardListener`.
 * - One ad is cached (application context only, no Activity kept); it is single-use and dropped
 *   after [MAX_AGE_MS] (Google: rewarded ads expire after one hour).
 * - Unit: Google's test rewarded unit in debug, `TSIPTV_ADMOB_REWARDED_UNIT` in release.
 */
class AdMobRewardedAdGateway(
    context: Context,
    private val gate: AdsGate,
    private val platform: AdsPlatform,
) : RewardedAdGateway {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    // Touched on the main thread only.
    private var cached: RewardedAd? = null
    private var cachedAt = 0L
    private var loading: CompletableDeferred<RewardedAd?>? = null

    override fun availability(): RewardAvailability = RewardAvailability.of(
        supported = platform.isAdMobSupported,
        tvLayout = gate.tvLayout,
        firstDayOver = gate.adFreePeriodOver,
        canRequestAds = platform.canRequestAds.value && gate.adMobAllowedNow,
    )

    override fun preload(placement: RewardPlacement) {
        if (availability() != RewardAvailability.AVAILABLE) return
        main.post { startLoad() }
    }

    /** Main thread. Starts one load unless an ad is cached or loading; returns the pending result. */
    private fun startLoad(): CompletableDeferred<RewardedAd?> {
        cached?.let { ad ->
            if (SystemClock.elapsedRealtime() - cachedAt < MAX_AGE_MS) return CompletableDeferred(ad)
            cached = null
        }
        loading?.let { return it }
        val result = CompletableDeferred<RewardedAd?>()
        loading = result
        AdsLog.i { "Rewarded ad requested" }
        RewardedAd.load(app, app.getString(R.string.admob_rewarded_unit), AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                AdsLog.i { "Rewarded ad loaded" }
                cached = ad
                cachedAt = SystemClock.elapsedRealtime()
                loading = null
                result.complete(ad)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                AdsLog.i { "Rewarded ad not loaded: ${error.code}" }
                loading = null
                result.complete(null)
            }
        })
        return result
    }

    override suspend fun show(placement: RewardPlacement): RewardedAdResult {
        if (availability() != RewardAvailability.AVAILABLE) return RewardedAdResult.Unavailable
        val ad = withContext(Dispatchers.Main) {
            withTimeoutOrNull(LOAD_TIMEOUT_MS) { startLoad().await() }
        } ?: return RewardedAdResult.Unavailable
        val activity = AndroidAdsPlatform.currentActivity() ?: return RewardedAdResult.Unavailable
        return withContext(Dispatchers.Main) {
            // Single use: whatever happens next, this ad is not shown again.
            cached = null
            suspendCancellableCoroutine { cont ->
                var earned = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        ad.fullScreenContentCallback = null
                        if (cont.isActive) cont.resume(if (earned) RewardedAdResult.Earned else RewardedAdResult.Dismissed)
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        AdsLog.w { "Rewarded ad failed to show: ${error.code}" }
                        ad.fullScreenContentCallback = null
                        if (cont.isActive) cont.resume(RewardedAdResult.Unavailable)
                    }
                }
                cont.invokeOnCancellation { ad.fullScreenContentCallback = null }
                // The only place a reward is granted.
                ad.show(activity) { earned = true }
            }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L
        const val MAX_AGE_MS = 55 * 60_000L
    }
}
