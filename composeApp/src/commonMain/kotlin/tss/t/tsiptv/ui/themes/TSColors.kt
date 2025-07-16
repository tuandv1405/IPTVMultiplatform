package tss.t.tsiptv.ui.themes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TSColors {
    val PlayerBackgroundColor = Color.Black
    val SecondaryBackgroundColor = Color(0xFF161A32)
    val DeepBlue = Color(0xFF03041D)

    val BackgroundColor = DeepBlue
    val GradientBlue = Color(0xFF3B82F6)
    val GradientGreen = Color(0xFF10B981)

    val AccentGreen = Color(0xFF00F5A0)
    val AccentCyan = Color(0xFF00D9E9)
    val TextFieldBackground = Color(0xFF0D0F24)

    val TextGray = Color(0xFF8E8E93)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextSecondaryLight = Color(0xFFE5E7EB)
    val TextPrimary = Color.White
    val TextTitlePrimaryDart = Color(0xFF111827)
    val TextBodyPrimaryDart = Color(0xFF4B5563)

    val ButtonBackground = Color(0xFF2C2C2E)
    val ButtonBackgroundBlue = Color(0xFF2D3238)

    val baseGradient = Brush.horizontalGradient(
        listOf(
            GradientBlue,
            GradientGreen
        )
    )

    val backgroundGradientColor1 = Color(0xFF0C0D2C)
    val backgroundGradientColor2 = BackgroundColor
    val backgroundGradientMain = Brush.horizontalGradient(
        listOf(
            backgroundGradientColor1,
            backgroundGradientColor2
        )
    )

    val loginBackgroundGradientColors: List<Color> = listOf(
        backgroundGradientColor2,
        backgroundGradientColor1
    )

    val loginBrush = Brush.linearGradient(
        colors = loginBackgroundGradientColors
    )

    val OnSurface = Color(0xFF161A32)

    val RedNotify = Color(0xFFEF4444)
    val ErrorRed = Color(0xFFF87171)
    val ActiveGreen = Color(0xFF34D399)
    val LoadingYellow = Color(0xFFFBBF24)

    val strokeWhite = Color(0xFFF3F4F6)
    val strokeColor = Color(0xFF1F2937)
    val IconContainerColor = Color(0xFF4B5563)
    val IconOnlyColor = TextSecondary
}