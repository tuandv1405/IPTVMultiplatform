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

/**
 * iOS implementation of the blur modifier.
 * Uses Skia's blur filter to create a blur effect that closely resembles iOS-style blur.
 * 
 * This implementation mimics the iOS UIVisualEffectView with UIBlurEffect
 * by using a combination of translucent background and Skia's blur filter with
 * optimized parameters for iOS-like appearance.
 *
 * @param blurRadius The radius of the blur effect. Higher values create a more intense blur.
 */
@Composable
actual fun Modifier.blur(blurRadius: Dp): Modifier {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }

    // iOS blur effect is a sophisticated combination of translucency, blur, and subtle lighting effects
    return this.drawBehind {
        // First draw a translucent background to mimic the frosted glass effect
        drawRect(
            color = Color(0x40000000), // Semi-transparent black for dark blur
            alpha = 0.7f // iOS blur has a specific level of translucency
        )

        // Apply the primary blur effect
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = Color(0x30FFFFFF) // Translucent white for the main blur
            }
            val frameworkPaint = paint.asFrameworkPaint()

            if (blurRadiusPx > 0) {
                // Use a carefully tuned sigma for an iOS-like blur
                val sigma = blurRadiusPx / 2.5f
                // SOLID blur mode gives the most iOS-like effect
                frameworkPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.SOLID, sigma)
            }

            // Draw a rectangle covering the entire component
            val rect = org.jetbrains.skia.Rect.makeXYWH(
                0f, 0f, this.size.width, this.size.height
            )
            canvas.nativeCanvas.drawRect(rect, frameworkPaint)
        }

        // Add a subtle highlight layer to enhance the glass-like appearance
        drawIntoCanvas { canvas ->
            val highlightPaint = Paint().apply {
                this.color = Color(0x08FFFFFF) // Very subtle white highlight
            }
            val frameworkPaint = highlightPaint.asFrameworkPaint()

            if (blurRadiusPx > 0) {
                val sigma = blurRadiusPx / 4f
                frameworkPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.SOLID, sigma)
            }

            // Draw the highlight at the top portion
            val rect = org.jetbrains.skia.Rect.makeXYWH(
                0f, 0f, this.size.width, this.size.height / 2
            )
            canvas.nativeCanvas.drawRect(rect, frameworkPaint)
        }
    }
}
