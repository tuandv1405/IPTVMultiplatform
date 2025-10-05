package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

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