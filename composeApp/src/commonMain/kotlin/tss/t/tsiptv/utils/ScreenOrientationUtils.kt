package tss.t.tsiptv.utils

/**
 * Common interface for screen orientation utilities.
 * This interface defines the basic functionality that all platform-specific
 * implementations must provide.
 */
interface ScreenOrientationUtils {
    /**
     * Set the player view controller that will be used for full-screen mode.
     * This is only used on iOS, but is included in the interface for consistency.
     * 
     * @param viewController The player view controller, or null to clear it.
     */
    fun setPlayerViewController(viewController: Any?)
    /**
     * Enter full-screen mode.
     */
    fun enterFullScreen()

    /**
     * Exit full-screen mode.
     */
    fun exitFullScreen()

    /**
     * Toggle full-screen mode.
     */
    fun toggleFullScreen()

    /**
     * Check if the device is in landscape orientation.
     */
    fun isLandscape(): Boolean

    /**
     * Check if the device is in portrait orientation.
     */
    fun isPortrait(): Boolean

    /**
     * Check if the device is in full-screen mode.
     */
    fun isFullScreen(): Boolean

    fun hideSystemUI()

    fun showSystemUI()
}

/**
 * Get the platform-specific implementation of ScreenOrientationUtils.
 */
expect fun getScreenOrientationUtils(): ScreenOrientationUtils
