package tss.t.tsiptv

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.core.network.NetworkConnectivityCheckerFactory
import tss.t.tsiptv.core.permission.PermissionCheckerFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize the application context
        AndroidPlatformUtils.appContext = applicationContext

        // Initialize the permission checker factory
        PermissionCheckerFactory.initialize(applicationContext, this)

        // Initialize the network connectivity checker factory
        NetworkConnectivityCheckerFactory.initialize(applicationContext as Application)

        setContent {
            App()
        }
    }
}

// Preview removed as it requires Koin initialization
// Use the actual app for testing
