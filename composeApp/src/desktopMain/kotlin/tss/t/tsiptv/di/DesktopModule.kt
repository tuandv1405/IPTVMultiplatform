package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.dsl.module
import tss.t.tsiptv.core.player.IMediaPlayer
import tss.t.tsiptv.core.player.SimpleIMediaPlayer

/**
 * Desktop-specific module for dependencies
 */
val desktopModule = module {
    // Desktop-specific dependencies
    single<IMediaPlayer> { SimpleIMediaPlayer() }
}

/**
 * Function to get all desktop modules
 */
fun getDesktopModules(): List<Module> = listOf(desktopModule)