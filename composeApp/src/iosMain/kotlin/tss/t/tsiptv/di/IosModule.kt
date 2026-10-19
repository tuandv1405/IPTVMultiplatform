package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import tss.t.tsiptv.core.firebase.IFirebaseAuth
import tss.t.tsiptv.core.firebase.IosFirebaseAuthImplementation
import tss.t.tsiptv.core.storage.IosSettingsFactory
import tss.t.tsiptv.core.storage.SettingsFactory
import tss.t.tsiptv.feature.account.DeviceNameProvider
import tss.t.tsiptv.feature.lan.IosDeviceNameProvider
import tss.t.tsiptv.feature.lan.IosLanDiscovery
import tss.t.tsiptv.feature.lan.IosLanKeyAgreementFactory
import tss.t.tsiptv.feature.lan.IosLanTransport
import tss.t.tsiptv.feature.lan.LanDiscovery
import tss.t.tsiptv.feature.lan.LanKeyAgreementFactory
import tss.t.tsiptv.feature.lan.LanTransport
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

    // Firebase implementations
    single {
        IosFirebaseAuthImplementation()
    } bind IFirebaseAuth::class

//    single<IosRemoteConfigImpl> {
//        IosRemoteConfigImpl(
//            keyValueStorage = get<KeyValueStorage>(),
//            coroutineScope = CoroutineScope(Dispatchers.IO)
//        )
//    } bind IRemoteConfig::class

    // Cast / send to TV (sender only), overriding castSyncModule's "not supported" defaults.
    single<DeviceNameProvider> { IosDeviceNameProvider }
    single<LanKeyAgreementFactory> { IosLanKeyAgreementFactory }
    single<LanDiscovery> { IosLanDiscovery() }
    single<LanTransport> { IosLanTransport }

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
