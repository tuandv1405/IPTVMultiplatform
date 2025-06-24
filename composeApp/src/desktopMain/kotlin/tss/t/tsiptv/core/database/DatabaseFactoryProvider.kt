package tss.t.tsiptv.core.database

import tss.t.tsiptv.core.network.NetworkClient

/**
 * Desktop implementation of createDatabaseFactory.
 *
 * @param networkClient The network client to use for fetching playlists
 * @return A Desktop-specific implementation of DatabaseFactory
 */
actual fun createDatabaseFactory(networkClient: NetworkClient): DatabaseFactory {
    return DesktopDatabaseFactory(networkClient)
}
