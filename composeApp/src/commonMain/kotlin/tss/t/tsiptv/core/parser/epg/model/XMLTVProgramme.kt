package tss.t.tsiptv.core.parser.epg.model

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
    // One per language is allowed, and common in multi-language guides.
    @XmlElement(true)
    val title: List<XMLTVTitle> = emptyList(),
    @XmlElement(true)
    val desc: List<XMLTVDesc> = emptyList(),
    @XmlElement(true)
    val category: List<XMLTVCategory>? = null,
    @XmlElement(true)
    val icon: XMLTVIcon? = null,
    @XmlElement(true)
    val credits: XMLTVCredits? = null,
) {
    /**
     * Convert to IPTVProgram model
     */
    /**
     * @param preferredLanguage `lang` of the title / description to prefer; otherwise the first
     * @param channelIcon the `<channel><icon>` of this programme's channel, used when the programme
     *   has no icon of its own (the Programs tab shows it as the channel's avatar)
     */
    fun toIPTVProgram(preferredLanguage: String? = null, channelIcon: String? = null): IPTVProgram? {
        val startTime = parseXMLTVDateTime(start)
        val endTime = parseXMLTVDateTime(stop)

        if (startTime == null || endTime == null) {
            return null
        }
        val programId = "${channel}_${startTime}"

        fun <T> List<T>.pick(lang: (T) -> String?): T? =
            firstOrNull { preferredLanguage != null && lang(it).equals(preferredLanguage, ignoreCase = true) }
                ?: firstOrNull()

        return IPTVProgram(
            id = programId,
            channelId = channel,
            title = title.pick { it.lang }?.value ?: "Unknown Program",
            description = desc.pick { it.lang }?.value,
            startTime = startTime,
            endTime = endTime,
            category = category?.map {
                it.value
            },
            logo = icon?.src?.takeIf { it.isNotBlank() } ?: channelIcon,
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
        return XMLTVEPGParser.parseXMLTVDateTime(dateTime)
    }
}
