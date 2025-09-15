package tss.t.tsiptv.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import tss.t.tsiptv.AndroidPlatformUtils

/**
 * Android implementation of ScreenOrientationUtils.
 */
class AndroidScreenOrientationUtils : ScreenOrientationUtils {
    private var isFullScreen = false

    /**
     * Set the player view controller that will be used for full-screen mode.
     * This is only used on iOS, but is included in the interface for consistency.
     *
     * @param viewController The player view controller, or null to clear it.
     */
    override fun setPlayerViewController(viewController: Any?) {
        // No-op on Android
    }

    /**
     * Enter full-screen mode.
     * This will:
     * 1. Set the screen orientation to landscape
     * 2. Hide the system UI (status bar, navigation bar)
     */
    override fun enterFullScreen() {
        val activity = AndroidPlatformUtils.appContext as? Activity
        activity ?: return

        // Set screen orientation to landscape
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Hide the system UI
        val decorView = activity.window.decorView
        val uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decorView.systemUiVisibility = uiOptions

        // Keep the screen on
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isFullScreen = true
    }

    /**
     * Exit full-screen mode.
     * This will:
     * 1. Set the screen orientation to portrait
     * 2. Show the system UI (status bar, navigation bar)
     */
    override fun exitFullScreen() {
        val activity = AndroidPlatformUtils.appContext as? Activity
        activity ?: return

        // Set screen orientation to portrait
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Show the system UI
        val decorView = activity.window.decorView
        val uiOptions = View.SYSTEM_UI_FLAG_VISIBLE
        decorView.systemUiVisibility = uiOptions

        // Allow the screen to turn off
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isFullScreen = false
    }

    /**
     * Toggle full-screen mode.
     */
    override fun toggleFullScreen() {
        if (isFullScreen()) {
            exitFullScreen()
        } else {
            enterFullScreen()
        }
    }

    /**
     * Check if the device is in landscape orientation.
     */
    override fun isLandscape(): Boolean {
        val context = AndroidPlatformUtils.appContext
        val orientation = context.resources.configuration.orientation
        return orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Check if the device is in portrait orientation.
     */
    override fun isPortrait(): Boolean {
        val context = AndroidPlatformUtils.appContext
        val orientation = context.resources.configuration.orientation
        return orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * Check if the device is in full-screen mode.
     */
    override fun isFullScreen(): Boolean {
        return isFullScreen
    }

    override fun hideSystemUI() {
        val activity = AndroidPlatformUtils.appContext as? Activity
        activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller =
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    override fun showSystemUI() {
        val activity = AndroidPlatformUtils.appContext as? Activity
        activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller =
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        // Singleton instance
        private val INSTANCE = AndroidScreenOrientationUtils()

        fun getInstance(): AndroidScreenOrientationUtils {
            return INSTANCE
        }
    }
}

/**
 * Get the platform-specific implementation of ScreenOrientationUtils.
 */
actual fun getScreenOrientationUtils(): ScreenOrientationUtils {
    return AndroidScreenOrientationUtils.getInstance()
}
