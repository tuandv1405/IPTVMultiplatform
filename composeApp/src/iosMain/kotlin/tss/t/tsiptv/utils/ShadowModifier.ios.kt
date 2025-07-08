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
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.RRect
import org.jetbrains.skia.FilterBlurMode

@Composable
actual fun Modifier.customShadow(
    borderRadius: Dp,
    blurRadius: Dp,
    offsetY: Dp,
    offsetX: Dp,
    color: Color,
): Modifier {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val offsetXpx = with(density) { offsetX.toPx() }
    val offsetYpx = with(density) { offsetY.toPx() }
    val borderRadiusPx = with(density) { borderRadius.toPx() }

    return this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = color
            }
            val frameworkPaint = paint.asFrameworkPaint()

            if (blurRadiusPx > 0) {
                // Skia's blur is based on sigma, not radius. A common approximation is sigma = radius / 2.
                val sigma = blurRadiusPx / 2f
                frameworkPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
            }

            val rrect = RRect.makeXYWH(
                0f + offsetXpx,
                0f + offsetYpx,
                this.size.width,
                this.size.height,
                borderRadiusPx
            )
            canvas.nativeCanvas.drawRRect(rrect, frameworkPaint)
        }
    }
}
