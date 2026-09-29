package tss.t.tsiptv.core.database

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v3 → v4 adds the per-channel playback columns (headers, DRM, groups, number, …).
 *
 * Existing rows get only defaults, so channels imported before F1 would play without the
 * headers and DRM their playlist declares. Marking every URL playlist as never updated makes
 * the next open re-parse it through the new parser; favourites and history survive because the
 * importer keeps channel ids and carries favourite flags over.
 */
class Migration3To4 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE playlists SET lastUpdated = 0 WHERE sourceType = 'URL'")
    }
}
