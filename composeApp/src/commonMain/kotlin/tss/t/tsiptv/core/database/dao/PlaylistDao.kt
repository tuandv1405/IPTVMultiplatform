package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.PlaylistEntity
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount

/**
 * DAO for accessing playlist data in the database.
 */
@Dao
interface PlaylistDao {
    /**
     * Gets all playlists.
     *
     * @return A flow of all playlists
     */
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    /**
     * Gets all playlists with their channel counts.
     *
     * @return A flow of all playlists with channel counts
     */
    @Query(
        """
        SELECT p.*, COUNT(c.id) as channelCount 
        FROM playlists p 
        LEFT JOIN channel c ON c.playlistId = p.id 
        GROUP BY p.id
    """
    )
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithChannelCount>>

    /**
     * Gets a playlist by ID.
     *
     * @param id The ID of the playlist to get
     * @return The playlist with the given ID, or null if not found
     */
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    /**
     * Inserts or updates a playlist.
     *
     * @param playlist The playlist to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    /**
     * Inserts or updates multiple playlists.
     *
     * @param playlists The playlists to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    /**
     * Deletes a playlist.
     *
     * @param playlist The playlist to delete
     */
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    /**
     * Deletes a playlist by ID.
     *
     * @param id The ID of the playlist to delete
     */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)
}