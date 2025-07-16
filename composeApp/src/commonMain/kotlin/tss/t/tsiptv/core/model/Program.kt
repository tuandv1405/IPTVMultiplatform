package tss.t.tsiptv.core.model

import kotlinx.serialization.Serializable

/**
 * Data class representing a TV program.
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
@Serializable
data class Program(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
    val playlistId: String
)