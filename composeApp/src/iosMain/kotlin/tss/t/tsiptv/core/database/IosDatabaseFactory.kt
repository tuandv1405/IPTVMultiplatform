package tss.t.tsiptv.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import tss.t.tsiptv.core.network.NetworkClient

/**
 * iOS implementation of DatabaseFactory.
 * This implementation uses Room to persist data between app restarts.
 *
 * @property networkClient The network client to use for fetching playlists
 */
class IosDatabaseFactory(
    private val networkClient: NetworkClient
) : DatabaseFactory {
    /**
     * Creates an instance of IPTVDatabase using Room.
     *
     * @return An instance of IPTVDatabase
     */
    override fun createDatabase(): IPTVDatabase {
        // For now, we'll use the InMemoryIPTVDatabase as a fallback
        // until we properly implement Room for iOS
        return RoomIPTVDatabase(
            getRoomDatabase(
                getDatabaseBuilder()
            ), networkClient
        )
    }

    private fun getDatabasePath(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDirectory = paths.firstOrNull() as? String ?: ""
        return "$documentsDirectory/iptv_database.db"
    }

    fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
        val dbFilePath = documentDirectory() + "/my_room.db"
        return Room.databaseBuilder<AppDatabase>(dbFilePath).setDriver(BundledSQLiteDriver())
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun documentDirectory(): String {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(documentDirectory?.path)
    }

    companion object {
        /**
         * Creates an iOS-specific implementation of DatabaseFactory.
         *
         * @param networkClient The network client to use for fetching playlists
         * @return An iOS-specific implementation of DatabaseFactory
         */
        fun create(networkClient: NetworkClient): DatabaseFactory {
            return IosDatabaseFactory(networkClient)
        }
    }
}
