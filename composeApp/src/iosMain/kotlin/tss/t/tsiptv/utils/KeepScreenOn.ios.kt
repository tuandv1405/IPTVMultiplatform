package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * iOS implementation of KeepScreenOn.
 * Uses UIApplication.sharedApplication.idleTimerDisabled to keep the screen on.
 */
@Composable
actual fun KeepScreenOn(keepScreenOn: Boolean) {
    DisposableEffect(keepScreenOn) {
        val application = UIApplication.sharedApplication
        val previousValue = application.idleTimerDisabled

        application.idleTimerDisabled = keepScreenOn

        onDispose {
            // Restore previous value when the composable is disposed
            application.idleTimerDisabled = previousValue
        }
    }
}
