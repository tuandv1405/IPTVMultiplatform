package tss.t.tsiptv.utils

/**
 * Desktop implementation of ScreenOrientationUtils.
 */
class DesktopScreenOrientationUtils : ScreenOrientationUtils {
    private var isFullScreen = false
    override fun setPlayerViewController(viewController: Any?) {

    }

    /**
     * Enter full-screen mode.
     */
    override fun enterFullScreen() {
        // Desktop implementation would use JFrame or similar to go full-screen
        // For now, just update the state
        isFullScreen = true
    }
    
    /**
     * Exit full-screen mode.
     */
    override fun exitFullScreen() {
        // Desktop implementation would use JFrame or similar to exit full-screen
        // For now, just update the state
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
     * Desktop doesn't have orientation in the same way as mobile devices,
     * but we can consider full-screen mode as "landscape".
     */
    override fun isLandscape(): Boolean {
        return isFullScreen
    }
    
    /**
     * Check if the device is in portrait orientation.
     * Desktop doesn't have orientation in the same way as mobile devices,
     * but we can consider non-full-screen mode as "portrait".
     */
    override fun isPortrait(): Boolean {
        return !isFullScreen
    }
    
    /**
     * Check if the device is in full-screen mode.
     */
    override fun isFullScreen(): Boolean {
        return isFullScreen
    }

    override fun hideSystemUI() {
        // No-op for desktop
    }

    override fun showSystemUI() {
        // No-op for desktop
    }
    
    companion object {
        // Singleton instance
        private val INSTANCE = DesktopScreenOrientationUtils()
        
        fun getInstance(): DesktopScreenOrientationUtils {
            return INSTANCE
        }
    }
}

/**
 * Get the platform-specific implementation of ScreenOrientationUtils.
 */
actual fun getScreenOrientationUtils(): ScreenOrientationUtils {
    return DesktopScreenOrientationUtils.getInstance()
}