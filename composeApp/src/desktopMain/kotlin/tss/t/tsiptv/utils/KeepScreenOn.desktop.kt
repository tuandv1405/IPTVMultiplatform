package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable

/**
 * Desktop implementation of KeepScreenOn.
 * This is a no-op implementation as desktop platforms don't typically have a standard way
 * to keep the screen on through Compose.
 */
@Composable
actual fun KeepScreenOn(keepScreenOn: Boolean) {
    // No-op implementation for desktop
}