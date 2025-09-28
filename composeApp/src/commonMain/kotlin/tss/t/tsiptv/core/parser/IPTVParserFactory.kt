package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.core.parser.iptv.iptvorg.IptvOrgParser
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.model.IPTVFormat

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
            IPTVFormat.XSPF -> XSPFParser()
            IPTVFormat.JSON_IPTV_ORG -> IptvOrgParser(
                "",
                NetworkClientFactory.get().getNetworkClient()
            )

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
            content.trimStart().startsWith("<?xml") && content.contains("<playlist") &&
                    (content.contains("xmlns=\"http://xspf.org/ns/0/\"") ||
                            content.contains("xmlns:vlc=\"http://www.videolan.org/vlc/playlist/ns/0/\"")) -> IPTVFormat.XSPF

            content.trimStart().startsWith("<?xml") || content.trimStart()
                .startsWith("<tv") -> IPTVFormat.XML

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