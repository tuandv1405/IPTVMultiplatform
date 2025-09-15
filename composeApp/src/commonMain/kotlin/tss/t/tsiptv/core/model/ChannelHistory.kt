package tss.t.tsiptv.core.model

import kotlinx.serialization.Serializable

/**
 * Data class representing channel history.
 *
 * @property id The unique ID of the history entry
 * @property channelId The ID of the channel that was played
 * @property playlistId The ID of the playlist the channel belongs to
 * @property lastPlayedTimestamp The timestamp when the channel was last played
 * @property totalPlayedTimeMs The total time the channel has been played (in milliseconds)
 * @property playCount The number of times the channel has been played
 * @property currentPositionMs The current playback position when last played (in milliseconds)
 * @property totalDurationMs The total duration of the media item (in milliseconds)
 */
@Serializable
data class ChannelHistory(
    val id: Long = 0,
    val channelId: String,
    val playlistId: String,
    val lastPlayedTimestamp: Long,
    val totalPlayedTimeMs: Long = 0,
    val playCount: Int = 1,
    val currentPositionMs: Long = 0,
    val totalDurationMs: Long = 0
)
