package tss.t.tsiptv.core.tsiptv.di

import io.ktor.client.HttpClient
import org.koin.dsl.module
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.language.customAppLocale
import tss.t.tsiptv.core.network.KtorNetworkClient
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.stremio.createStremioHttpClient
import tss.t.tsiptv.core.tsiptv.AddonRepositorySourceBridge
import tss.t.tsiptv.core.tsiptv.KtorSourceHttpTransport
import tss.t.tsiptv.core.tsiptv.SourceAddonBridge
import tss.t.tsiptv.core.tsiptv.SourceHttpTransport
import tss.t.tsiptv.core.tsiptv.TsiptvSourceService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }

/** F3: TS IPTV Sources (fetching, include resolution, storage). UI view models are in `tsiptvUiModule`. */
val tsiptvModule = module {
    // Derived from the platform client (same engine, logging off); redirects, gzip, timeouts, no status exceptions.
    single<SourceHttpTransport> {
        val base: HttpClient = (get<NetworkClient>() as? KtorNetworkClient)?.httpClient ?: HttpClient()
        KtorSourceHttpTransport(createStremioHttpClient(base))
    }
    single<SourceAddonBridge> { AddonRepositorySourceBridge(get()) }
    single { tss.t.tsiptv.core.tsiptv.TsiptvDetailProvider(get()) }
    single {
        TsiptvSourceService(
            database = get(),
            http = get(),
            addons = get(),
            mediaHistory = get<IPTVDatabase>().stremioStores,
            nowMs = nowMs,
            uiLanguage = { customAppLocale },
        )
    }
}
