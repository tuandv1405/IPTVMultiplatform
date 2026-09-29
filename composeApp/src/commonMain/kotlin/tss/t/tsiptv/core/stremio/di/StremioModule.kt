package tss.t.tsiptv.core.stremio.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import tss.t.tsiptv.AppBuildInfo
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.network.KtorNetworkClient
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.security.SecretCipher
import tss.t.tsiptv.core.security.createSecretCipher
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.core.stremio.AddonBlocklistFetcher
import tss.t.tsiptv.core.stremio.AddonRepository
import tss.t.tsiptv.core.stremio.AddonSettings
import tss.t.tsiptv.core.stremio.KtorStremioHttpTransport
import tss.t.tsiptv.core.stremio.MediaHistoryRepository
import tss.t.tsiptv.core.stremio.MediaProgressTracker
import tss.t.tsiptv.core.stremio.StremioClient
import tss.t.tsiptv.core.stremio.StremioHttpTransport
import tss.t.tsiptv.core.stremio.createStremioHttpClient
import tss.t.tsiptv.core.stremio.debugAddonBlocklistUrlOverride
import tss.t.tsiptv.ui.screens.addons.AddonsViewModel
import tss.t.tsiptv.ui.screens.discover.CatalogViewModel
import tss.t.tsiptv.ui.screens.discover.DiscoverViewModel
import tss.t.tsiptv.ui.screens.mediadetail.MediaDetailViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** [AddonSettings] over the app's key-value storage. A missing key is null, not 0. */
class KeyValueAddonSettings(private val storage: KeyValueStorage) : AddonSettings {
    override suspend fun getLong(key: String): Long? = storage.getLong(key, MISSING).takeIf { it != MISSING }
    override suspend fun putLong(key: String, value: Long) = storage.putLong(key, value)
    override suspend fun getString(key: String): String? = storage.getString(key, "").takeIf { it.isNotEmpty() }
    override suspend fun putString(key: String, value: String) = storage.putString(key, value)

    private companion object {
        const val MISSING = Long.MIN_VALUE
    }
}

@OptIn(ExperimentalTime::class)
private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }

val STREMIO_SCOPE = named("StremioScope")

/** F2: Stremio-compatible addons (protocol client, repositories, view models). */
val stremioModule = module {
    single<CoroutineScope>(STREMIO_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // One shared client for addons, derived from the platform client (same engine), never logged.
    single<StremioHttpTransport> {
        val base: HttpClient = (get<NetworkClient>() as? KtorNetworkClient)?.httpClient ?: HttpClient()
        KtorStremioHttpTransport(createStremioHttpClient(base))
    }
    single {
        StremioClient(
            transport = get(),
            userAgent = StremioClient.userAgentFor(AppBuildInfo.VERSION_NAME),
            nowMs = nowMs,
            refreshScope = get(STREMIO_SCOPE),
        )
    }
    single {
        AddonBlocklistFetcher(
            transport = get(),
            userAgent = StremioClient.userAgentFor(AppBuildInfo.VERSION_NAME),
            // QC only (debuggable Android builds); production URL otherwise.
            url = debugAddonBlocklistUrlOverride() ?: AddonBlocklistFetcher.BLOCKLIST_URL,
        )
    }
    single<SecretCipher> { createSecretCipher() }
    single<AddonSettings> { KeyValueAddonSettings(get()) }
    single {
        val stores = get<IPTVDatabase>().stremioStores
        AddonRepository(
            store = stores,
            history = stores,
            client = get(),
            cipher = get(),
            blocklistFetcher = get(),
            settings = get(),
            nowMs = nowMs,
            scope = get(STREMIO_SCOPE),
        )
    }
    single { MediaHistoryRepository(get<IPTVDatabase>().stremioStores, nowMs) }
    single { MediaProgressTracker(get(), get(), get(named("MediaCoroutine"))) }

    viewModelOf(::AddonsViewModel)
    viewModelOf(::DiscoverViewModel)
    viewModelOf(::CatalogViewModel)
    viewModelOf(::MediaDetailViewModel)
}
