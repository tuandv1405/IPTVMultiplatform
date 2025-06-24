package tss.t.tsiptv.core.database

import tss.t.tsiptv.core.network.NetworkClient

/**
 * Provides a platform-specific implementation of DatabaseFactory.
 *
 * @param networkClient The network client to use for fetching playlists
 * @return A platform-specific implementation of DatabaseFactory
 */
expect fun createDatabaseFactory(networkClient: NetworkClient): DatabaseFactory
