# WebView Implementation for Compose Multiplatform

This document describes the implementation of a WebView component for Compose Multiplatform that works across Android, iOS, and Desktop platforms.

## Overview

The WebView implementation provides:

1. **Android**: In-app WebView with JavaScript interface, WebViewClient, and WebChromeClient
2. **iOS**: In-app WKWebView with JavaScript bridge
3. **Desktop**: Opens URLs in the system browser

## Usage

### Basic Usage

```kotlin
// In your composable
WebViewInApp(
    url = "https://example.com",
    onClose = { /* Handle close event */ }
)
```

### With JavaScript Bridge

```kotlin
// Create a JavaScript bridge implementation
val jsBridge = remember {
    object : WebViewJSBridge {
        override fun openDialog(message: String) {
            // Show a dialog with the message
        }
        
        override fun close() {
            // Handle close event
        }
        
        override fun getAccessToken(): String {
            // Return the access token
            return "your-access-token"
        }
    }
}

// Use the WebView with the JavaScript bridge
WebViewInApp(
    url = "https://example.com",
    onClose = { /* Handle close event */ },
    jsBridge = jsBridge
)
```

### Navigation

The WebView is integrated into the navigation system using the `WEBVIEW` route:

```kotlin
// Navigate to the WebView
navController.navigate(NavRoutes.WEBVIEW)
```

To pass parameters to the WebView, you can use navigation arguments:

```kotlin
// Define the route with parameters
const val WEBVIEW = "web_view?url={url}"

// Navigate to the WebView with a specific URL
navController.navigate("web_view?url=https://example.com")
```

Then update the WebViewInApp composable to extract the URL from the navigation arguments:

```kotlin
@Composable
fun WebViewInApp() {
    val navBackStackEntry = LocalNavBackStackEntry.current
    val arguments = navBackStackEntry?.arguments
    val url = arguments?.getString("url") ?: "https://example.com"
    
    WebViewInApp(
        url = url,
        onClose = { /* Handle close event */ }
    )
}
```

## JavaScript Bridge

The JavaScript bridge provides a way for the web content to communicate with the native app. The following methods are available:

- `openDialog(message: String)`: Show a dialog with the given message
- `close()`: Close the WebView
- `getAccessToken(): String`: Get the access token for the current user

### Using the JavaScript Bridge in Web Content

```javascript
// Show a dialog
window.NativeApp.openDialog("Hello from WebView!");

// Close the WebView
window.NativeApp.close();

// Get the access token
const token = window.NativeApp.getAccessToken();
```

## Platform-Specific Details

### Android

- Uses Android's WebView component
- JavaScript is enabled
- DOM storage is enabled
- File access is allowed
- Content access is allowed
- Images are loaded automatically
- Multiple windows are supported
- JavaScript can open windows automatically

### iOS

- Uses WKWebView
- JavaScript is enabled
- Uses WKUserContentController for JavaScript bridge
- Injects JavaScript to expose bridge methods

### Desktop

- Opens URLs in the system browser
- Provides a button to reopen the URL if needed

## Extending the Implementation

To add more methods to the JavaScript bridge:

1. Add the method to the `WebViewJSBridge` interface in the common module
2. Implement the method in each platform-specific implementation
3. Update the JavaScript injection to expose the new method

Example:

```kotlin
// In the common module
interface WebViewJSBridge {
    // Existing methods
    fun openDialog(message: String)
    fun close()
    fun getAccessToken(): String
    
    // New method
    fun shareContent(content: String)
}

// In the platform-specific implementations
// Android
@JavascriptInterface
fun shareContent(content: String) {
    bridge.shareContent(content)
}

// iOS
// Update the message handler to handle the new method
// Update the JavaScript injection to expose the new method
```

## Conclusion

This WebView implementation provides a consistent way to display web content across Android, iOS, and Desktop platforms. It includes a JavaScript bridge for communication between the web content and the native app.