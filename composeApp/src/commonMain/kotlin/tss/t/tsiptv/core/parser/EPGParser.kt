package tss.t.tsiptv.core.parser

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.datetime.toInstant
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XML
import tss.t.tsiptv.core.parser.model.XMLTVDocument

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
     * @throws EPGParserException if parsing fails
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
    XMLTV,
    UNKNOWN
}

/**
 * Exception thrown when parsing EPG data fails.
 *
 * @property message The error message
 */
class EPGParserException(override val message: String) : Exception(message)

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
            EPGFormat.XML -> XMLEPGParser()
            EPGFormat.JSON -> JSONEPGParser()
            EPGFormat.XMLTV -> XMLTVEPGParser()
            EPGFormat.UNKNOWN -> throw IllegalArgumentException("Unknown EPG format")
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
                if (content.contains("<tv") || content.contains("<channel") || content.contains("<programme")) {
                    EPGFormat.XMLTV
                } else {
                    EPGFormat.XML
                }
            }

            content.trimStart().startsWith("{") -> EPGFormat.JSON
            else -> EPGFormat.UNKNOWN
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

/**
 * Implementation of EPGParser for XML format.
 */
class XMLEPGParser : EPGParser {
    override fun parse(content: String): List<IPTVProgram> {
        if (!content.trimStart().startsWith("<?xml")) {
            throw EPGParserException("Invalid XML format: missing XML header")
        }

        // Simple implementation for now
        // In a real implementation, we would use a proper XML parser library
        val programs = mutableListOf<IPTVProgram>()

        // TODO: Implement XML parsing logic

        return programs
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.XML
    }
}

/**
 * Implementation of EPGParser for JSON format.
 */
class JSONEPGParser : EPGParser {
    override fun parse(content: String): List<IPTVProgram> {
        if (!content.trimStart().startsWith("{")) {
            throw EPGParserException("Invalid JSON format: missing opening brace")
        }

        // Simple implementation for now
        // In a real implementation, we would use a proper JSON parser library
        val programs = mutableListOf<IPTVProgram>()

        // TODO: Implement JSON parsing logic

        return programs
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.JSON
    }
}

/**
 * Implementation of EPGParser for XMLTV format.
 * XMLTV is a common format for TV listings.
 */
class XMLTVEPGParser : EPGParser {
    override fun parse(content: String): List<IPTVProgram> {
        val xml = XML {
            autoPolymorphic = true
            isCollectingNSAttributes = false
        }
        val xmltvDocument = xml.decodeFromString<XMLTVDocument>(content)
        println("TuanDV: ${xmltvDocument.programme.size}")
        println("TuanDV: ${xmltvDocument.channel.size}")
        return xmltvDocument.programme.mapNotNull {
            it.toIPTVProgram()
        }
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.XMLTV
    }

    /**
     * Parses a date-time string in XMLTV format into a timestamp.
     * XMLTV format is typically YYYYMMDDHHMMSS +/-HHMM
     *
     * @param dateTime The date-time string
     * @return The timestamp in milliseconds since epoch, or null if parsing fails
     */
    private fun parseXMLTVDateTime(dateTime: String): Long? {
        return LocalDateTime
            .parse(
                input = dateTime,
                format = localDateTimeFormat
            )
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }

    companion object {
        val localDateTimeFormat = LocalDateTime.Format {
            year();monthNumber();dayOfMonth();hour();minute();second()
            char(' ')
            optional {
                char('+');char('0');char('7');char('0');char('0')
            }
        }
    }
}
