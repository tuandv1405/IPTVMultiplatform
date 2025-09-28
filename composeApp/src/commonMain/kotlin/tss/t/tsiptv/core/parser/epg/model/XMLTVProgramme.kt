package tss.t.tsiptv.core.parser.epg.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import tss.t.tsiptv.core.parser.epg.XMLTVEPGParser
import tss.t.tsiptv.core.parser.model.IPTVProgram

/**
 * XMLTV Programme
 */
@Serializable
@XmlSerialName("programme")
data class XMLTVProgramme(
    val channel: String,
    @SerialName("channel-number")
    val channelNumber: String? = null,
    val start: String,
    val stop: String,
    @XmlElement(true)
    val title: XMLTVTitle? = null,
    @XmlElement(true)
    val desc: XMLTVDesc? = null,
    @XmlElement(true)
    val category: XMLTVCategory? = null,
    @XmlElement(true)
    val icon: XMLTVIcon? = null,
    @SerialName("credits")
    val credits: XMLTVCredits? = null,
) {
    /**
     * Convert to IPTVProgram model
     */
    fun toIPTVProgram(): IPTVProgram? {
        val startTime = parseXMLTVDateTime(start)
        val endTime = parseXMLTVDateTime(stop)

        if (startTime == null || endTime == null) {
            return null
        }
        val programId = "${channel}_${startTime}"

        return IPTVProgram(
            id = programId,
            channelId = channel,
            title = title?.value ?: "Unknown Program",
            description = desc?.value,
            startTime = startTime,
            endTime = endTime,
            category = category?.value,
            logo = icon?.src,
            credits = credits?.let {
                IPTVProgram.Credits(
                    director = it.director,
                    actors = it.actor
                )
            }
        )
    }

    /**
     * Parses a date-time string in XMLTV format into a timestamp.
     * XMLTV format is typically YYYYMMDDHHMMSS +/-HHMM
     */
    private fun parseXMLTVDateTime(dateTime: String): Long? {
        return try {
            // Handle different timezone formats
            LocalDateTime.Companion
                .parse(
                    input = dateTime,
                    format = XMLTVEPGParser.Companion.localDateTimeFormat
                )
                .toInstant(TimeZone.Companion.currentSystemDefault())
                .toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }
    }
}