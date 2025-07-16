package tss.t.tsiptv.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import tss.t.tsiptv.core.database.dao.CategoryDao
import tss.t.tsiptv.core.database.dao.ChannelAttributeDao
import tss.t.tsiptv.core.database.dao.ChannelDao
import tss.t.tsiptv.core.database.dao.PlaylistDao
import tss.t.tsiptv.core.database.dao.ProgramDao
import tss.t.tsiptv.core.database.entity.CategoryEntity
import tss.t.tsiptv.core.database.entity.ChannelAttributeEntity
import tss.t.tsiptv.core.database.entity.ChannelEntity
import tss.t.tsiptv.core.database.entity.PlaylistEntity
import tss.t.tsiptv.core.database.entity.ProgramEntity

/**
 * Room database for the application.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        CategoryEntity::class,
        ChannelAttributeEntity::class,
        ProgramEntity::class
    ],
    version = 2,
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

    /**
     * Gets the program DAO.
     *
     * @return The program DAO
     */
    abstract fun programDao(): ProgramDao
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
        .addMigrations(object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS `programs` (`id` TEXT NOT NULL, `channelId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `category` TEXT, `playlistId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`channelId`) REFERENCES `channels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_programs_channelId` ON `programs` (`channelId`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_programs_playlistId` ON `programs` (`playlistId`)")
            }
        })
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
