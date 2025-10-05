package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.IosFirebaseAuthImplementation
import tss.t.tsiptv.core.storage.IosSettingsFactory
import tss.t.tsiptv.core.storage.SettingsFactory
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.MediaPlayerFactory

/**
 * iOS-specific module for dependencies
 */
val iosModule = module {
    // iOS-specific dependencies

    // Settings factory
    single<SettingsFactory> {
        IosSettingsFactory()
    }

    single {
        IosFirebaseAuthImplementation()
    } bind IFirebaseAuth::class

    single<MediaPlayer> {
        MediaPlayerFactory.createPlayer(
            get(named("MediaCoroutine"))
        )
    }
}

/**
 * Function to get all iOS modules
 */
fun getIosModules(): List<Module> = listOf(iosModule)
