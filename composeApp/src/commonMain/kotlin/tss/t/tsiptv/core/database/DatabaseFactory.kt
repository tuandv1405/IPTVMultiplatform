package tss.t.tsiptv.core.database

import tss.t.tsiptv.core.network.NetworkClient

/**
 * Interface for creating database instances.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface DatabaseFactory {
    /**
     * Creates an instance of IPTVDatabase.
     *
     * @return An instance of IPTVDatabase
     */
    fun createDatabase(): IPTVDatabase

    companion object {
        /**
         * Creates a platform-specific implementation of DatabaseFactory.
         * This is a placeholder implementation that returns InMemoryIPTVDatabase.
         * Platform-specific implementations should override this method.
         *
         * @param networkClient The network client to use for fetching playlists
         * @return A platform-specific implementation of DatabaseFactory
         */
        fun create(networkClient: NetworkClient): DatabaseFactory {
            return object : DatabaseFactory {
                override fun createDatabase(): IPTVDatabase {
                    return InMemoryIPTVDatabase()
                }
            }
        }
    }
}
