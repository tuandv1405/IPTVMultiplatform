package tss.t.tsiptv

import androidx.compose.ui.window.ComposeUIViewController
import tss.t.tsiptv.di.IosKoinInitializer

// Initialize Koin when the module is loaded
private val koinInitializer = IosKoinInitializer.initialize()

fun MainViewController() = ComposeUIViewController { App() }
