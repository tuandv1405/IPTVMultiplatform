package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVPlaylist

/**
 * Interface for parsing IPTV playlists.
 * This is a platform-independent interface that will have implementations for different formats.
 */
interface IPTVParser {

    fun parseFromUrl(url: String): IPTVPlaylist {
        throw IllegalStateException("Not implement yet")
    }

    /**
     * Parses an IPTV playlist from a string.
     *
     * @param content The playlist content as a string
     * @return The parsed playlist
     * @throws tss.t.tsiptv.core.parser.model.exception.IPTVParserException if parsing fails
     */
    fun parse(content: String): IPTVPlaylist

    /**
     * Gets the supported format for this parser.
     *
     * @return The supported format
     */
    fun getSupportedFormat(): IPTVFormat
}

