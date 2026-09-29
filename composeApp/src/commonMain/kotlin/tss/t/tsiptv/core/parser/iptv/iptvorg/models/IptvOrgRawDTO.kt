package tss.t.tsiptv.core.parser.iptv.iptvorg.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry of iptv-org's `streams.json`. */
@Serializable
data class IptvOrgRawDTO(
    @SerialName("channel")
    val channel: String? = null,
    @SerialName("feed")
    val feed: String? = null,
    @SerialName("quality")
    val quality: String? = null,
    @SerialName("referrer")
    val referrer: String? = null,
    @SerialName("title")
    val title: String,
    @SerialName("url")
    val url: String,
    @SerialName("user_agent")
    val userAgent: String? = null,
)
