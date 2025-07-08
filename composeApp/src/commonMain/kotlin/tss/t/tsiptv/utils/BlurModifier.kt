package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A custom blur modifier that applies a blur effect to the background.
 * This is particularly useful for creating iOS-style blur effects on UI components
 * like navigation bars and app bars.
 *
 * @param blurRadius The radius of the blur effect. Higher values create a more intense blur.
 */
@Composable
expect fun Modifier.blur(
    blurRadius: Dp = 10.dp
): Modifier