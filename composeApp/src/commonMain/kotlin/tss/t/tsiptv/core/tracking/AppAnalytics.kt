package tss.t.tsiptv.core.tracking

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import tss.t.tsiptv.utils.AppFlags

/**
 * Firebase Analytics where the platform has it. On desktop gitlive's JVM `Firebase.analytics` is not
 * implemented and throws, so every call there is dropped ([AppFlags.analyticsAvailable]).
 */
object AppAnalytics {
    fun logEvent(name: String, parameters: Map<String, Any>? = null) {
        if (AppFlags.analyticsAvailable) Firebase.analytics.logEvent(name, parameters)
    }

    fun setUserId(id: String?) {
        if (AppFlags.analyticsAvailable) Firebase.analytics.setUserId(id)
    }
}
