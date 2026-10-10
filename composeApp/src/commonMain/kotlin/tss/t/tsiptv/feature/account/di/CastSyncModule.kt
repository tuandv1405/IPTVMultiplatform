package tss.t.tsiptv.feature.account.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import tss.t.tsiptv.core.security.createSecretCipher
import tss.t.tsiptv.feature.account.AccountCloud
import tss.t.tsiptv.feature.account.DeviceNameProvider
import tss.t.tsiptv.feature.account.DeviceSessionManager
import tss.t.tsiptv.feature.account.UnavailableRewardedAdGateway
import tss.t.tsiptv.feature.account.FirestoreAccountCloud
import tss.t.tsiptv.feature.account.LocalDevice
import tss.t.tsiptv.feature.account.QuotaService
import tss.t.tsiptv.feature.account.RewardedAdGateway
import tss.t.tsiptv.feature.account.SyncService
import tss.t.tsiptv.feature.lan.LanDiscovery
import tss.t.tsiptv.feature.lan.LanKeyAgreementFactory
import tss.t.tsiptv.feature.lan.LanReceiverController
import tss.t.tsiptv.feature.lan.LanSender
import tss.t.tsiptv.feature.lan.LanServer
import tss.t.tsiptv.feature.lan.LanTransport
import tss.t.tsiptv.feature.lan.PairingStore
import tss.t.tsiptv.feature.lan.TvSendViewModel
import tss.t.tsiptv.feature.lan.UnsupportedLanDiscovery
import tss.t.tsiptv.feature.lan.UnsupportedLanServer
import tss.t.tsiptv.feature.lan.UnsupportedLanTransport
import tss.t.tsiptv.ui.screens.connect.ConnectViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val SENDER_PAIRINGS = named("lanSenderPairings")
val RECEIVER_PAIRINGS = named("lanReceiverPairings")

/**
 * TV cast, send to TV, device limit and device sync (docs/prd-tv-cast-and-sync.md).
 *
 * The defaults here are the "not supported yet" ones; `androidLanModule` (androidMain), loaded after
 * this module, overrides the LAN pieces, the device name and binds AdMob's rewarded ads to [RewardedAdGateway].
 */
@OptIn(ExperimentalTime::class)
val castSyncModule = module {
    val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    single<DeviceNameProvider> { DeviceNameProvider.Default }
    single<LanKeyAgreementFactory> { LanKeyAgreementFactory.Unsupported }
    single<LanDiscovery> { UnsupportedLanDiscovery }
    single<LanTransport> { UnsupportedLanTransport }
    single<LanServer> { UnsupportedLanServer }
    single<RewardedAdGateway> { UnavailableRewardedAdGateway }
    single<AccountCloud> { FirestoreAccountCloud(get()) }

    single { LocalDevice(get(), get()) }
    single(SENDER_PAIRINGS) { PairingStore(get(), createSecretCipher(), PairingStore.Role.SENDER) }
    single(RECEIVER_PAIRINGS) { PairingStore(get(), createSecretCipher(), PairingStore.Role.RECEIVER) }

    single { LanSender(get(), get(SENDER_PAIRINGS), get(), get(), clock) }
    single { LanReceiverController(get(), get(), get(RECEIVER_PAIRINGS), get(), clock) }

    single {
        DeviceSessionManager(
            auth = get(),
            cloud = get(),
            local = get(),
            storage = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            clock = clock,
        ).also { it.start() }
    }
    single { QuotaService(get(), get(), get(), clock) }
    single { SyncService(get(), get(), get(), get(), get(), get(), getOrNull(), get(), clock) }

    viewModelOf(::TvSendViewModel)
    viewModel { ConnectViewModel(get(), get(), get(), get(SENDER_PAIRINGS)) }
}
