package tss.t.tsiptv.utils

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Android implementation of the blur modifier.
 * Uses Android's BlurMaskFilter to create a proper blur effect.
 *
 * @param blurRadius The radius of the blur effect. Higher values create a more intense blur.
 */
@Composable
actual fun Modifier.blur(blurRadius: Dp): Modifier {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }

    return this.drawBehind {
        // First draw a semi-transparent background to enhance the blur effect
        drawRect(
            color = Color.White.copy(alpha = 0.5f),
            size = this.size
        )

        // Then apply the blur effect
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                this.color = Color.Transparent.toArgb()
                this.isAntiAlias = true
                if (blurRadiusPx > 0) {
                    this.maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                }
            }

            // Draw a rectangle with the blur effect
            canvas.nativeCanvas.drawRect(
                0f,
                0f,
                this.size.width,
                this.size.height,
                android.graphics.Paint(paint).apply {
                    this.color = Color(0x33000000).toArgb() // Semi-transparent black
                }
            )
        }
    }
}
