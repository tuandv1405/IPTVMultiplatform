package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.model.IPTVProgram

/**
 * Parser for Electronic Program Guide (EPG) data.
 * This class is responsible for parsing program schedules from EPG data sources.
 */
interface EPGParser {
    /**
     * Parses EPG data from the given content.
     *
     * @param content The EPG data content as a string
     * @return A list of program schedules
     * @throws tss.t.tsiptv.core.parser.model.exception.EPGParserException if parsing fails
     */
    fun parse(content: String): List<IPTVProgram>

    /**
     * Gets the supported format for this parser.
     *
     * @return The supported format
     */
    fun getSupportedFormat(): EPGFormat
}

/**
 * Enum representing the format of an EPG data source.
 */
enum class EPGFormat {
    XML,
    JSON,
}

