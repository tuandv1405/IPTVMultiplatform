package tss.t.tsiptv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin
import tss.t.tsiptv.core.firebase.DesktopFirebaseInitializer
import tss.t.tsiptv.di.getCommonModules
import tss.t.tsiptv.di.getDesktopModules
import tss.t.tsiptv.player.DesktopVlc
import tss.t.tsiptv.support.SupportHost
import tss.t.tsiptv.utils.AppFlags

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
        Window(
            onCloseRequest = ::exitApplication,
            title = "TS IPTV",
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
        ) {
            Box(Modifier.fillMaxSize()) {
                App()
                // Support promos' dialog and start screen: desktop's replacement for ads.
                SupportHost()
            }
        }
    }
}
