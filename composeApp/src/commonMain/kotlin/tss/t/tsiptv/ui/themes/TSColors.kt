package tss.t.tsiptv.ui.themes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TSColors {
    val GradientBlue = Color(0xFF3B82F6)
    val GradientGreen = Color(0xFF10B981)

    val DarkBackground = Color(0xFF0A0A0B)
    val CardBackground = Color(0xFF1C1C1E)
    val CardBorderBlue = Color(0xFF2A62B8)
    val TextGray = Color(0xFF8E8E93)

    val ButtonBackground = Color(0xFF2C2C2E)
    val ButtonBackgroundBlue = Color(0xFF2D3238)

    val baseGradient = Brush.horizontalGradient(
        listOf(
            GradientBlue,
            GradientGreen
        )
    )
}