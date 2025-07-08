package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A custom shadow modifier that allows for precise control over the shadow's
 * properties, including color, offset, and blur. This is a platform-independent
 * modifier that uses expect/actual to provide the correct drawing logic.
 *
 * @param borderRadius The corner radius of the shadow shape.
 * @param blurRadius The blur radius of the shadow.
 * @param offsetY The vertical offset of the shadow.
 * @param offsetX The horizontal offset of the shadow.
 * @param color The color of the shadow.
 */
@Composable
expect fun Modifier.customShadow(
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    color: Color = Color.Black.copy(alpha = 0.5f)
): Modifier