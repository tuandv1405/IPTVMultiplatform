package tss.t.tsiptv.core.parser.epg

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
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
        val unescapedAmpersandRegex = Regex("""&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9A-Fa-f]+);)""")

        return content.replace(unescapedAmpersandRegex, "&amp;")
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.XML
    }

    companion object {
        /**
         * Kept for callers that need a [kotlinx.datetime.format.DateTimeFormat] for the
         * offset-less XMLTV date-time form (`YYYYMMDDHHMMSS`, optionally followed by `+0700`).
         * Prefer [parseXMLTVDateTime], which accepts any XMLTV offset.
         */
        val localDateTimeFormat = LocalDateTime.Format {
            year();monthNumber();dayOfMonth();hour();minute();second()
            optional {
                char(' ')
                char('+');char('0');char('7');char('0');char('0')
            }
        }

        /**
         * `YYYYMMDD[HH[MM[SS]]]` followed by an optional ` +/-HHMM` (or ` +/-HH:MM`) UTC offset,
         * which is what the XMLTV DTD allows for `start`/`stop` attributes.
         */
        private val XMLTV_DATE_TIME_REGEX = Regex(
            """^(\d{4})(\d{2})(\d{2})(\d{2})?(\d{2})?(\d{2})?(?:\s*([+-])(\d{2}):?(\d{2}))?$"""
        )

        /**
         * Parses a date-time string in XMLTV format into a timestamp.
         *
         * When the string carries a UTC offset it is honoured, so `20230101120000 +0000` and
         * `20230101190000 +0700` map to the same instant. When it carries none, the value is
         * interpreted in the system's current time zone.
         *
         * @param dateTime The date-time string
         * @return The timestamp in milliseconds since epoch, or null if parsing fails
         */
        @OptIn(ExperimentalTime::class)
        fun parseXMLTVDateTime(dateTime: String): Long? {
            val match = XMLTV_DATE_TIME_REGEX.matchEntire(dateTime.trim()) ?: return null
            val (year, month, day, hour, minute, second, sign, offsetHours, offsetMinutes) =
                match.destructured

            return try {
                val localDateTime = LocalDateTime(
                    year = year.toInt(),
                    monthNumber = month.toInt(),
                    dayOfMonth = day.toInt(),
                    hour = hour.toIntOrNull() ?: 0,
                    minute = minute.toIntOrNull() ?: 0,
                    second = second.toIntOrNull() ?: 0
                )

                if (sign.isEmpty()) {
                    localDateTime.toInstant(TimeZone.currentSystemDefault())
                } else {
                    val totalMinutes = offsetHours.toInt() * 60 + offsetMinutes.toInt()
                    val signedMinutes = if (sign == "-") -totalMinutes else totalMinutes
                    localDateTime.toInstant(UtcOffset(minutes = signedMinutes))
                }.toEpochMilliseconds()
            } catch (_: IllegalArgumentException) {
                // Out-of-range field (e.g. month 13, hour 25) or an unrepresentable offset.
                null
            }
        }
    }
}
