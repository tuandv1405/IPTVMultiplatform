package tss.t.tsiptv

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin
import tss.t.tsiptv.core.firebase.DesktopFirebaseInitializer
import tss.t.tsiptv.di.getCommonModules
import tss.t.tsiptv.di.getDesktopModules

fun main() {
    // Initialize Firebase
    DesktopFirebaseInitializer.initialize()

    // Initialize Koin
    startKoin {
        modules(getCommonModules() + getDesktopModules())
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "tsiptv",
        ) {
            App()
        }
    }
}
