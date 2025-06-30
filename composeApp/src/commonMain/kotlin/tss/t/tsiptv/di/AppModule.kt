package tss.t.tsiptv.di

import org.koin.core.module.Module
import org.koin.dsl.module
import tss.t.tsiptv.core.database.createDatabaseFactory
import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.core.parser.IPTVParserService
import tss.t.tsiptv.core.parser.JSONParser
import tss.t.tsiptv.core.parser.M3UParser
import tss.t.tsiptv.core.parser.XMLParser
import tss.t.tsiptv.core.parser.XMLEPGParser
import tss.t.tsiptv.core.parser.JSONEPGParser
import tss.t.tsiptv.core.parser.XMLTVEPGParser

/**
 * Common module for shared dependencies
 */
val commonModule = module {
    // Network
    single { NetworkClientFactory.create().getNetworkClient() }

    // Database
    single { createDatabaseFactory(get()).createDatabase() }

    // Parsers
    single { M3UParser() }
    single { XMLParser() }
    single { JSONParser() }
    single { XMLEPGParser() }
    single { JSONEPGParser() }
    single { XMLTVEPGParser() }

    // Parser Service
    single { IPTVParserService(get()) }
}

/**
 * Function to get all common modules
 */
fun getCommonModules(): List<Module> = listOf(commonModule)
