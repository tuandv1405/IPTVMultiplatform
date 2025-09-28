package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

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