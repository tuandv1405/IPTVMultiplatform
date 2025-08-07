package tss.t.tsiptv.core.database

import android.app.Application
import tss.t.tsiptv.core.network.NetworkClient

/**
 * Android implementation of DatabaseFactory.
 * This implementation uses Room to persist data between app restarts.
 *
 * @property context The application context
 * @property networkClient The network client to use for fetching playlists
 */
class AndroidDatabaseFactory(
    private val context: Application,
    private val networkClient: NetworkClient
) : DatabaseFactory {
    /**
     * Creates an instance of IPTVDatabase using Room.
     *
     * @return An instance of IPTVDatabase
     */
    override fun createDatabase(): IPTVDatabase {
        return RoomIPTVDatabase(
            getRoomDatabase(getDatabaseBuilder(context)),
            networkClient
        )
    }

    companion object {
        /**
         * Creates an Android-specific implementation of DatabaseFactory.
         *
         * @param context The application context
         * @param networkClient The network client to use for fetching playlists
         * @return An Android-specific implementation of DatabaseFactory
         */
        private lateinit var instance: AndroidDatabaseFactory
        fun create(context: Application, networkClient: NetworkClient): DatabaseFactory {
            if (!this::instance.isInitialized) {
                instance = AndroidDatabaseFactory(context, networkClient)
            }
            return instance
        }
    }
}
