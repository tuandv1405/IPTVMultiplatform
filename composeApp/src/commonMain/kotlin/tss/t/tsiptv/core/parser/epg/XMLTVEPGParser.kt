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
import kotlin.time.ExperimentalTime

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
        
        // Pre-process the XML content to escape unescaped '&' characters
        // This fixes the "unterminated entity ref" error when URLs contain unescaped '&'
        val processedContent = escapeUnescapedAmpersands(content)
        
        val xmltvDocument = xml.decodeFromString<XMLTVDocument>(processedContent)
        return xmltvDocument.programme.mapNotNull {
            it.toIPTVProgram()
        }
    }

    /**
     * Escapes unescaped '&' characters in XML content while preserving valid XML entities.
     * This prevents "unterminated entity ref" errors when URLs contain unescaped '&' characters.
     *
     * @param content The XML content to process
     * @return The XML content with unescaped '&' characters properly escaped
     */
    private fun escapeUnescapedAmpersands(content: String): String {
        // Regex to find '&' characters that are not part of valid XML entities
        // Valid XML entities: &amp; &lt; &gt; &quot; &apos; and numeric entities like &#123; or &#x1A;
        val unescapedAmpersandRegex = Regex("&(?!(?:amp|lt|gt|quot|apos|#\\d+|#x[0-9A-Fa-f]+);)")
        
        return content.replace(unescapedAmpersandRegex, "&amp;")
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
    @OptIn(ExperimentalTime::class)
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