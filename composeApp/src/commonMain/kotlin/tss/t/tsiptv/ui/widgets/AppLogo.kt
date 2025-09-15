package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.utils.customShadow
import tss.t.tsiptv.utils.innerShadow

val LogoShape = RoundedCornerShape(24.dp)

@Composable
fun AppLogo() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .customShadow(
                blurRadius = 20.dp,
                offsetX = 3.dp,
                offsetY = 3.dp,
                borderRadius = 20.dp,
                color = TSColors.GradientGreen
            )
            .background(TSColors.baseGradient, LogoShape)
            .innerShadow(
                color = TSColors.GradientBlue.copy(alpha = 0.6f),
                blur = 10.dp,
                spread = 2.dp,
                offsetX = 2.dp,
                offsetY = 2.dp,
                shape = LogoShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = "App Logo",
            tint = Color.White,
            modifier = Modifier.size(50.dp)
        )
    }
}


@Composable
fun AppLogoCircle(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    iconSize: Dp = 50.dp,
    blurRadius: Dp = 20.dp,
    shape: Shape = LogoShape,
) {
    Box(
        modifier = modifier
            .size(size)
            .customShadow(
                blurRadius = blurRadius,
                offsetX = 3.dp,
                offsetY = 3.dp,
                color = TSColors.GradientGreen
            )
            .background(brush = TSColors.baseGradient, shape = shape)
            .innerShadow(
                color = TSColors.GradientGreen.copy(alpha = 0.7f),
                blur = 8.dp,
                spread = 1.dp,
                offsetX = (-2).dp,
                offsetY = (-2).dp,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = "App Logo",
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}
