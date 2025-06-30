package tss.t.tsiptv.core.parser

/**
 * Interface for parsing IPTV playlists.
 * This is a platform-independent interface that will have implementations for different formats.
 */
interface IPTVParser {
    /**
     * Parses an IPTV playlist from a string.
     *
     * @param content The playlist content as a string
     * @return The parsed playlist
     * @throws IPTVParserException if parsing fails
     */
    fun parse(content: String): IPTVPlaylist

    /**
     * Gets the supported format for this parser.
     *
     * @return The supported format
     */
    fun getSupportedFormat(): IPTVFormat
}

/**
 * Enum representing the format of an IPTV playlist.
 */
enum class IPTVFormat {
    M3U,
    XML,
    JSON,
    UNKNOWN
}

/**
 * Data class representing an IPTV playlist.
 *
 * @property name The name of the playlist
 * @property channels The channels in the playlist
 * @property groups The channel groups in the playlist
 * @property programs The program schedules in the playlist
 * @property epgUrl The URL of the EPG (Electronic Program Guide) for this playlist
 */
data class IPTVPlaylist(
    val name: String,
    val channels: List<IPTVChannel>,
    val groups: List<IPTVGroup>,
    val programs: List<IPTVProgram> = emptyList(),
    val epgUrl: String? = null
)

/**
 * Data class representing an IPTV channel.
 *
 * @property id The unique ID of the channel
 * @property name The name of the channel
 * @property url The URL of the channel
 * @property logoUrl The URL of the channel's logo
 * @property groupTitle The title of the group the channel belongs to
 * @property epgId The ID of the channel in the EPG (Electronic Program Guide)
 * @property attributes Additional attributes of the channel
 */
data class IPTVChannel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val epgId: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Data class representing an IPTV group.
 *
 * @property id The unique ID of the group
 * @property title The title of the group
 */
data class IPTVGroup(
    val id: String,
    val title: String
)

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
    val category: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Exception thrown when parsing an IPTV playlist fails.
 *
 * @property message The error message
 */
class IPTVParserException(override val message: String) : Exception(message)

/**
 * Factory for creating IPTV parsers.
 */
object IPTVParserFactory {
    /**
     * Creates an IPTV parser for the specified format.
     *
     * @param format The format to create a parser for
     * @return The created parser
     * @throws IllegalArgumentException if the format is not supported
     */
    fun createParser(format: IPTVFormat): IPTVParser {
        return when (format) {
            IPTVFormat.M3U -> M3UParser()
            IPTVFormat.XML -> XMLParser()
            IPTVFormat.JSON -> JSONParser()
            IPTVFormat.UNKNOWN -> throw IllegalArgumentException("Unknown format")
        }
    }

    /**
     * Detects the format of an IPTV playlist from its content.
     *
     * @param content The playlist content as a string
     * @return The detected format
     */
    fun detectFormat(content: String): IPTVFormat {
        return when {
            content.trimStart().startsWith("#EXTM3U") -> IPTVFormat.M3U
            content.trimStart().startsWith("<?xml") || content.trimStart().startsWith("<tv") -> IPTVFormat.XML
            content.trimStart().startsWith("{") -> IPTVFormat.JSON
            else -> IPTVFormat.UNKNOWN
        }
    }

    /**
     * Creates an IPTV parser for the content.
     *
     * @param content The playlist content as a string
     * @return The created parser
     * @throws IllegalArgumentException if the format is not supported
     */
    fun createParserForContent(content: String): IPTVParser {
        val format = detectFormat(content)
        return createParser(format)
    }
}
