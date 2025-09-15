package tss.t.tsiptv.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.tsiptv.ui.themes.TSColors

@Composable
fun PlayerProgress(
    progress: Float = 0.7f,
    onProgressChanged: (Float) -> Unit = {},
    progressHeigh: Dp = 10.dp,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val animateProgress = remember {
        Animatable(initialValue = progress)
    }
    val progressValue by animateProgress.asState()

    LaunchedEffect(progress) {
        animateProgress.animateTo(targetValue = 1f)
    }

    Canvas(
        modifier.height(progressHeigh)
            .pointerInput(Unit) {
                detectTapGestures {

                }
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onDragEnd = { },
                    onHorizontalDrag = { change, dragAmount ->
                        val original = progressValue
                        val newValue = (original + dragAmount / size.width).coerceIn(0f..1f)
                        onProgressChanged(newValue)
                        change.consume()
                    }
                )
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val progressWidth = canvasWidth * progressValue
        val path = Path().apply {
            this.fillType = PathFillType.EvenOdd
            moveTo(canvasWidth + canvasHeight / 2, canvasHeight / 2)
            arcTo(
                rect = Rect(
                    left = progressWidth - canvasHeight / 2,
                    top = 0f,
                    right = progressWidth + canvasHeight / 2,
                    bottom = canvasHeight
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(canvasWidth - canvasHeight / 2, 0f)
            lineTo(canvasWidth - canvasHeight / 2, canvasHeight)
            lineTo(progressWidth, canvasHeight)
            close()
            moveTo(canvasWidth - canvasHeight / 2, canvasHeight / 2)
            arcTo(
                rect = Rect(
                    left = canvasWidth - canvasHeight,
                    top = 0f,
                    right = canvasWidth,
                    bottom = canvasHeight
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    TSColors.GradientBlue,
                    TSColors.GradientGreen,
                )
            ),
            size = size,
            cornerRadius = CornerRadius(x = canvasHeight / 2, y = canvasHeight / 2)
        )
        if (progressValue < 1) {
            drawPath(path, color = Color(0xFF4B5563))
        }
    }
}
