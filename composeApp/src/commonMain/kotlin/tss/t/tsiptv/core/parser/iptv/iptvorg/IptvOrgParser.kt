package tss.t.tsiptv.core.parser.iptv.iptvorg

import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.parser.IPTVParser
import tss.t.tsiptv.core.parser.iptv.iptvorg.models.IptvOrgRawDTO
import tss.t.tsiptv.core.parser.iptv.m3u.HeaderSuffixParser
import tss.t.tsiptv.core.parser.iptv.m3u.IdAllocator
import tss.t.tsiptv.core.parser.iptv.m3u.M3UChannelBuilder
import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVPlaylist

class IptvOrgParser(
    val name: String = "",
) : IPTVParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    override fun parse(content: String): IPTVPlaylist {
        val listIptv: List<IptvOrgRawDTO> = json.decodeFromString(content)
        val ids = IdAllocator()
        val channels = listIptv.map { channel ->
            val base = channel.channel?.takeIf { it.isNotBlank() } ?: M3UChannelBuilder.slug(channel.title)
            IPTVChannel(
                id = ids.allocate(base),
                name = channel.title,
                url = channel.url,
                logoUrl = null,
                groupTitle = channel.feed,
                groupId = channel.feed ?: "",
                epgId = channel.channel,
                headers = HeaderSuffixParser.sanitize(buildMap {
                    channel.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
                    channel.referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
                }),
            )
        }
        return IPTVPlaylist(
            name = name,
            channels = channels,
            programs = listOf(),
            groups = listOf()
        )
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.JSON_IPTV_ORG
    }
}
