package tss.t.tsiptv.core.parser

/**
 * Implementation of IPTVParser for M3U format.
 */
class M3UParser : IPTVParser {
    override fun parse(content: String): IPTVPlaylist {
        if (!content.trimStart().startsWith("#EXTM3U")) {
            throw IPTVParserException("Invalid M3U format: missing #EXTM3U header")
        }

        val lines = content.lines()
        val channels = mutableListOf<IPTVChannel>()
        val groups = mutableSetOf<IPTVGroup>()
        var playlistName = "IPTV Playlist"
        var epgUrl: String? = null

        var currentExtInf: String? = null
        var currentAttributes = mutableMapOf<String, String>()
        var currentChannelId: String? = null

        // Extract EPG URL from the header line
        val headerLine = lines.firstOrNull { it.trim().startsWith("#EXTM3U") }
        if (headerLine != null) {
            val urlTvgMatch = "url-tvg=\"([^\"]+)\"".toRegex().find(headerLine)
            if (urlTvgMatch != null && urlTvgMatch.groupValues.size > 1) {
                epgUrl = urlTvgMatch.groupValues[1]
            }
        }

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            if (trimmedLine.startsWith("#EXTINF:")) {
                currentExtInf = trimmedLine
                currentAttributes = parseExtInfAttributes(trimmedLine)
            } else if (trimmedLine.startsWith("#EXTGRP:")) {
                val groupTitle = trimmedLine.substring("#EXTGRP:".length).trim()
                currentAttributes["group-title"] = groupTitle
            } else if (trimmedLine.startsWith("#PLAYLIST:")) {
                playlistName = trimmedLine.substring("#PLAYLIST:".length).trim()
            } else if (!trimmedLine.startsWith("#") && currentExtInf != null) {
                // This is a URL line
                val url = trimmedLine
                val name = currentAttributes["tvg-name"] ?: extractNameFromExtInf(currentExtInf) ?: "Unknown Channel"
                val logoUrl = currentAttributes["tvg-logo"]
                val groupTitle = currentAttributes["group-title"]
                val epgId = currentAttributes["tvg-id"]
                val id = epgId ?: name.replace(" ", "_").lowercase()

                // Store the current channel ID for program association
                currentChannelId = id

                val channel = IPTVChannel(
                    id = id,
                    name = name,
                    url = url,
                    logoUrl = logoUrl,
                    groupTitle = groupTitle,
                    epgId = epgId,
                    attributes = currentAttributes.toMap()
                )
                channels.add(channel)

                if (groupTitle != null) {
                    val groupId = groupTitle.replace(" ", "_").lowercase()
                    groups.add(IPTVGroup(id = groupId, title = groupTitle))
                }

                // Reset for next channel
                currentExtInf = null
                currentAttributes = mutableMapOf()
            }
        }

        return IPTVPlaylist(
            name = playlistName,
            channels = channels,
            groups = groups.toList(),
            epgUrl = epgUrl
        )
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.M3U
    }

    /**
     * Parses attributes from an EXTINF line.
     *
     * @param extInf The EXTINF line
     * @return A map of attribute names to values
     */
    private fun parseExtInfAttributes(extInf: String): MutableMap<String, String> {
        val attributes = mutableMapOf<String, String>()

        // Extract duration
        val durationRegex = "#EXTINF:(-?\\d+)".toRegex()
        val durationMatch = durationRegex.find(extInf)
        if (durationMatch != null) {
            attributes["duration"] = durationMatch.groupValues[1]
        }

        // Extract other attributes
        val attrRegex = "(\\w+(?:-\\w+)?)\\s*=\\s*\"([^\"]*)\"".toRegex()
        val matches = attrRegex.findAll(extInf)
        for (match in matches) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            attributes[key] = value
        }

        return attributes
    }

    /**
     * Extracts the channel name from an EXTINF line.
     *
     * @param extInf The EXTINF line
     * @return The channel name, or null if not found
     */
    private fun extractNameFromExtInf(extInf: String): String? {
        // The name is typically after the last comma
        val lastCommaIndex = extInf.lastIndexOf(',')
        if (lastCommaIndex != -1 && lastCommaIndex < extInf.length - 1) {
            return extInf.substring(lastCommaIndex + 1).trim()
        }
        return null
    }

}
