package tss.t.tsiptv.utils

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVPlayerViewController
import platform.UIKit.UIApplication
import platform.UIKit.setStatusBarHidden

/**
 * iOS implementation of ScreenOrientationUtils.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSScreenOrientationUtils : ScreenOrientationUtils {
    private var isFullScreen = false
    private var playerViewController: AVPlayerViewController? = null

    override fun setPlayerViewController(viewController: Any?) {
       (viewController as? AVPlayerViewController)?.let {
           playerViewController = it
       }
    }

    /**
     * Enter full-screen mode.
     * This will:
     * 1. Hide the status bar
     * 2. Set the player view controller to full-screen mode
     */
    override fun enterFullScreen() {
        // Hide the status bar
        UIApplication.sharedApplication.setStatusBarHidden(true, animated = true)

        // In iOS, we rely on the system's built-in full-screen handling
        // for AVPlayerViewController

        isFullScreen = true
    }

    /**
     * Exit full-screen mode.
     * This will:
     * 1. Show the status bar
     * 2. Exit the player view controller's full-screen mode
     */
    override fun exitFullScreen() {
        // Show the status bar
        UIApplication.sharedApplication.setStatusBarHidden(false, animated = true)

        // In iOS, we rely on the system's built-in full-screen handling
        // for AVPlayerViewController

        isFullScreen = false
    }

    /**
     * Toggle full-screen mode.
     */
    override fun toggleFullScreen() {
        if (isFullScreen) {
            exitFullScreen()
        } else {
            enterFullScreen()
        }
    }

    /**
     * Check if the device is in landscape orientation.
     */
    override fun isLandscape(): Boolean {
        // In iOS, we can simply check if the player is in full-screen mode
        // since we're enforcing landscape orientation in full-screen mode
        return isFullScreen
    }

    /**
     * Check if the device is in portrait orientation.
     */
    override fun isPortrait(): Boolean {
        // In iOS, we can simply check if the player is not in full-screen mode
        // since we're enforcing portrait orientation when not in full-screen mode
        return !isFullScreen
    }

    /**
     * Check if the device is in full-screen mode.
     */
    override fun isFullScreen(): Boolean {
        return isFullScreen
    }

    override fun hideSystemUI() {
        UIApplication.sharedApplication.setStatusBarHidden(false, animated = true)
    }

    override fun showSystemUI() {
        UIApplication.sharedApplication.setStatusBarHidden(true, animated = true)
    }

    companion object {
        // Singleton instance
        private val INSTANCE = IOSScreenOrientationUtils()

        fun getInstance(): IOSScreenOrientationUtils {
            return INSTANCE
        }
    }
}

/**
 * Get the platform-specific implementation of ScreenOrientationUtils.
 */
actual fun getScreenOrientationUtils(): ScreenOrientationUtils {
    return IOSScreenOrientationUtils.getInstance()
}
