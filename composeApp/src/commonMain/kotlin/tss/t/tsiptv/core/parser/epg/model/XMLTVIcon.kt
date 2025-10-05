package tss.t.tsiptv.core.parser.epg.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * XMLTV Icon
 */
@Serializable
@XmlSerialName("icon")
data class XMLTVIcon(
    @XmlSerialName(
        value = "src",
    )
    val src: String,
    val width: String? = null,
    val height: String? = null,
)