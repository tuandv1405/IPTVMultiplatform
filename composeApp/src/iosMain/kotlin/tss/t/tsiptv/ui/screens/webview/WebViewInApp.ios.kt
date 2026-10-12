package tss.t.tsiptv.ui.screens.webview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.navigation.NavHostController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURL
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKPreferences
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled
import platform.darwin.NSObject
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKScriptMessage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS implementation of WebViewInApp
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun WebViewInApp(
    url: String,
    onClose: () -> Unit,
    jsBridge: WebViewJSBridge?
) {
    // Create a WKWebViewConfiguration
    val configuration = remember { WKWebViewConfiguration() }
    val preferences = remember { WKPreferences() }
    val contentController = remember { WKUserContentController() }

    // Enable JavaScript
    preferences.javaScriptEnabled = true
    configuration.preferences = preferences
    configuration.userContentController = contentController

    // Create a script message handler for the bridge
    val messageHandler = remember {
        jsBridge?.let { bridge ->
            IOSScriptMessageHandler(bridge, onClose)
        }
    }

    // Add the script message handler to the content controller
    DisposableEffect(messageHandler) {
        if (messageHandler != null) {
            contentController.addScriptMessageHandler(messageHandler, "nativeBridge")

            // Inject JavaScript to expose the bridge methods
            val jsInjection = """
                window.NativeApp = {
                    openDialog: function(message) {
                        window.webkit.messageHandlers.nativeBridge.postMessage({
                            method: 'openDialog',
                            params: { message: message }
                        });
                    },
                    close: function() {
                        window.webkit.messageHandlers.nativeBridge.postMessage({
                            method: 'close',
                            params: {}
                        });
                    },
                    getAccessToken: function() {
                        // This is synchronous in our interface but WKWebView bridge is asynchronous
                        // For a real implementation, you'd need to use callbacks or promises
                        window.webkit.messageHandlers.nativeBridge.postMessage({
                            method: 'getAccessToken',
                            params: {}
                        });
                        return 'sample-token'; // Placeholder
                    }
                };
            """.trimIndent()

            val userScript = WKUserScript(
                source = jsInjection,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                forMainFrameOnly = true
            )

            contentController.addUserScript(userScript)

            onDispose {
                contentController.removeScriptMessageHandlerForName("nativeBridge")
            }
        } else {
            onDispose { }
        }
    }

    // Create a navigation delegate
    val navigationDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            // Implement navigation delegate methods if needed
        }
    }

    // Create the WKWebView and load the URL
    UIKitView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = configuration
            ).apply {
                this.navigationDelegate = navigationDelegate
                // A malformed link shows a blank page instead of crashing.
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest(uRL = it)) }
            }
        },
        update = { webView ->
            // Update logic if needed
        },
        onRelease = { webView ->
            // Clean up resources
            webView.stopLoading()
        }
    )
}

/**
 * iOS-specific script message handler for the JavaScript bridge
 */
@OptIn(ExperimentalForeignApi::class)
private class IOSScriptMessageHandler(
    private val bridge: WebViewJSBridge,
    private val onClose: () -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        // postMessage({ method, params }) arrives as an NSDictionary.
        val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
        val params = body["params"] as? Map<*, *>
        when (body["method"] as? String) {
            "openDialog" -> {
                val message = params?.get("message") as? String ?: ""
                dispatch_async(dispatch_get_main_queue()) {
                    bridge.openDialog(message)
                }
            }
            "close" -> {
                dispatch_async(dispatch_get_main_queue()) {
                    bridge.close()
                    onClose()
                }
            }
            "getAccessToken" -> {
                // This is handled synchronously in our interface
                // but WKWebView bridge is asynchronous
                // For a real implementation, you'd need to use callbacks or promises
                val token = bridge.getAccessToken()
                // You would need to inject JavaScript to call a callback with the token
            }
        }
    }
}
