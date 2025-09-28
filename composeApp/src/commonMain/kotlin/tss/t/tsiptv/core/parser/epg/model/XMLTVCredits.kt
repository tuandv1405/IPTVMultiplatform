package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Serializable models for XMLTV EPG format.
 * These models are used with kotlinx-serialization-xml to parse XMLTV content.
 */

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
