package tss.t.tsiptv.core.network

import android.app.Application

/**
 * Actual implementation of NetworkConnectivityCheckerFactory for Android.
 */
actual object NetworkConnectivityCheckerFactory {
    private lateinit var context: Application

    /**
     * Initializes the factory with the Android application context.
     * This must be called before using the factory.
     */
    fun initialize(context: Application) {
        this.context = context
    }

    /**
     * Creates an Android-specific NetworkConnectivityChecker.
     * @return A NetworkConnectivityChecker instance for Android.
     */
    actual fun create(): NetworkConnectivityChecker {
        if (!::context.isInitialized) {
            throw IllegalStateException("NetworkConnectivityCheckerFactory must be initialized with a Context")
        }
        return AndroidNetworkConnectivityChecker(context)
    }
}