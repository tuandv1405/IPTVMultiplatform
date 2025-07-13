package tss.t.tsiptv.ui.screens.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import java.awt.Desktop
import java.net.URI

/**
 * Desktop implementation of WebViewInApp
 * For desktop, we open the URL in the system browser instead of an in-app WebView
 */
@Composable
actual fun WebViewInApp(
    url: String,
    onClose: () -> Unit,
    jsBridge: WebViewJSBridge?,
) {
    // Open the URL in the system browser when the composable is first launched
    LaunchedEffect(url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                    .isSupported(Desktop.Action.BROWSE)
            ) {
                Desktop.getDesktop().browse(URI(url))
                // Call onClose after opening the browser
                onClose()
            }
        } catch (e: Exception) {
            println("Error opening URL in browser: ${e.message}")
        }
    }

    // Show a simple UI with a button to reopen the URL if needed
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                            .isSupported(Desktop.Action.BROWSE)
                    ) {
                        Desktop.getDesktop().browse(URI(url))
                    }
                } catch (e: Exception) {
                    println("Error opening URL in browser: ${e.message}")
                }
            }
        ) {
            Text("Open in Browser")
        }
    }
}