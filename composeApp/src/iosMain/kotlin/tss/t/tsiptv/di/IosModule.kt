package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.IosFirebaseAuthImplementation
import tss.t.tsiptv.core.player.IMediaPlayer
import tss.t.tsiptv.core.player.SimpleIMediaPlayer
import tss.t.tsiptv.core.storage.IosSettingsFactory
import tss.t.tsiptv.core.storage.SettingsFactory

/**
 * iOS-specific module for dependencies
 */
val iosModule = module {
    // iOS-specific dependencies
    single<IMediaPlayer> { SimpleIMediaPlayer() }

    // Settings factory
    single<SettingsFactory> {
        IosSettingsFactory()
    }

    single {
        IosFirebaseAuthImplementation()
    } bind IFirebaseAuth::class
}

/**
 * Function to get all iOS modules
 */
fun getIosModules(): List<Module> = listOf(iosModule)
