package tss.t.tsiptv.feature.lan

import android.content.Context
import android.content.pm.ApplicationInfo
import org.koin.dsl.module
import tss.t.tsiptv.feature.account.DeviceNameProvider
import tss.t.tsiptv.feature.account.FakeRewardedAdGateway
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
    // Debug builds "earn" rewards without an ad until AdMob is wired (handoff); release builds get none.
    single<RewardedAdGateway> {
        val debuggable = (get<Context>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        FakeRewardedAdGateway(grants = debuggable)
    }
}
