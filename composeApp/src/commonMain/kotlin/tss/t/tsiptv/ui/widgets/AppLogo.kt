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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tss.t.tsiptv.ui.themes.TSColors

val LogoShape = RoundedCornerShape(24.dp)

@Composable
fun AppLogo() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .shadow(
                elevation = 20.dp,
                shape = LogoShape,
                spotColor = TSColors.GradientGreen
            )
            .clip(LogoShape)
            .background(TSColors.baseGradient),
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
fun AppLogoCircle() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                spotColor = TSColors.GradientGreen
            )
            .clip(LogoShape)
            .background(TSColors.baseGradient),
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

