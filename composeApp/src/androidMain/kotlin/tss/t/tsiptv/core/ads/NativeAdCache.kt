package tss.t.tsiptv.core.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import java.util.IdentityHashMap

/**
 * Native ads by slot (`placement:slot`), loaded only when a slot is composed (PRD §8), kept a
 * little while for reuse (scrolling back, recomposition) and destroyed when evicted or after
 * [AdsPolicy.NATIVE_AD_MAX_AGE_MS]. An ad being shown is never destroyed. Main thread only.
 */
object NativeAdCache {
    /** Ads not on screen kept for reuse. */
    private const val MAX_IDLE = 3

    private class Entry(val key: String, val ad: NativeAd, val loadedAtMs: Long) {
        var inUse = 0
        var lastUsedMs = loadedAtMs
    }

    private val main = Handler(Looper.getMainLooper())

    /** The current (reusable) ad per slot key. */
    private val current = LinkedHashMap<String, Entry>()

    /**
     * Every ad not yet destroyed, by identity: also ads replaced in [current] while still on screen,
     * which are destroyed when their last slot releases them.
     */
    private val live = IdentityHashMap<NativeAd, Entry>()
    private val pending = HashMap<String, MutableList<(NativeAd?) -> Unit>>()

    fun key(placement: AdPlacement, slot: Int) = "${placement.name}:$slot"

    /**
     * Gives the ad for [key] (cached or newly loaded) to [onResult]; null on no fill or error.
     * Call [release] with that ad when the slot leaves composition.
     */
    fun acquire(context: Context, placement: AdPlacement, key: String, onResult: (NativeAd?) -> Unit) {
        val now = System.currentTimeMillis()
        current[key]?.let { entry ->
            if (now - entry.loadedAtMs < AdsPolicy.NATIVE_AD_MAX_AGE_MS) {
                entry.inUse++
                entry.lastUsedMs = now
                onResult(entry.ad)
                return
            }
            // Expired: no longer reusable. Destroyed now if idle, else on its last release.
            retire(entry)
        }
        val waiting = pending[key]
        if (waiting != null) {
            waiting += onResult
            return
        }
        pending[key] = mutableListOf(onResult)
        AdsLog.d { "Native ad request: $key" }
        val appContext = context.applicationContext
        AdLoader.Builder(appContext, AndroidAdsPlatform.unitId(appContext, placement))
            .forNativeAd { ad ->
                main.post {
                    AdsLog.d { "Native ad loaded: $key" }
                    val callbacks = pending.remove(key).orEmpty()
                    current[key]?.let { retire(it) }
                    val entry = Entry(key, ad, System.currentTimeMillis())
                    current[key] = entry
                    live[ad] = entry
                    callbacks.forEach {
                        entry.inUse++
                        it(ad)
                    }
                    trim()
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdsLog.i { "Native ad not loaded: ${error.code}" }
                    main.post { pending.remove(key).orEmpty().forEach { it(null) } }
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setRequestMultipleImages(false)
                    .build()
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    /** A slot no longer shows [ad]: keep it for reuse (within [MAX_IDLE]) or destroy it if retired. */
    fun release(ad: NativeAd) {
        val entry = live[ad] ?: return
        entry.inUse = (entry.inUse - 1).coerceAtLeast(0)
        entry.lastUsedMs = System.currentTimeMillis()
        if (entry.inUse == 0 && current[entry.key] !== entry) destroy(entry)
        trim()
    }

    /** A slot that left before its ad arrived no longer waits for it. */
    fun cancel(key: String, onResult: (NativeAd?) -> Unit) {
        pending[key]?.remove(onResult)
    }

    /** Takes [entry] out of reuse; destroys it unless a slot still shows it. */
    private fun retire(entry: Entry) {
        if (current[entry.key] === entry) current.remove(entry.key)
        if (entry.inUse == 0) destroy(entry)
    }

    private fun destroy(entry: Entry) {
        live.remove(entry.ad)
        entry.ad.destroy()
    }

    private fun trim() {
        val now = System.currentTimeMillis()
        current.values.filter { it.inUse == 0 && now - it.loadedAtMs >= AdsPolicy.NATIVE_AD_MAX_AGE_MS }
            .forEach { retire(it) }
        current.values.filter { it.inUse == 0 }.sortedBy { it.lastUsedMs }
            .dropLast(MAX_IDLE)
            .forEach { retire(it) }
    }
}
