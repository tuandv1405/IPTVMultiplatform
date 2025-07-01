package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.dsl.module
import tss.t.tsiptv.core.firebase.FirebaseAuth
import tss.t.tsiptv.core.firebase.FirebaseFirestore
import tss.t.tsiptv.core.firebase.FirebaseStorage
import tss.t.tsiptv.core.firebase.IosFirebaseInitializer
import tss.t.tsiptv.core.player.IMediaPlayer
import tss.t.tsiptv.core.player.SimpleIMediaPlayer

/**
 * iOS-specific module for dependencies
 */
val iosModule = module {
    // iOS-specific dependencies
    single<IMediaPlayer> { SimpleIMediaPlayer() }

    // Firebase dependencies
    single<FirebaseAuth> { IosFirebaseInitializer.provideFirebaseAuth() }
    single<FirebaseFirestore> { IosFirebaseInitializer.provideFirebaseFirestore() }
    single<FirebaseStorage> { IosFirebaseInitializer.provideFirebaseStorage() }
}

/**
 * Function to get all iOS modules
 */
fun getIosModules(): List<Module> = listOf(iosModule)
