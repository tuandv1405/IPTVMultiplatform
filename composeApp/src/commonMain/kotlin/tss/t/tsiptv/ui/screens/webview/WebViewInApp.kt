package tss.t.tsiptv.ui.screens.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

/**
 * Companion object to store the URL parameter for the WebView
 * 
 * Usage:
 * ```
 * // Set the URL parameter before navigating
 * WebViewParams.url = "https://example.com/your-url"
 * navController.navigate(NavRoutes.WEBVIEW)
 * ```
 */
object WebViewParams {
    /**
     * The URL to load in the WebView
     * Set this value before navigating to the WebView screen
     */
    var url: String = "https://example.com"
}

/**
 * JavaScript bridge interface for WebView
 * Provides common functionality for all platforms
 */
interface WebViewJSBridge {
    /**
     * Show a dialog with the given message
     */
    fun openDialog(message: String)

    /**
     * Close the WebView
     */
    fun close()

    /**
     * Get the access token for the current user
     */
    fun getAccessToken(): String
}

/**
 * A multiplatform WebView component
 *
 * @param url The URL to load in the WebView
 * @param onClose Callback when the WebView is closed
 * @param jsBridge Optional JavaScript bridge implementation
 */
@Composable
expect fun WebViewInApp(
    url: String,
    onClose: () -> Unit = {},
    jsBridge: WebViewJSBridge? = null,
)

/**
 * Default implementation of WebViewInApp that uses the platform-specific implementation
 */
@Composable
fun WebViewInApp(
    pageUrl: String = WebViewParams.url,
    navController: NavHostController,
) {
    // Use the URL from the parameter or from WebViewParams

    // Create a simple JS bridge implementation
    val jsBridge = remember {
        object : WebViewJSBridge {
            override fun openDialog(message: String) {
                println("Dialog opened with message: $message")
            }

            override fun close() {
                println("WebView closed")
            }

            override fun getAccessToken(): String {
                return "sample-token"
            }
        }
    }

    WebViewInApp(
        url = pageUrl,
        onClose = {
            navController.popBackStack()
        },
        jsBridge = jsBridge
    )
}
