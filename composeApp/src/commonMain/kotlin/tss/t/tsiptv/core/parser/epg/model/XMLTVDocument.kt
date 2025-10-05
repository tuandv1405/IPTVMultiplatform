package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

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