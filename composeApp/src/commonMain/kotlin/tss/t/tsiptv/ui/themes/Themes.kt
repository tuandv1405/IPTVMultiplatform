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
            primary = Color(0xFF4DD0E1),
            primaryContainer = Color(0xFF1A1A2E),
            surface = Color(0xFF1A1D21),
            onPrimary = Color.Black,
            background = TSColors.BackgroundColor,
            secondaryContainer = TSColors.SecondaryBackgroundColor,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        typography = MaterialTheme.typography.copy()
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}