package tss.t.tsiptv.feature.lan

import org.koin.dsl.module
import tss.t.tsiptv.feature.account.DeviceNameProvider
import tss.t.tsiptv.core.ads.AdMobRewardedAdGateway
import tss.t.tsiptv.feature.account.RewardedAdGateway

/**
 * Android pieces of the TV cast / send / sync features. Loaded after the common `castSyncModule`,
 * so these bindings override its "not supported" defaults.
 */
val androidLanModule = module {
    single<DeviceNameProvider> { AndroidDeviceNameProvider(get()) }
    single<LanKeyAgreementFactory> { JvmLanKeyAgreementFactory }
    single<LanDiscovery> { NsdLanDiscovery(get()) }
    single<LanTransport> { SocketLanTransport }
    single<LanServer> { NsdSocketLanServer(get()) }
    // AdMob rewarded ads behind the ads layer's consent / 24 h / TV rules (test unit in debug).
    single<RewardedAdGateway> { AdMobRewardedAdGateway(get(), get(), get()) }
}
