package tss.t.tsiptv.core.parser.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class representing a program in an IPTV program schedule.
 *
 * @property id The unique ID of the program
 * @property channelId The ID of the channel the program belongs to
 * @property title The title of the program
 * @property description The description of the program
 * @property startTime The start time of the program (in milliseconds since epoch)
 * @property endTime The end time of the program (in milliseconds since epoch)
 * @property category The category of the program
 * @property attributes Additional attributes of the program
 */
data class IPTVProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: List<String>? = null,
    val logo: String? = null,
    val credits: Credits? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    var startTimeStr: String? = null
    var endTimeStr: String? = null

    @Serializable
    data class Credits(
        @SerialName("director")
        val director: String? = null,
        @SerialName("actors")
        val actors: List<String>? = null,
    )
}
