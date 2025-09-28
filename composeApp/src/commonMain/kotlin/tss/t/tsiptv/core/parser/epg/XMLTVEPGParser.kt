package tss.t.tsiptv.core.parser.epg

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.datetime.toInstant
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import tss.t.tsiptv.core.parser.EPGFormat
import tss.t.tsiptv.core.parser.EPGParser
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.core.parser.epg.model.XMLTVDocument

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
        return xmltvDocument.programme.mapNotNull {
            it.toIPTVProgram()
        }
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.XML
    }

    /**
     * Parses a date-time string in XMLTV format into a timestamp.
     * XMLTV format is typically YYYYMMDDHHMMSS +/-HHMM
     *
     * @param dateTime The date-time string
     * @return The timestamp in milliseconds since epoch, or null if parsing fails
     */
    private fun parseXMLTVDateTime(dateTime: String): Long? {
        return LocalDateTime.Companion
            .parse(
                input = dateTime,
                format = localDateTimeFormat
            )
            .toInstant(TimeZone.Companion.currentSystemDefault())
            .toEpochMilliseconds()
    }

    companion object {
        val localDateTimeFormat = LocalDateTime.Companion.Format {
            year();monthNumber();dayOfMonth();hour();minute();second()
            char(' ')
            optional {
                char('+');char('0');char('7');char('0');char('0')
            }
        }
    }
}