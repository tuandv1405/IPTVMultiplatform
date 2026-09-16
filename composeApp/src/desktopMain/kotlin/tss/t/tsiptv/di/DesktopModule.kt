package tss.t.tsiptv.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import tss.t.tsiptv.core.firebase.DesktopFirebaseInitializer
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.IFirebaseFirestore
import tss.t.tsiptv.core.firebase.IFirebaseStorage
import tss.t.tsiptv.core.storage.DesktopSettingsFactory
import tss.t.tsiptv.core.storage.SettingsFactory
import tss.t.tsiptv.player.DesktopMediaPlayer
import tss.t.tsiptv.player.MediaPlayer

/**
 * Desktop-specific module for dependencies
 */
val desktopModule = module {
    // Settings factory. Android and iOS register theirs; desktop never did, so
    // resolving KeyValueStorage failed and took the whole graph down.
    single<SettingsFactory> { DesktopSettingsFactory() }

    // Desktop-specific dependencies
    single<MediaPlayer> {
        DesktopMediaPlayer(
            CoroutineScope(Dispatchers.IO + SupervisorJob())
        )
    }

    // Firebase dependencies
    single<IFirebaseAuth> { DesktopFirebaseInitializer.provideFirebaseAuth() }
    single<IFirebaseFirestore> { DesktopFirebaseInitializer.provideFirebaseFirestore() }
    single<IFirebaseStorage> { DesktopFirebaseInitializer.provideFirebaseStorage() }
}

/**
 * Function to get all desktop modules
 */
fun getDesktopModules(): List<Module> = listOf(desktopModule)
