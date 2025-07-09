package tss.t.tsiptv.ui.themes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TSColors {
    val SecondaryBackgroundColor = Color(0xFF161A32)
    val BackgroundColor = Color(0xFF03041D)
    val GradientBlue = Color(0xFF3B82F6)
    val GradientGreen = Color(0xFF10B981)

    val TextGray = Color(0xFF8E8E93)
    val TextSecondary = Color(0xFF9CA3AF)

    val ButtonBackground = Color(0xFF2C2C2E)
    val ButtonBackgroundBlue = Color(0xFF2D3238)

    val baseGradient = Brush.horizontalGradient(
        listOf(
            GradientBlue,
            GradientGreen
        )
    )

    val backgroundGradientColor1 = Color(0xFF0C0D2C)
    val backgroundGradientColor2 = Color(0xFF03041D)
    val backgroundGradientMain = Brush.horizontalGradient(
        listOf(
            backgroundGradientColor1,
            backgroundGradientColor2
        )
    )

    val OnSurface = Color(0xFF161A32)

    val RedVibrant = Color(0xFFFF4757) //Red Orange
    val RedVibrantDark = Color(0xFFB00020) //Red Orange Dark
    val GreenVibrant = Color(0xFF43A047) //Green Orange
    val GreenVibrantDark = Color(0xFF00701A) //Green Orange Dark
    val BlueVibrant = Color(0xFF2196F3) //Blue Orange
    val BlueVibrantDark = Color(0xFF0069C0) //Blue Orange Dark
    val YellowVibrant = Color(0xFFFFEB3B) //Yellow Orange
    val YellowVibrantDark = Color(0xFFC7B900) //Yellow Orange Dark
    val PurpleVibrant = Color(0xFF9C27B0) //Purple Orange
    val PurpleVibrantDark = Color(0xFF6A0080) //Purple Orange Dark

    val strokeWhite = Color(0xFFF3F4F6)
}