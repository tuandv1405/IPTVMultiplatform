package tss.t.tsiptv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val isAndroid: Boolean
        get() = true

    // Detected once the Application exists. Without one (JVM unit tests) this is "not a TV", and that
    // answer is not cached.
    private var tv: Boolean? = null

    override val isTv: Boolean
        get() = tv ?: TSAndroidApplication.instanceOrNull?.let(::detectTv)?.also { tv = it } ?: false

    private fun detectTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
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
