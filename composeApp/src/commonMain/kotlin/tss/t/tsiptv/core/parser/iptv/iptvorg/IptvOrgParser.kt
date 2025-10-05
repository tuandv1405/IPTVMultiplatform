package tss.t.tsiptv.core.parser.iptv.iptvorg

import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.IPTVParser
import tss.t.tsiptv.core.parser.model.IPTVPlaylist
import tss.t.tsiptv.core.parser.iptv.iptvorg.models.IptvOrgRawDTO

class IptvOrgParser(
    val name: String,
    val networkClient: NetworkClient,
) : IPTVParser {
    override fun parse(content: String): IPTVPlaylist {
        val listIptv: List<IptvOrgRawDTO>? = Json.decodeFromString(content)
        val channels = listIptv?.map { channel ->
            IPTVChannel(
                id = channel.channel ?: "",
                name = channel.channel ?: "",
                url = channel.url,
                logoUrl = "",
                groupTitle = channel.feed,
                groupId = channel.feed ?: "",
                epgId = "",
                attributes = channel.referrer?.let {
                    mapOf(
                        "Referer" to it
                    )
                } ?: emptyMap()
            )
        }
        return IPTVPlaylist(
            name = name,
            channels = channels ?: emptyList(),
            programs = listOf(),
            groups = listOf()
        )
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.JSON_IPTV_ORG
    }
}
