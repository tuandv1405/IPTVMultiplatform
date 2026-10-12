package tss.t.tsiptv.utils

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNumber
import platform.Foundation.setValue
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskAllButUpsideDown
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIViewController
import platform.UIKit.attemptRotationToDeviceOrientation
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

/**
 * The orientations the app allows right now. The app delegate returns it from
 * `application(_:supportedInterfaceOrientationsFor:)` (see iOSApp.swift).
 */
object IosOrientationLock {
    var mask: UIInterfaceOrientationMask = UIInterfaceOrientationMaskAllButUpsideDown
}

/**
 * iOS implementation of ScreenOrientationUtils: full screen turns the phone to landscape and
 * leaving it turns it back to portrait, as on Android. In landscape iOS hides the status bar
 * on its own.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSScreenOrientationUtils : ScreenOrientationUtils {
    private var isFullScreen = false

    override fun setPlayerViewController(viewController: Any?) {}

    override fun enterFullScreen() {
        applyOrientation(UIInterfaceOrientationMaskLandscape)
        isFullScreen = true
    }

    override fun exitFullScreen() {
        applyOrientation(UIInterfaceOrientationMaskPortrait)
        isFullScreen = false
    }

    override fun toggleFullScreen() {
        if (isFullScreen) exitFullScreen() else enterFullScreen()
    }

    override fun isLandscape(): Boolean = isFullScreen

    override fun isPortrait(): Boolean = !isFullScreen

    override fun isFullScreen(): Boolean = isFullScreen

    override fun hideSystemUI() {}

    override fun showSystemUI() {}

    override fun lockLandscape(locked: Boolean) {
        if (locked) {
            applyOrientation(UIInterfaceOrientationMaskLandscape)
        } else {
            // iOS does not turn back on its own when the lock is lifted (Android follows the
            // sensor): go to portrait, then let the device rotate freely again.
            applyOrientation(UIInterfaceOrientationMaskPortrait)
            // The geometry update is asynchronous: free the mask once the rotation has run.
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, UNLOCK_DELAY_NS), dispatch_get_main_queue()) {
                if (IosOrientationLock.mask == UIInterfaceOrientationMaskPortrait && !isFullScreen) {
                    IosOrientationLock.mask = UIInterfaceOrientationMaskAllButUpsideDown
                }
            }
        }
    }

    private fun applyOrientation(mask: UIInterfaceOrientationMask) {
        IosOrientationLock.mask = mask
        val scene = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull()
        val majorVersion = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0
        if (majorVersion >= 16 && scene != null) {
            scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            scene.requestGeometryUpdateWithPreferences(
                UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
                errorHandler = null,
            )
        } else {
            // iOS 15: the private-but-documented-by-practice device orientation key.
            val orientation = if (mask == UIInterfaceOrientationMaskLandscape) {
                UIInterfaceOrientationLandscapeRight
            } else {
                UIInterfaceOrientationPortrait
            }
            UIDevice.currentDevice.setValue(NSNumber(long = orientation), forKey = "orientation")
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }

    companion object {
        private const val UNLOCK_DELAY_NS = 600_000_000L

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
