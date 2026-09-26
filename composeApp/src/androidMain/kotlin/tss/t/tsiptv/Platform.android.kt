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

    override val isTv: Boolean by lazy {
        val context = TSAndroidApplication.instance
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
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
