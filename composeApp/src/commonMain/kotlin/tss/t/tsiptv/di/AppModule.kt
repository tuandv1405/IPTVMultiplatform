package tss.t.tsiptv.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.createDatabaseFactory
import tss.t.tsiptv.core.firebase.IRemoteConfig
import tss.t.tsiptv.core.firebase.remoteconfig.HttpRemoteConfigImpl
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.language.LanguageRepository
import tss.t.tsiptv.core.language.LocaleManager
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.core.network.NetworkConnectivityChecker
import tss.t.tsiptv.core.network.NetworkConnectivityCheckerFactory
import tss.t.tsiptv.core.parser.IPTVParser
import tss.t.tsiptv.core.parser.IPTVParserService
import tss.t.tsiptv.core.parser.epg.JSONEPGParser
import tss.t.tsiptv.core.parser.JSONParser
import tss.t.tsiptv.core.parser.XMLParser
import tss.t.tsiptv.core.parser.epg.XMLTVEPGParser
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.repository.HistoryRepositoryImpl
import tss.t.tsiptv.core.repository.IAdsRepository
import tss.t.tsiptv.core.repository.IHistoryRepository
import tss.t.tsiptv.core.repository.ads.IAdsRepositoryImpl
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.core.storage.MultiplatformSettingsKeyValueStorage
import tss.t.tsiptv.feature.auth.di.authModule
import tss.t.tsiptv.ui.screens.ads.AdsViewModel
import tss.t.tsiptv.ui.screens.home.HomeViewModel
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.screens.programs.ProgramViewModel
import tss.t.tsiptv.ui.screens.programs.details.ProgramDetailViewModel
import tss.t.tsiptv.usecase.di.useCaseModule

/**
 * Common module for shared dependencies
 */
val commonModule = module {
    // Network
    single<NetworkClient> {
        NetworkClientFactory.create().getNetworkClient()
    }

    // Database
    single {
        createDatabaseFactory(get()).createDatabase()
    } bind IPTVDatabase::class

    single<HttpRemoteConfigImpl> {
        HttpRemoteConfigImpl(
            networkClient = get<NetworkClient>(),
            keyValueStorage = get<KeyValueStorage>(),
            coroutineScope = CoroutineScope(Dispatchers.IO)
        )
    } bind IRemoteConfig::class

    single<IAdsRepositoryImpl> {
        IAdsRepositoryImpl(
            remoteConfig = get(),
            networkClient = get()
        )
    } bind IAdsRepository::class

    // Parsers
    single { M3UParser() }
    single { XMLParser() }
    single { JSONParser() }

    single<IPTVParser>(named("M3uParser")) {
        get<M3UParser>()
    }

    single<IPTVParser>(named("XMLParser")) {
        get<XMLParser>()
    }

    single<IPTVParser>(named("JSONParser")) {
        get<JSONParser>()
    }

    single { JSONEPGParser() }
    single { XMLTVEPGParser() }

    // Parser Service
    single { IPTVParserService(get()) }
    single<KeyValueStorage> {
        MultiplatformSettingsKeyValueStorage(
            settingsFactory = get(),
            name = "GlobalSettings"
        )
    }

    single<InMemoryKeyValueStorage> {
        InMemoryKeyValueStorage()
    }

    // Language
    single { LanguageRepository(get()) }
    single { LocaleManager(get()) }


    single<CoroutineScope>(named("MediaCoroutine")) {
        CoroutineScope(Dispatchers.IO) + SupervisorJob()
    }

    single<CoroutineScope>(named("MediaCoroutineUI")) {
        CoroutineScope(Dispatchers.Main) + SupervisorJob()
    }


    // History Repository
    single<IHistoryRepository> {
        HistoryRepositoryImpl(get())
    }

    // Channel History Tracker
    single {
        ChannelHistoryTracker(get(), get(named("MediaCoroutine")), get())
    }

    single<NetworkConnectivityChecker> {
        NetworkConnectivityCheckerFactory.create()
    }

    viewModelOf(::AdsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::ProgramViewModel)
    viewModelOf(::ProgramDetailViewModel)
}

/**
 * Function to get all common modules
 */
fun getCommonModules(): List<Module> = listOf(
    commonModule,
    authModule,
    useCaseModule
)
