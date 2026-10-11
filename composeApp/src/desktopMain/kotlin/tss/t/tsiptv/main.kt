package tss.t.tsiptv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.startKoin
import tss.t.tsiptv.core.firebase.DesktopFirebaseInitializer
import tss.t.tsiptv.di.getCommonModules
import tss.t.tsiptv.di.getDesktopModules
import tss.t.tsiptv.player.DesktopVlc
import tss.t.tsiptv.player.ui.DesktopKeyRouter
import tss.t.tsiptv.support.SupportHost
import tss.t.tsiptv.utils.AppFlags
import tss.t.tsiptv.utils.DesktopFullScreen
import tss.t.tsiptv.utils.DesktopScreenOrientationUtils

fun main() {
    // Before anything touches vlcj: the installer's own libVLC (Windows).
    DesktopVlc.useBundledLibVlc()

    // Firebase Auth is in memory only on desktop: no sign-in, the app works signed out.
    AppFlags.worksSignedOut = true
    // gitlive has no JVM Firebase Analytics (it throws): analytics calls are dropped here.
    AppFlags.analyticsAvailable = false

    // Initialize Firebase
    DesktopFirebaseInitializer.initialize()

    // Initialize Koin
    startKoin {
        modules(getCommonModules() + getDesktopModules())
    }

    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        Window(
            onCloseRequest = ::exitApplication,
            title = "TS IPTV",
            // Without it the title bar and the taskbar show Java's icon.
            icon = remember { appIcon() },
            state = windowState,
            // Player shortcuts (Space, Esc, F, M, arrows) wherever the focus is.
            onPreviewKeyEvent = DesktopKeyRouter::dispatch,
        ) {
            // The player's full screen button fills the screen (DesktopScreenOrientationUtils).
            val playerFullScreen by DesktopScreenOrientationUtils.getInstance().fullScreen.collectAsState()
            val fullScreen = remember { DesktopFullScreen(window, windowState) }
            LaunchedEffect(playerFullScreen) { fullScreen.set(playerFullScreen) }
            Box(Modifier.fillMaxSize()) {
                App()
                // Support promos' dialog and start screen: desktop's replacement for ads.
                SupportHost()
            }
        }
    }
}

/** brand/app-icon-512.png, copied into the desktop resources. */
private fun appIcon(): Painter? = runCatching {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")!!.use { it.readBytes() }
    BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()
