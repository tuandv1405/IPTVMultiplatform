package tss.t.tsiptv.core.database.entity

import androidx.room.*
import tss.t.tsiptv.core.model.Channel
import kotlin.String

/**
 * Room entity for a playlist.
 *
 * @property id The unique ID of the playlist (URL of the playlist)
 * @property name The name of the playlist
 * @property url The URL of the playlist
 * @property lastUpdated The timestamp when the playlist was last updated
 * @property format The format of the playlist (M3U, XML, JSON)
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long,
    val format: String
)

/**
 * Room entity for a channel.
 *
 * @property id The unique ID of the channel
 * @property name The name of the channel
 * @property url The URL of the channel
 * @property logoUrl The URL of the channel's logo
 * @property categoryId The ID of the category the channel belongs to
 * @property playlistId The ID of the playlist the channel belongs to
 * @property isFavorite Whether the channel is a favorite
 * @property lastWatched The timestamp when the channel was last watched, or null if never watched
 */
@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("categoryId")
    ]
)
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String?,
    val categoryId: String?,
    val playlistId: String,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null
)

/**
 * Room entity for a category.
 *
 * @property id The unique ID of the category
 * @property name The name of the category
 * @property playlistId The ID of the playlist the category belongs to
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId")
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val playlistId: String
)

/**
 * Room entity for channel attributes.
 *
 * @property id The unique ID of the attribute
 * @property channelId The ID of the channel the attribute belongs to
 * @property attrKey The key of the attribute
 * @property attrValue The value of the attribute
 */
@Entity(
    tableName = "channel_attributes",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("channelId")
    ]
)
data class ChannelAttributeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: String,
    val attrKey: String,
    val attrValue: String
)

/**
 * Room entity for a program in a program schedule.
 *
 * @property id The unique ID of the program
 * @property channelId The ID of the channel the program belongs to
 * @property title The title of the program
 * @property description The description of the program
 * @property startTime The start time of the program (in milliseconds since epoch)
 * @property endTime The end time of the program (in milliseconds since epoch)
 * @property category The category of the program
 * @property playlistId The ID of the playlist the program belongs to
 */
@Entity(
    tableName = "programs",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("channelId"),
        Index("playlistId")
    ]
)
data class ProgramEntity(
    @PrimaryKey
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val category: String?,
    val playlistId: String
)

/**
 * Room entity for channel history tracking.
 *
 * @property id The unique ID of the history entry (auto-generated)
 * @property channelId The ID of the channel that was played
 * @property playlistId The ID of the playlist the channel belongs to
 * @property lastPlayedTimestamp The timestamp when the channel was last played
 * @property totalPlayedTimeMs The total time the channel has been played (in milliseconds)
 * @property playCount The number of times the channel has been played
 * @property currentPositionMs The current playback position when last played (in milliseconds)
 * @property totalDurationMs The total duration of the media item (in milliseconds)
 */
@Entity(
    tableName = "channel_history",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("channelId"),
        Index("playlistId"),
        Index("lastPlayedTimestamp"),
        Index(value = ["channelId", "playlistId"], unique = true)
    ]
)
data class ChannelHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: String,
    val playlistId: String,
    val lastPlayedTimestamp: Long,
    val totalPlayedTimeMs: Long = 0,
    val playCount: Int = 1,
    val currentPositionMs: Long = 0,
    val totalDurationMs: Long = 0
)

// Conversion functions for ChannelHistory
fun ChannelHistoryEntity.toChannelHistory() = tss.t.tsiptv.core.model.ChannelHistory(
    id = id,
    channelId = channelId,
    playlistId = playlistId,
    lastPlayedTimestamp = lastPlayedTimestamp,
    totalPlayedTimeMs = totalPlayedTimeMs,
    playCount = playCount,
    currentPositionMs = currentPositionMs,
    totalDurationMs = totalDurationMs
)

fun tss.t.tsiptv.core.model.ChannelHistory.toChannelHistoryEntity() = ChannelHistoryEntity(
    id = id,
    channelId = channelId,
    playlistId = playlistId,
    lastPlayedTimestamp = lastPlayedTimestamp,
    totalPlayedTimeMs = totalPlayedTimeMs,
    playCount = playCount,
    currentPositionMs = currentPositionMs,
    totalDurationMs = totalDurationMs
)

/**
 * Data class for joined Channel and ChannelHistory query results.
 * Contains complete channel information along with history data.
 */
data class ChannelWithHistory(
    // Channel information
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val logoUrl: String?,
    val categoryId: String?,
    val playlistId: String,
    val isFavorite: Boolean,
    val lastWatched: Long?,

    // History information
    val historyId: Long,
    val lastPlayedTimestamp: Long,
    val totalPlayedTimeMs: Long,
    val playCount: Int,
    val currentPositionMs: Long,
    val totalDurationMs: Long,
) {
    fun getChannel() = Channel(
        id = channelId,
        name = channelName,
        url = channelUrl,
        logoUrl = logoUrl,
        categoryId = categoryId,
        playlistId = playlistId,
        isFavorite = isFavorite,
        lastWatched = lastPlayedTimestamp
    )
}
