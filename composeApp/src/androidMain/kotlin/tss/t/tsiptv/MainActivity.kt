package tss.t.tsiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize the application context
        AndroidPlatformUtils.appContext = applicationContext

        setContent {
            App()
        }
    }
}

// Preview removed as it requires Koin initialization
// Use the actual app for testing
