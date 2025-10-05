package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.epg.JSONEPGParser
import tss.t.tsiptv.core.parser.epg.XMLTVEPGParser

/**
 * Factory for creating EPG parsers.
 */
object EPGParserFactory {
    /**
     * Creates an EPG parser for the specified format.
     *
     * @param format The format to create a parser for
     * @return The created parser
     * @throws IllegalArgumentException if the format is not supported
     */
    fun createParser(format: EPGFormat): EPGParser {
        return when (format) {
            EPGFormat.XML -> XMLTVEPGParser()
            EPGFormat.JSON -> JSONEPGParser()
        }
    }

    /**
     * Detects the format of EPG data from its content.
     *
     * @param content The EPG data content as a string
     * @return The detected format
     */
    fun detectFormat(content: String): EPGFormat {
        return when {
            content.trimStart().startsWith("<?xml") -> {
                EPGFormat.XML
            }

            content.trimStart().startsWith("{") -> EPGFormat.JSON
            else -> EPGFormat.XML
        }
    }

    /**
     * Creates an EPG parser for the content.
     *
     * @param content The EPG data content as a string
     * @return The created parser
     * @throws IllegalArgumentException if the format is not supported
     */
    fun createParserForContent(content: String): EPGParser {
        val format = detectFormat(content)
        return createParser(format)
    }
}