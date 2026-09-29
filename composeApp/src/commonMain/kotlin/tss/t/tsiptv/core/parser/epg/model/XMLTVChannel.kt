package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * XMLTV `<channel>`. The DTD allows several `display-name`s (one per language, or the
 * number and the name), so they are a list.
 */
@Serializable
@XmlSerialName("channel")
data class XMLTVChannel(
    val id: String,
    @XmlElement(true)
    @XmlSerialName("display-name")
    val displayName: List<XMLTVDisplayName> = emptyList(),
    @XmlElement(true)
    @SerialName("display-number")
    val displayNumber: String? = null,
    @XmlElement(true)
    val icon: XMLTVIcon? = null,
    val lang: String? = null,
)
