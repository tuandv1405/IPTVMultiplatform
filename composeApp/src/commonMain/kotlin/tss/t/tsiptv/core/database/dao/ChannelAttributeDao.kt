package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.ChannelAttributeEntity

/**
 * DAO for accessing channel attribute data in the database.
 */
@Dao
interface ChannelAttributeDao {
    /**
     * Gets all attributes for a channel.
     *
     * @param channelId The ID of the channel to get attributes for
     * @return A flow of attributes for the given channel
     */
    @Query("SELECT * FROM channel_attributes WHERE channelId = :channelId")
    fun getAttributesForChannel(channelId: String): Flow<List<ChannelAttributeEntity>>

    /**
     * Inserts or updates a channel attribute.
     *
     * @param attribute The attribute to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAttribute(attribute: ChannelAttributeEntity)

    /**
     * Inserts or updates multiple channel attributes.
     *
     * @param attributes The attributes to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAttributes(attributes: List<ChannelAttributeEntity>)

    /**
     * Deletes a channel attribute.
     *
     * @param attribute The attribute to delete
     */
    @Delete
    suspend fun deleteAttribute(attribute: ChannelAttributeEntity)

    /**
     * Deletes all attributes for a channel.
     *
     * @param channelId The ID of the channel to delete attributes for
     */
    @Query("DELETE FROM channel_attributes WHERE channelId = :channelId")
    suspend fun deleteAttributesForChannel(channelId: String)

    /** One-shot read, for comparing what each import path stored. */
    @Query("SELECT * FROM channel_attributes WHERE channelId = :channelId ORDER BY id")
    suspend fun getAttributesForChannelOnce(channelId: String): List<ChannelAttributeEntity>

    /**
     * Deletes the attributes of every channel in a playlist. Done explicitly rather than relying
     * on the foreign key's cascade, which only runs when the connection enforces foreign keys.
     */
    @Query("DELETE FROM channel_attributes WHERE channelId IN (SELECT id FROM channel WHERE playlistId = :playlistId)")
    suspend fun deleteAttributesForPlaylist(playlistId: String)
}