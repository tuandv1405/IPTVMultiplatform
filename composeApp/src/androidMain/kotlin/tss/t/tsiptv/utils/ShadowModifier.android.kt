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

@Composable
actual fun Modifier.customShadow(
    borderRadius: Dp,
    blurRadius: Dp,
    offsetY: Dp,
    offsetX: Dp,
    color: Color
): Modifier {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val offsetXpx = with(density) { offsetX.toPx() }
    val offsetYpx = with(density) { offsetY.toPx() }
    val borderRadiusPx = with(density) { borderRadius.toPx() }

    val shadowPaint = Paint().asFrameworkPaint().apply {
        this.color = Color.Transparent.toArgb()
        this.isAntiAlias = true
        if (blurRadiusPx > 0) {
            this.maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    return this.drawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                0f + offsetXpx,
                0f + offsetYpx,
                this.size.width + offsetXpx,
                this.size.height + offsetYpx,
                borderRadiusPx,
                borderRadiusPx,
                android.graphics.Paint(shadowPaint).apply {
                    this.color = color.toArgb()
                }
            )
        }
    }
}
