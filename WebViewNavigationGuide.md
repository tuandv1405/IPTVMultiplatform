# How to Navigate to WebView with a Specific URL

This guide explains how to navigate to the WebView component with a specific URL in the TSIPTV application.

## Overview

The application uses Compose Navigation to handle navigation between screens, including the WebView. The WebView component is designed to display web content from a specified URL. There are two main ways to navigate to the WebView with a specific URL:

1. Using the `createWebViewRoute` helper function
2. Using the `WebViewParams` object

## Method 1: Using the `createWebViewRoute` Helper Function (Recommended)

This is the recommended approach as it uses the navigation system's built-in argument passing mechanism.

### Step 1: Make sure you have access to the NavRoutes class

You'll need access to the NavRoutes class in your composable.

### Step 2: Create a route with your URL

Use the `createWebViewRoute` helper function to create a route with your URL:

```kotlin
val router = NavRoutes.createWebViewRoute("https://example.com/your-url")
```

### Step 3: Navigate to the route

Use the NavController to navigate to the route:

```kotlin
navController.navigate(router) {
    // Optional navigation options
    launchSingleTop = true
}
```

### Complete Example

```kotlin
// In your composable
Button(
    onClick = {
        val router = NavRoutes.createWebViewRoute("https://example.com/your-url")
        navController.navigate(router) {
            launchSingleTop = true
        }
    }
) {
    Text("Open WebView")
}
```

## Method 2: Using the WebViewParams Object

This method is useful if you need to set the URL before navigating, or if you need to pass additional parameters that aren't part of the route.

### Step 1: Make sure you have access to the necessary classes

You'll need access to both the NavRoutes class and the WebViewParams object in your composable.

### Step 2: Set the URL in WebViewParams

```kotlin
WebViewParams.url = "https://example.com/your-url"
```

### Step 3: Navigate to the WebView route

```kotlin
navController.navigate(NavRoutes.WEBVIEW) {
    // Optional navigation options
    launchSingleTop = true
}
```

### Complete Example

```kotlin
// In your composable
Button(
    onClick = {
        WebViewParams.url = "https://example.com/your-url"
        navController.navigate(NavRoutes.WEBVIEW) {
            launchSingleTop = true
        }
    }
) {
    Text("Open WebView")
}
```

## How It Works

1. The `NavRoutes.WEBVIEW` constant is defined as `"web_view/{url}"`, which includes a URL parameter.
2. The `createWebViewRoute` function creates a route with the URL parameter: `"web_view/$url"`.
3. When navigating to this route, the URL parameter is extracted in the App.kt file and passed to the WebViewInApp component.
4. The WebViewInApp component uses this URL to load the web content.

## Edge Cases and Considerations

1. **URL Encoding**: If your URL contains special characters, make sure to encode it properly to avoid navigation issues:

```kotlin
// Use URLEncoder to encode the URL
val encodedUrl = URLEncoder.encode("https://example.com/path with spaces", "UTF-8")
val router = NavRoutes.createWebViewRoute(encodedUrl)
```

Note: You'll need to import `java.net.URLEncoder` in your file.

2. **Default URL**: If the URL parameter is missing or if there's an exception, a default URL ("https://example.com") will be used.

3. **Platform Differences**: The WebView implementation differs across platforms:
   - Android: Uses Android's WebView component
   - iOS: Uses WKWebView
   - Desktop: Opens URLs in the system browser

## Real-World Example

Here's a real example from the HomeFeedScreen.kt file:

```kotlin
Text(
    stringResource(Res.string.iptv_help_title),
    style = MaterialTheme.typography.titleLarge
        .copy(
            color = TSColors.TextSecondary,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            textDecoration = TextDecoration.Underline
        ),
    modifier = Modifier.clickable {
        // Set the URL parameter before navigating
        val router = NavRoutes.createWebViewRoute("https://dvt1405.github.io/iMediaReleasePages/")
        parentNavController.navigate(router) {
            launchSingleTop = true
        }
    }
)
```

## Conclusion

By following this guide, you can easily navigate to the WebView component with a specific URL in your application. The recommended approach is to use the `createWebViewRoute` helper function, but you can also use the `WebViewParams` object if needed.
