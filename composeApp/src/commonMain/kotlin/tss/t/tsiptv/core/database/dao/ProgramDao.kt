package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.ProgramEntity

/**
 * Room DAO for TV channel programs.
 */
@Dao
interface ProgramDao {
    /**
     * Gets all programs.
     *
     * @return A flow of all programs
     */
    @Query("SELECT * FROM programs")
    fun getAllPrograms(): Flow<List<ProgramEntity>>

    @Query(
        "SELECT COUNT(*) FROM programs WHERE " +
                "playlistId == :playListId AND " +
                "(startTime <= :timeStamp OR endTime <= :timeStamp)"
    )
    suspend fun countValidPrograms(playListId: String, timeStamp: Long): Int

    @Query(
        "SELECT * FROM programs WHERE " +
                "playlistId == :playListId AND " +
                "(startTime <= :timeStamp OR endTime <= :timeStamp) LIMIT :limit OFFSET :offset"
    )
    suspend fun getValidPrograms(playListId: String, timeStamp: Long, offset: Int, limit: Int): List<ProgramEntity>

    /**
     * Gets a program by ID.
     *
     * @param id The ID of the program
     * @return The program, or null if not found
     */
    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getProgramById(id: String): ProgramEntity?

    /**
     * Gets programs for a channel.
     *
     * @param channelId The ID of the channel
     * @return A flow of programs for the channel
     */
    @Query("SELECT * FROM programs WHERE channelId = :channelId ORDER BY startTime ASC")
    suspend fun getProgramsForChannel(channelId: String): List<ProgramEntity>

    /**
     * Gets distinct channel IDs from programs for a playlist.
     *
     * @param playlistId The ID of the playlist
     * @return A list of programs with distinct channel IDs
     */
    @Query("SELECT DISTINCT channelId FROM programs WHERE playlistId = :playlistId")
    suspend fun getDistinctChannelIds(playlistId: String): List<String>

    /**
     * Gets all channels with the count of their valid programs for a playlist.
     *
     * @param playlistId The ID of the playlist
     * @param timeStamp The current timestamp to filter valid programs
     * @return A list of pairs, where each pair contains a channel ID and the count of its valid programs
     */
    @Query("""
        SELECT p.channelId, COUNT(p.id) as programCount
        FROM programs p
        WHERE p.playlistId = :playlistId AND (p.startTime <= :timeStamp OR p.endTime <= :timeStamp)
        GROUP BY p.channelId
    """)
    suspend fun getChannelsWithValidProgramCounts(playlistId: String, timeStamp: Long): List<ChannelWithProgramCount>
    /**
     * Gets programs for a channel within a time range.
     *
     * @param channelId The ID of the channel
     * @param startTime The start time of the range
     * @param endTime The end time of the range
     * @return A flow of programs for the channel within the time range
     */
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime >= :startTime AND endTime <= :endTime ORDER BY startTime ASC")
    suspend fun getProgramsForChannelInTimeRange(
        channelId: String,
        startTime: Long,
        endTime: Long,
    ): List<ProgramEntity>

    /**
     * Gets current and upcoming programs for a channel.
     *
     * @param channelId The ID of the channel
     * @param currentTime The current time
     * @return A flow of current and upcoming programs for the channel
     */
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND endTime > :currentTime ORDER BY startTime ASC")
    suspend fun getCurrentAndUpcomingProgramsForChannel(
        channelId: String,
        currentTime: Long,
    ): List<ProgramEntity>

    /**
     * Gets the current program for a channel.
     *
     * @param channelId The ID of the channel
     * @param currentTime The current time
     * @return The current program, or null if not found
     */
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime LIMIT 1")
    suspend fun getCurrentProgramForChannel(channelId: String, currentTime: Long): ProgramEntity?

    /**
     * Inserts a program.
     *
     * @param program The program to insert
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertProgram(program: ProgramEntity)

    /**
     * Inserts multiple programs.
     *
     * @param programs The programs to insert
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertPrograms(programs: List<ProgramEntity>)

    /**
     * Deletes a program.
     *
     * @param program The program to delete
     */
    @Delete
    suspend fun deleteProgram(program: ProgramEntity)

    /**
     * Deletes a program by ID.
     *
     * @param id The ID of the program to delete
     */
    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteProgramById(id: String)

    /**
     * Deletes all programs for a channel.
     *
     * @param channelId The ID of the channel
     */
    @Query("DELETE FROM programs WHERE channelId = :channelId")
    suspend fun deleteProgramsForChannel(channelId: String)

    /**
     * Deletes all programs for a playlist.
     *
     * @param playlistId The ID of the playlist
     */
    @Query("DELETE FROM programs WHERE playlistId = :playlistId")
    suspend fun deleteProgramsForPlaylist(playlistId: String)
}

/**
 * Data class to hold the result of the query for channels with their valid program counts.
 */
data class ChannelWithProgramCount(
    val channelId: String,
    val programCount: Int
)