package tss.t.tsiptv.ui.themes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun StreamVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4DD0E1), // A teal/cyan color
            background = Color(0xFF121212),
            surface = Color(0xFF1A1D21),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

//// --- Color Palette based on your design ---
//val DarkBackground = Color(0xFF0A0A0B)
//val CardBackground = Color(0xFF1C1C1E)
//val CardBorderBlue = Color(0xFF2A62B8)
//val ButtonBackground = Color(0xFF2C2C2E)
//val TextGray = Color(0xFF8E8E93)
//
//// Gradient TSColors
//val GradientBlue = Color(0xFF2979FF)
//val GradientGreen = Color(0xFF00E676)
