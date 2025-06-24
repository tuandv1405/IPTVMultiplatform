package tss.t.tsiptv.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

// shared/src/jvmMain/kotlin/Database.kt

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "iptv_list.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}
