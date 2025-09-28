package tss.t.tsiptv.core.parser

import kotlinx.serialization.json.*
import tss.t.tsiptv.core.parser.model.*
import tss.t.tsiptv.core.parser.model.exception.IPTVParserException

/**
 * Implementation of IPTVParser for JSON format using Kotlin Serialization.
 */
class JSONParser : IPTVParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override fun parse(content: String): IPTVPlaylist {
        if (!content.trimStart().startsWith("{") && !content.trimStart().startsWith("[")) {
            throw IPTVParserException("Invalid JSON format: missing opening brace or bracket")
        }

        try {
            // Try to parse as a JSONPlaylist object first
            if (content.trimStart().startsWith("{")) {
                return parseJsonObject(content)
            } else {
                return parseJsonArray(content)
            }
        } catch (e: Exception) {
            throw IPTVParserException("Failed to parse JSON content: ${e.message}")
        }
    }

    private fun parseJsonObject(content: String): IPTVPlaylist {
        try {
            // Try to parse as a JSONPlaylist object
            val playlist = json.decodeFromString<JSONPlaylist>(content)
            return playlist.toIPTVPlaylist()
        } catch (e: Exception) {
            // If that fails, try to parse as a JSONChannelArray
            try {
                val channelArray = json.decodeFromString<JSONChannelArray>(content)
                val playlist = JSONPlaylist(channels = channelArray.channels)
                return playlist.toIPTVPlaylist()
            } catch (e2: Exception) {
                // If that fails, try to parse as a JSONGroupArray
                try {
                    val groupArray = json.decodeFromString<JSONGroupArray>(content)
                    val playlist = JSONPlaylist(groups = groupArray.groups)
                    return playlist.toIPTVPlaylist()
                } catch (e3: Exception) {
                    // If all parsing attempts fail, throw an exception
                    throw IPTVParserException("Failed to parse JSON content: ${e.message}, ${e2.message}, ${e3.message}")
                }
            }
        }
    }

    private fun parseJsonArray(content: String): IPTVPlaylist {
        try {
            // Try to parse as an array of JSONChannel objects
            val channels = json.decodeFromString<List<JSONChannel>>(content)
            val playlist = JSONPlaylist(channels = channels)
            return playlist.toIPTVPlaylist()
        } catch (e: Exception) {
            // If that fails, try to parse as an array of JSONGroup objects
            try {
                val groups = json.decodeFromString<List<JSONGroup>>(content)
                val playlist = JSONPlaylist(groups = groups)
                return playlist.toIPTVPlaylist()
            } catch (e2: Exception) {
                // If all parsing attempts fail, throw an exception
                throw IPTVParserException("Failed to parse JSON array content: ${e.message}, ${e2.message}")
            }
        }
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.JSON
    }
}
