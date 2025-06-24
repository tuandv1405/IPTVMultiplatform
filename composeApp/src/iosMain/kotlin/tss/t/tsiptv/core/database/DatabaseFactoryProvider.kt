package tss.t.tsiptv.core.database

import tss.t.tsiptv.core.network.NetworkClient

/**
 * iOS implementation of createDatabaseFactory.
 *
 * @param networkClient The network client to use for fetching playlists
 * @return An iOS-specific implementation of DatabaseFactory
 */
actual fun createDatabaseFactory(networkClient: NetworkClient): DatabaseFactory {
    return IosDatabaseFactory(networkClient)
}
