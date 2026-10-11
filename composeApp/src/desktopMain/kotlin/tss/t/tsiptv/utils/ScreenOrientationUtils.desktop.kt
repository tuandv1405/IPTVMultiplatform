package tss.t.tsiptv.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop implementation of ScreenOrientationUtils. The player's full screen is the window's: main.kt
 * follows [fullScreen] and switches the window placement.
 */
class DesktopScreenOrientationUtils : ScreenOrientationUtils {
    private val _fullScreen = MutableStateFlow(false)

    /** Whether the player asked for full screen; the window follows it. */
    val fullScreen: StateFlow<Boolean> = _fullScreen.asStateFlow()

    override fun setPlayerViewController(viewController: Any?) {

    }

    override fun enterFullScreen() {
        _fullScreen.value = true
    }

    override fun exitFullScreen() {
        _fullScreen.value = false
    }

    override fun toggleFullScreen() {
        _fullScreen.value = !_fullScreen.value
    }

    /**
     * Desktop has no orientation; full screen counts as landscape.
     */
    override fun isLandscape(): Boolean = _fullScreen.value

    override fun isPortrait(): Boolean = !_fullScreen.value

    override fun isFullScreen(): Boolean = _fullScreen.value

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
