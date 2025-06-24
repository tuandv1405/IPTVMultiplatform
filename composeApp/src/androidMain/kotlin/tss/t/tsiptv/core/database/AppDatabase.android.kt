package tss.t.tsiptv.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

// shared/src/androidMain/kotlin/Database.kt
fun getDatabaseBuilder(ctx: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = ctx.applicationContext
    val dbFile = appContext.getDatabasePath("iptv_list.db")
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}
