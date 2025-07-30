package tss.t.tsiptv.core.parser.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.core.parser.XMLTVEPGParser.Companion.localDateTimeFormat

/**
 * Serializable models for XMLTV EPG format.
 * These models are used with kotlinx-serialization-xml to parse XMLTV content.
 */

/**
 * Root XMLTV document
 */
@Serializable
@XmlSerialName("tv")
data class XMLTVDocument(
    @XmlElement(true)
    @SerialName("channel")
    val channel: List<XMLTVChannel> = emptyList(),
    @XmlElement(true)
    @SerialName("programme")
    val programme: List<XMLTVProgramme> = emptyList(),
    val date: String? = null,
    @XmlElement(false)
    @SerialName("generator-info-name")
    val generatorInfoName: String? = null,
    @XmlElement(false)
    @SerialName("source-info-name")
    val sourceInfoName: String? = null,
    @XmlElement(false)
    @SerialName("source-info-url")
    val sourceInfoUrl: String? = null,
)

/**
 * XMLTV Channel
 */
@Serializable
@XmlSerialName("channel")
data class XMLTVChannel(
    val id: String,
    @XmlElement(true)
    @XmlSerialName("display-name")
    val displayName: XMLTVDisplayName,
    @XmlElement(true)
    @SerialName("display-number")
    val displayNumber: String? = null,
    @XmlElement(true)
    val icon: XMLTVIcon? = null,
    val lang: String? = null,
)

/**
 * XMLTV Display Name
 */
@Serializable
@XmlSerialName("display-name")
data class XMLTVDisplayName(
    @XmlValue(true)
    val value: String?,
    val lang: String? = null,
)

/**
 * XMLTV Icon
 */
@Serializable
@XmlSerialName("icon")
data class XMLTVIcon(
    val src: String,
    val width: String? = null,
    val height: String? = null,
)

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
            category = category?.value
        )
    }

    /**
     * Parses a date-time string in XMLTV format into a timestamp.
     * XMLTV format is typically YYYYMMDDHHMMSS +/-HHMM
     */
    private fun parseXMLTVDateTime(dateTime: String): Long? {
        return try {
            // Handle different timezone formats
            LocalDateTime
                .parse(
                    input = dateTime,
                    format = localDateTimeFormat
                )
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * XMLTV Title
 */
@Serializable
@XmlSerialName("title")
data class XMLTVTitle(
    @XmlValue(true)
    val value: String,
    val lang: String? = null,
)

/**
 * XMLTV Description
 */
@Serializable
@XmlSerialName("desc")
data class XMLTVDesc(
    @XmlValue(true)
    val value: String,
    val lang: String? = null,
)

/**
 * XMLTV Category
 */
@Serializable
@XmlSerialName("category")
data class XMLTVCategory(
    @XmlValue(true)
    val value: String,
    val lang: String? = null,
)

/**
 * XMLTV Credits
 */
@Serializable
@XmlSerialName("credits")
data class XMLTVCredits(
    @XmlElement(true)
    @SerialName("director")
    val director: String?,
    @XmlElement(true)
    @SerialName("actor")
    val actor: List<String>? = null,
)
