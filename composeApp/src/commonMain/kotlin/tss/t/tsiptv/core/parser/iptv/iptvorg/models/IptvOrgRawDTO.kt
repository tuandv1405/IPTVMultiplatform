package tss.t.tsiptv.core.parser.iptv.iptvorg.models

import kotlinx.serialization.SerialName

data class IptvOrgRawDTO(
    @SerialName("channel")
    val channel: String?,
    @SerialName("feed")
    val feed: String?,
    @SerialName("quality")
    val quality: String?,
    @SerialName("referrer")
    val referrer: String?,
    @SerialName("title")
    val title: String,
    @SerialName("url")
    val url: String,
    @SerialName("user_agent")
    val userAgent: String? = null,
)
