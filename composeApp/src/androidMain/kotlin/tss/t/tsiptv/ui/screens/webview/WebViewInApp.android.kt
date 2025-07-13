package tss.t.tsiptv.ui.screens.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController

/**
 * Android implementation of WebViewInApp
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebViewInApp(
    url: String,
    onClose: () -> Unit,
    jsBridge: WebViewJSBridge?
) {
    val context = LocalContext.current
    
    // Create a WebView instance
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadsImagesAutomatically = true
            settings.supportMultipleWindows()
            settings.javaScriptCanOpenWindowsAutomatically = true
            
            // Add JavaScript interface if bridge is provided
            jsBridge?.let { bridge ->
                addJavascriptInterface(
                    AndroidJSBridge(context, bridge, onClose),
                    "NativeBridge"
                )
            }
            
            // Set WebViewClient to handle URL loading
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Handle URL loading within the WebView
                    return false
                }
                
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // Inject JavaScript to expose the bridge methods
                    val jsInjection = """
                        window.NativeApp = {
                            openDialog: function(message) {
                                NativeBridge.openDialog(message);
                            },
                            close: function() {
                                NativeBridge.close();
                            },
                            getAccessToken: function() {
                                return NativeBridge.getAccessToken();
                            }
                        };
                    """.trimIndent()
                    
                    view.evaluateJavascript(jsInjection, null)
                }
            }
            
            // Set WebChromeClient to handle JavaScript dialogs and other features
            webChromeClient = object : WebChromeClient() {
                // Handle JavaScript alerts, confirms, prompts, etc.
            }
            
            // Load the URL
            loadUrl(url)
        }
    }
    
    // Use AndroidView to embed the WebView in Compose
    AndroidView(
        factory = { webView },
        update = { it.loadUrl(url) }
    )
    
    // Clean up resources when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }
}

/**
 * Android-specific JavaScript bridge implementation
 */
private class AndroidJSBridge(
    private val context: Context,
    private val bridge: WebViewJSBridge,
    private val onClose: () -> Unit
) {
    @JavascriptInterface
    fun openDialog(message: String) {
        bridge.openDialog(message)
    }
    
    @JavascriptInterface
    fun close() {
        bridge.close()
        onClose()
    }
    
    @JavascriptInterface
    fun getAccessToken(): String {
        return bridge.getAccessToken()
    }
}