package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState

/**
 * Utility to keep the screen on when a condition is met.
 * This is implemented using expect/actual to provide platform-specific implementations.
 */
@Composable
expect fun KeepScreenOn(keepScreenOn: Boolean)

/**
 * Utility to keep the screen on when a condition is met.
 * This version takes a State<Boolean> parameter for reactive updates.
 */
@Composable
fun KeepScreenOnState(keepScreenOn: State<Boolean>) {
    val currentKeepScreenOn = rememberUpdatedState(keepScreenOn.value)

    KeepScreenOn(currentKeepScreenOn.value)
}
