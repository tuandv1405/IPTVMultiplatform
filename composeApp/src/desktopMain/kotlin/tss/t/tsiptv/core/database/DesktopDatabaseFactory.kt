package tss.t.tsiptv.core.database

import tss.t.tsiptv.core.network.NetworkClient
import java.io.File

/**
 * Desktop implementation of DatabaseFactory.
 * This implementation uses Room to persist data between app restarts.
 *
 * @property networkClient The network client to use for fetching playlists
 */
class DesktopDatabaseFactory(
    private val networkClient: NetworkClient
) : DatabaseFactory {
    /**
     * Creates an instance of IPTVDatabase using Room.
     *
     * @return An instance of IPTVDatabase
     */
    override fun createDatabase(): IPTVDatabase {
        // For now, we'll use the InMemoryIPTVDatabase as a fallback
        // until we properly implement Room for Desktop
        return RoomIPTVDatabase(
            getRoomDatabase(
                getDatabaseBuilder()
            ),
            networkClient
        )
    }

    private fun getDbPath(): String {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".tsiptv")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return File(appDir, "iptv_database.db").absolutePath
    }

    companion object {
        /**
         * Creates a Desktop-specific implementation of DatabaseFactory.
         *
         * @param networkClient The network client to use for fetching playlists
         * @return A Desktop-specific implementation of DatabaseFactory
         */
        fun create(networkClient: NetworkClient): DatabaseFactory {
            return DesktopDatabaseFactory(networkClient)
        }
    }
}
