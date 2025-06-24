package tss.t.tsiptv

import android.content.Context
import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
/**
 * Platform-specific utilities for Android.
 */
object AndroidPlatformUtils {
    /**
     * The application context.
     * This is set by the MainActivity when the app starts.
     */
    lateinit var appContext: Context
}
