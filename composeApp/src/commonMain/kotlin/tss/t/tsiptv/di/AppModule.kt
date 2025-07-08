package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.createDatabaseFactory
import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.core.parser.IPTVParserService
import tss.t.tsiptv.core.parser.JSONParser
import tss.t.tsiptv.core.parser.M3UParser
import tss.t.tsiptv.core.parser.XMLParser
import tss.t.tsiptv.core.parser.XMLEPGParser
import tss.t.tsiptv.core.parser.JSONEPGParser
import tss.t.tsiptv.core.parser.XMLTVEPGParser
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.core.storage.MultiplatformSettingsKeyValueStorage
import tss.t.tsiptv.core.language.LanguageRepository
import tss.t.tsiptv.core.language.LocaleManager
import tss.t.tsiptv.core.parser.IPTVParser
import tss.t.tsiptv.feature.auth.di.authModule

/**
 * Common module for shared dependencies
 */
val commonModule = module {
    // Network
    single { NetworkClientFactory.create().getNetworkClient() }

    // Database
    single {
        createDatabaseFactory(get()).createDatabase()
    } bind IPTVDatabase::class


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

    single { XMLEPGParser() }
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

    // Language
    single { LanguageRepository(get()) }
    single { LocaleManager(get()) }
}

/**
 * Function to get all common modules
 */
fun getCommonModules(): List<Module> = listOf(
    commonModule,
    authModule,
)
