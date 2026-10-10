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
 * App open ad, cold start only (PRD R2): requested as soon as the process may load ads (stored
 * consent, 24 h over), shown at most once per process, only if it is ready within
 * [AdsPolicy.APP_OPEN_TIMEOUT_MS] of the first screen becoming visible (first activity resume) and
 * while that screen is resumed, never over playback, never older than 4 h. After
 * returning from background, or a configuration change, the process-level [handled] flag keeps it
 * from showing again.
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

    /** Shown, skipped or timed out in this process: never again until the next cold start. */
    var handled = false
        private set

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
                    AdsLog.i { "App open ad loaded at +${sinceProcessStart()} ms" }
                    if (handled) return // timed out meanwhile: discard
                    ad = loaded
                    loadedAtMs = System.currentTimeMillis()
                    tryShow()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    AdsLog.i { "App open ad not loaded: ${error.code}" }
                    skip("no fill")
                }
            },
        )
    }

    fun onActivityResumed(activity: Activity) {
        resumed = true
        if (windowStartElapsed == 0L) {
            windowStartElapsed = SystemClock.elapsedRealtime()
            AdsLog.i { "First screen visible at +${sinceProcessStart()} ms" }
            // Past the window nothing will be shown: drop the ad then (no late show, no leak).
            main.postDelayed({ if (!handled) skip("timeout") }, timeoutMs)
        }
        AndroidAdsPlatform.onActivityResumed(activity)
        tryShow()
    }

    fun onActivityPaused() {
        resumed = false
    }

    private fun tryShow() {
        val current = ad ?: return
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
        )
        if (!allowed) {
            if (isPlaying() || !adsAllowed()) skip("not allowed now")
            return
        }
        handled = true
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = release()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = release()
        }
        current.show(activity)
    }

    private fun skip(reason: String) {
        if (handled) return
        handled = true
        AdsLog.i { "App open ad skipped: $reason" }
        release()
    }

    private fun release() {
        ad?.fullScreenContentCallback = null
        ad = null
        loadedAtMs = null
    }
}
