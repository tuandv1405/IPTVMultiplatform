package tss.t.tsiptv.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Initializes Koin for iOS.
 * This should be called early in the app lifecycle.
 */
object IosKoinInitializer {
    /**
     * Initializes Koin with the iOS modules.
     */
    fun initialize(appDeclaration: KoinAppDeclaration = {}) {
        startKoin {
            appDeclaration()
            modules(getCommonModules() + getIosModules())
        }
    }
}