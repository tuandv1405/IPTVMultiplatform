package tss.t.tsiptv.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import tss.t.tsiptv.core.firebase.DesktopFirebaseInitializer
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.FirebaseFirestore
import tss.t.tsiptv.core.firebase.FirebaseStorage
import tss.t.tsiptv.player.DesktopMediaPlayer
import tss.t.tsiptv.player.MediaPlayer

/**
 * Desktop-specific module for dependencies
 */
val desktopModule = module {
    // Desktop-specific dependencies
    single<MediaPlayer> {
        DesktopMediaPlayer(
            CoroutineScope(Dispatchers.IO + SupervisorJob())
        )
    }

    // Firebase dependencies
    single<IFirebaseAuth> { DesktopFirebaseInitializer.provideFirebaseAuth() }
    single<FirebaseFirestore> { DesktopFirebaseInitializer.provideFirebaseFirestore() }
    single<FirebaseStorage> { DesktopFirebaseInitializer.provideFirebaseStorage() }
}

/**
 * Function to get all desktop modules
 */
fun getDesktopModules(): List<Module> = listOf(desktopModule)
