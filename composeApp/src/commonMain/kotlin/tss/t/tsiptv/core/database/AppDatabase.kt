package tss.t.tsiptv.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Room database for the application.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        CategoryEntity::class,
        ChannelAttributeEntity::class
    ],
    version = 1,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Gets the playlist DAO.
     *
     * @return The playlist DAO
     */
    abstract fun playlistDao(): PlaylistDao

    /**
     * Gets the channel DAO.
     *
     * @return The channel DAO
     */
    abstract fun channelDao(): ChannelDao

    /**
     * Gets the category DAO.
     *
     * @return The category DAO
     */
    abstract fun categoryDao(): CategoryDao

    /**
     * Gets the channel attribute DAO.
     *
     * @return The channel attribute DAO
     */
    abstract fun channelAttributeDao(): ChannelAttributeDao
}
// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations()
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
