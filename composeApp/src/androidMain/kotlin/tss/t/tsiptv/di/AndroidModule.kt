package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.dsl.module
import tss.t.tsiptv.core.player.IMediaPlayer
import tss.t.tsiptv.core.player.SimpleIMediaPlayer

/**
 * Android-specific module for dependencies
 */
val androidModule = module {
    // Android-specific dependencies
    single<IMediaPlayer> { SimpleIMediaPlayer() }
}

/**
 * Function to get all Android modules
 */
fun getAndroidModules(): List<Module> = listOf(androidModule)
