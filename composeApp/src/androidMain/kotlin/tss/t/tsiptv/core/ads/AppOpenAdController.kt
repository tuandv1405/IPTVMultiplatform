package tss.t.tsiptv.core.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import org.koin.core.context.GlobalContext
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.PlaybackState

/**
 * App open ad, app launches only (PRD R2): requested as soon as the process may load ads (stored
 * consent, 24 h over), shown at most once per launch, only if it is ready within
 * [AdsPolicy.APP_OPEN_TIMEOUT_MS] of the first screen becoming visible (first activity resume) and
 * while that screen is resumed, never over playback, never older than 4 h, at most once per
 * [AdsPolicy.APP_OPEN_MIN_INTERVAL_MS]. After returning from background to a live screen, or a
 * configuration change, the per-launch [handled] flag keeps it from showing again.
 *
 * Smarter preload (device QC 1): an ad that loads after the window, or that could not be shown, is
 * kept (≤ 4 h) instead of dropped, and one is preloaded when the app goes to background. It is used
 * by the next launch in the same process: the first screen created again from scratch after the user
 * left with Back ([onLaunch]); there it is ready at once instead of racing the 4 s window.
 */
object AppOpenAdController {
    /** `SystemClock.elapsedRealtime()` when the process started (for timing logs). */
    private var processStartElapsed = 0L

    /** `SystemClock.elapsedRealtime()` at the first activity resume: the 4 s window starts here. */
    private var windowStartElapsed = 0L
    private val main = Handler(Looper.getMainLooper())

    private var ad: AppOpenAd? = null
    private var loadedAtMs: Long? = null
    private var loading = false

    /** Shown, skipped or timed out for this launch: never again until the next launch. */
    var handled = false
        private set

    /** Launches of the UI in this process (see [onLaunch]); also tags the window's timeout. */
    private var launch = 0

    /** When an app open ad was last shown in this process ([AdsPolicy.APP_OPEN_MIN_INTERVAL_MS]). */
    private var lastShownAtMs: Long? = null

    /** The ad is on screen (its activity stops ours: no preload then). */
    private var showing = false

    private var resumed = false

    /**
     * [AdsPolicy.APP_OPEN_TIMEOUT_MS]; a debug build may raise it with
     * `-Ptsiptv.debugAppOpenTimeoutMs=…` (slow x86 emulators load test ads in ~5 s).
     */
    private var timeoutMs = AdsPolicy.APP_OPEN_TIMEOUT_MS

    fun onProcessStart(debugTimeoutMs: Long = 0L) {
        processStartElapsed = Process.getStartElapsedRealtime()
        if (debugTimeoutMs > 0) timeoutMs = debugTimeoutMs
    }

    /** Milliseconds since the process started (logs only). */
    fun sinceProcessStart(): Long = SystemClock.elapsedRealtime() - processStartElapsed

    /** Milliseconds since the first screen became visible; 0 before that. */
    private fun sinceWindowStart(): Long =
        if (windowStartElapsed == 0L) 0L else SystemClock.elapsedRealtime() - windowStartElapsed

    /**
     * [AdsGate] opened AdMob and the SDK was started: request now (once). The gate calls this only
     * when AdMob may show, so no further wait is needed.
     */
    fun onSdkReady(context: Context) {
        // Ahead of the start-up work queued on the main thread: every millisecond counts here.
        main.postAtFrontOfQueue { load(context.applicationContext) }
    }

    private fun adsAllowed(): Boolean =
        runCatching { GlobalContext.get().get<AdsGate>().adMobAllowedNow }.getOrDefault(false)

    /**
     * Playback is running or about to (buffering, or ready after a play request). The app never
     * covers it, so BUFFERING and READY count as playing too (READY after an explicit pause is
     * reported as PAUSED by the player).
     */
    private fun isPlaying(): Boolean = runCatching {
        val player = GlobalContext.get().get<MediaPlayer>()
        player.isPlaying.value || player.playbackState.value in ACTIVE_STATES
    }.getOrDefault(false)

    private val ACTIVE_STATES = setOf(PlaybackState.PLAYING, PlaybackState.BUFFERING, PlaybackState.READY)

    /**
     * MainActivity was created without saved state: a launch of the UI. The first one is the cold
     * start (process-level state is already fresh). A later one (the user left with Back, the process
     * stayed alive, and the app is opened again) is a new launch with its own 4 s window. A kept ad
     * shows at once, or one is requested now. Returning to a live screen never calls this.
     */
    fun onLaunch(context: Context) {
        launch++
        // No screen was visible yet in this process: this is the cold start itself.
        if (windowStartElapsed == 0L) return
        handled = false
        windowStartElapsed = 0L
        AdsLog.i { "New launch in a running process (kept ad: ${ad != null})" }
        if (ad == null && !loading && adsAllowed()) requestAd(context.applicationContext)
    }

    /**
     * The app went to background (not a configuration change): preload one for the next launch if
     * none is kept. Nothing is shown now; the ad expires after 4 h like any other.
     */
    fun onAppBackground(context: Context) {
        if (ad != null || loading || showing || !adsAllowed()) return
        AdsLog.i { "App open ad: preload for the next launch" }
        requestAd(context.applicationContext)
    }

    private fun load(context: Context) {
        if (handled || loading || ad != null) return
        if (sinceWindowStart() > timeoutMs) {
            skip("late")
            return
        }
        requestAd(context)
    }

    private fun requestAd(context: Context) {
        loading = true
        AdsLog.i { "App open ad requested at +${sinceProcessStart()} ms" }
        AppOpenAd.load(
            context,
            AndroidAdsPlatform.unitId(context, AdPlacement.APP_OPEN),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    loading = false
                    ad = loaded
                    loadedAtMs = System.currentTimeMillis()
                    if (handled) {
                        // Too late for this launch: kept (≤ 4 h) for the next one instead of dropped.
                        AdsLog.i { "App open ad loaded at +${sinceProcessStart()} ms, kept for the next launch" }
                        return
                    }
                    AdsLog.i { "App open ad loaded at +${sinceProcessStart()} ms" }
                    tryShow()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    AdsLog.i { "App open ad not loaded: ${error.code}" }
                    if (windowStartElapsed != 0L) skip("no fill")
                }
            },
        )
    }

    fun onActivityResumed(activity: Activity) {
        resumed = true
        if (windowStartElapsed == 0L) {
            windowStartElapsed = SystemClock.elapsedRealtime()
            AdsLog.i { "First screen visible at +${sinceProcessStart()} ms" }
            // Past the window nothing is shown for this launch (a late ad is kept for the next one).
            val thisLaunch = launch
            main.postDelayed({ if (!handled && launch == thisLaunch) skip("timeout") }, timeoutMs)
        }
        AndroidAdsPlatform.onActivityResumed(activity)
        tryShow()
    }

    fun onActivityPaused() {
        resumed = false
    }

    private fun tryShow() {
        val current = ad ?: return
        val loaded = loadedAtMs
        if (loaded == null || !AdsPolicy.isAppOpenFresh(loaded, System.currentTimeMillis())) {
            release() // older than 4 h: never shown
            return
        }
        if (windowStartElapsed == 0L) return // not a launch window (background preload)
        val activity = AndroidAdsPlatform.currentActivity() ?: return
        val allowed = AdsPolicy.canShowAppOpen(
            alreadyHandled = handled,
            sinceProcessStartMs = sinceWindowStart(),
            appInForeground = resumed && !activity.isFinishing,
            isPlaying = isPlaying(),
            adsAllowed = adsAllowed(),
            loadedAtMs = loadedAtMs,
            nowMs = System.currentTimeMillis(),
            timeoutMs = timeoutMs,
            lastShownAtMs = lastShownAtMs,
        )
        if (!allowed) {
            when {
                !adsAllowed() -> {
                    skip("not allowed now")
                    release() // e.g. a subscriber now: drop it
                }
                isPlaying() -> skip("playback (kept for the next launch)")
                lastShownAtMs?.let { System.currentTimeMillis() - it in 0 until AdsPolicy.APP_OPEN_MIN_INTERVAL_MS } == true ->
                    skip("shown recently (kept)")
            }
            return
        }
        handled = true
        lastShownAtMs = System.currentTimeMillis()
        ad = null // one show per ad object; the callback still releases it
        loadedAtMs = null
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                current.fullScreenContentCallback = null
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                current.fullScreenContentCallback = null
            }
        }
        showing = true
        current.show(activity)
    }

    /** Nothing more for this launch. A loaded ad is kept (≤ 4 h) for the next launch. */
    private fun skip(reason: String) {
        if (handled) return
        handled = true
        AdsLog.i { "App open ad skipped: $reason" }
    }

    private fun release() {
        ad?.fullScreenContentCallback = null
        ad = null
        loadedAtMs = null
    }
}
