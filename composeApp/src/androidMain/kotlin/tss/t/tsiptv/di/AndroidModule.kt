package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import tss.t.tsiptv.core.firebase.AndroidFirebaseAuth
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.googlesignin.AndroidGoogleSignInImplementation
import tss.t.tsiptv.core.googlesignin.GoogleSignIn
import tss.t.tsiptv.core.storage.AndroidSettingsFactory
import tss.t.tsiptv.core.storage.SettingsFactory

/**
 * Android-specific module for dependencies
 */
val androidModule = module {
    // Settings factory
    single<SettingsFactory> {
        AndroidSettingsFactory(get())
    }
    single {
        AndroidFirebaseAuth()
    } bind IFirebaseAuth::class

    // Provide GoogleSignIn implementation
    single {
        AndroidGoogleSignInImplementation()
    } bind GoogleSignIn::class
}

/**
 * Function to get all Android modules
 */
fun getAndroidModules(): List<Module> = listOf(androidModule)
