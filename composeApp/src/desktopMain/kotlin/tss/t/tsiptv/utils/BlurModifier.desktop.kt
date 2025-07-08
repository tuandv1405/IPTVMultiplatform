package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Rect

/**
 * Desktop implementation of the blur modifier.
 * Uses Skia's blur filter to create a blur effect.
 *
 * @param blurRadius The radius of the blur effect. Higher values create a more intense blur.
 */
@Composable
actual fun Modifier.blur(blurRadius: Dp): Modifier {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }

    return this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = Color.White.copy(alpha = 0.5f) // Semi-transparent white for light blur
            }
            val frameworkPaint = paint.asFrameworkPaint()

            if (blurRadiusPx > 0) {
                // Skia's blur is based on sigma, not radius. A common approximation is sigma = radius / 2.
                val sigma = blurRadiusPx / 2f
                frameworkPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
            }

            // Draw a rectangle covering the entire component
            val rect = Rect.makeXYWH(
                0f, 0f, this.size.width, this.size.height
            )
            canvas.nativeCanvas.drawRect(rect, frameworkPaint)
        }
    }
}