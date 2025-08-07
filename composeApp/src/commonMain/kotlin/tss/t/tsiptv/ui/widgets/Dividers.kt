package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.tsiptv.ui.themes.TSColors

object Dividers {
    val horizontalDividers = Brush.horizontalGradient(
        listOf(
            TSColors.GradientGreen.copy(0.2f),
            TSColors.GradientBlue.copy(0.4f),
            TSColors.GradientGreen.copy(0.2f),
        )
    )
}

@Composable
fun HorizontalDividersGradient(
    modifier: Modifier = Modifier,
    height: Dp = 1.dp
) {
    Box(
        modifier = modifier.fillMaxWidth()
            .height(height)
            .background(
                brush = Dividers.horizontalDividers
            ),
    )
}
