package tss.t.tsiptv.core.ads

import android.util.Log

/**
 * Ads diagnostics (tag `TSAds`), on in debuggable builds only: release builds log nothing. Messages
 * never contain URLs, ad content or user data.
 */
internal object AdsLog {
    const val TAG = "TSAds"

    /** Set from the app's debuggable flag in `Application.onCreate`. */
    @Volatile
    var enabled = false

    inline fun d(message: () -> String) {
        if (enabled) Log.d(TAG, message())
    }

    inline fun i(message: () -> String) {
        if (enabled) Log.i(TAG, message())
    }

    inline fun w(message: () -> String) {
        if (enabled) Log.w(TAG, message())
    }
}
