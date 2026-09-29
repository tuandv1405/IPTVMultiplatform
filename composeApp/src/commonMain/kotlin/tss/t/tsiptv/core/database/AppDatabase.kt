package tss.t.tsiptv.core.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import tss.t.tsiptv.core.database.dao.CategoryDao
import tss.t.tsiptv.core.database.dao.ChannelAttributeDao
import tss.t.tsiptv.core.database.dao.ChannelDao
import tss.t.tsiptv.core.database.dao.ChannelHistoryDao
import tss.t.tsiptv.core.database.dao.PlaylistDao
import tss.t.tsiptv.core.database.dao.MediaHistoryDao
import tss.t.tsiptv.core.database.dao.ProgramDao
import tss.t.tsiptv.core.database.dao.StremioAddonDao
import tss.t.tsiptv.core.database.dao.TsiptvDao
import tss.t.tsiptv.core.database.entity.TsiptvIncludeEntity
import tss.t.tsiptv.core.database.entity.TsiptvSourceEntity
import tss.t.tsiptv.core.database.entity.VodEpisodeEntity
import tss.t.tsiptv.core.database.entity.VodItemEntity
import tss.t.tsiptv.core.database.entity.CategoryEntity
import tss.t.tsiptv.core.database.entity.MediaHistoryEntity
import tss.t.tsiptv.core.database.entity.StremioAddonEntity
import tss.t.tsiptv.core.database.entity.ChannelAttributeEntity
import tss.t.tsiptv.core.database.entity.ChannelEntity
import tss.t.tsiptv.core.database.entity.ChannelHistoryEntity
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
        ProgramEntity::class,
        ChannelHistoryEntity::class,
        StremioAddonEntity::class,
        MediaHistoryEntity::class,
        TsiptvSourceEntity::class,
        TsiptvIncludeEntity::class,
        VodItemEntity::class,
        VodEpisodeEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 2,
            to = 3
        ),
        AutoMigration(
            from = 3,
            to = 4,
            spec = Migration3To4::class
        ),
        // F2: two new tables only (stremio_addons, media_history); existing data is untouched.
        AutoMigration(
            from = 4,
            to = 5
        ),
        // F3: four new tables (tsiptv_sources, tsiptv_includes, vod_items, vod_episodes) and five
        // nullable channel columns; existing rows are untouched.
        AutoMigration(
            from = 5,
            to = 6
        ),
    ]
)
@TypeConverters(Converter::class)
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

    /**
     * Gets the channel history DAO.
     *
     * @return The channel history DAO
     */
    abstract fun channelHistoryDao(): ChannelHistoryDao

    /** F2: installed Stremio-compatible addons. */
    abstract fun stremioAddonDao(): StremioAddonDao

    /** F2: movie/series/tv watch history (shared with F3). */
    abstract fun mediaHistoryDao(): MediaHistoryDao

    /** F3: TS IPTV Sources, their includes and VOD rows. */
    abstract fun tsiptvDao(): TsiptvDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
): AppDatabase {
    return builder
        // Last resort only: a missing migration would silently delete every user's
        // playlists and history. Each version bump ships an AutoMigration and a test
        // (desktopTest/.../AppDatabaseMigrationTest.kt).
        .fallbackToDestructiveMigration(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
