package tss.t.tsiptv.utils

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Android implementation of KeepScreenOn.
 * Uses the WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON flag to keep the screen on.
 */
@Composable
actual fun KeepScreenOn(keepScreenOn: Boolean) {
    val view = LocalView.current
    
    DisposableEffect(keepScreenOn) {
        val window = view.context.getWindow()
        if (keepScreenOn) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

// Extension function to get the window from the context
private fun android.content.Context.getWindow(): android.view.Window? {
    return when (this) {
        is android.app.Activity -> this.window
        is android.content.ContextWrapper -> this.baseContext.getWindow()
        else -> null
    }
}