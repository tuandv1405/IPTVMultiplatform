package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

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