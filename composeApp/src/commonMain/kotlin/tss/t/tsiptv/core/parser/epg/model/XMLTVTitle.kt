package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

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