package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

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