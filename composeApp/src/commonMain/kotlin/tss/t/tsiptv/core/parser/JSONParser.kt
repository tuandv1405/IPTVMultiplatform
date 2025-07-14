package tss.t.tsiptv.core.parser

/**
 * Implementation of IPTVParser for JSON format.
 * This is a simple implementation that doesn't use a proper JSON parser.
 * In a real implementation, we would use a proper JSON parser library.
 */
class JSONParser : IPTVParser {
    override fun parse(content: String): IPTVPlaylist {
        if (!content.trimStart().startsWith("{") && !content.trimStart().startsWith("[")) {
            throw IPTVParserException("Invalid JSON format: missing opening brace or bracket")
        }

        val channels = mutableListOf<IPTVChannel>()
        val groups = mutableSetOf<IPTVGroup>()
        var playlistName = "IPTV Playlist"

        // Simple parsing of JSON content
        // In a real implementation, we would use a proper JSON parser library

        // Try to extract playlist name
        val namePattern = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val nameMatch = namePattern.find(content)
        if (nameMatch != null && nameMatch.groupValues.size > 1) {
            playlistName = nameMatch.groupValues[1]
        }

        // Try to extract channels from different JSON formats

        // Format 1: Array of channels
        // [{"id": "...", "name": "...", "url": "..."}]
        val channelPattern =
            "\\{[^\\{\\}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\"url\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\\}".toRegex()
        val channelMatches = channelPattern.findAll(content)

        for (match in channelMatches) {
            if (match.groupValues.size >= 4) {
                val id = match.groupValues[1]
                val name = match.groupValues[2]
                val url = match.groupValues[3]

                // Try to extract logo URL
                var logoUrl: String? = null
                val logoPattern = "\"logo(?:Url)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val logoMatch = logoPattern.find(match.value)
                if (logoMatch != null && logoMatch.groupValues.size > 1) {
                    logoUrl = logoMatch.groupValues[1]
                }

                // Try to extract group
                var groupTitle: String? = null
                var groupId: String? = null
                val groupPattern = "\"group(?:Title)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val groupMatch = groupPattern.find(match.value)
                if (groupMatch != null && groupMatch.groupValues.size > 1) {
                    groupTitle = groupMatch.groupValues[1]

                    // Add group
                    groupId = groupTitle.replace(" ", "_").lowercase()
                    groups.add(IPTVGroup(id = groupId, title = groupTitle))
                }

                // Try to extract EPG ID
                var epgId: String? = null
                val epgPattern = "\"epg(?:Id)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val epgMatch = epgPattern.find(match.value)
                if (epgMatch != null && epgMatch.groupValues.size > 1) {
                    epgId = epgMatch.groupValues[1]
                }

                val channel = IPTVChannel(
                    id = id,
                    name = name,
                    url = url,
                    logoUrl = logoUrl,
                    groupTitle = groupTitle,
                    groupId = groupId,
                    epgId = epgId,
                    attributes = emptyMap()
                )
                channels.add(channel)
            }
        }

        // Format 2: Object with channels property
        // {"channels": [{"id": "...", "name": "...", "url": "..."}]}
        if (channels.isEmpty() && content.contains("\"channels\"")) {
            val channelsStartPattern = "\"channels\"\\s*:\\s*\\[".toRegex()
            val channelsStartMatch = channelsStartPattern.find(content)

            if (channelsStartMatch != null) {
                val startIndex = channelsStartMatch.range.last + 1
                var endIndex = content.indexOf("]", startIndex)
                if (endIndex == -1) endIndex = content.length

                val channelsContent = content.substring(startIndex, endIndex)
                val channelMatches = channelPattern.findAll(channelsContent)

                for (match in channelMatches) {
                    if (match.groupValues.size >= 4) {
                        val id = match.groupValues[1]
                        val name = match.groupValues[2]
                        val url = match.groupValues[3]

                        // Try to extract logo URL
                        var logoUrl: String? = null
                        val logoPattern = "\"logo(?:Url)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        val logoMatch = logoPattern.find(match.value)
                        if (logoMatch != null && logoMatch.groupValues.size > 1) {
                            logoUrl = logoMatch.groupValues[1]
                        }

                        // Try to extract group
                        var groupTitle: String? = null
                        val groupPattern = "\"group(?:Title)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        val groupMatch = groupPattern.find(match.value)
                        if (groupMatch != null && groupMatch.groupValues.size > 1) {
                            groupTitle = groupMatch.groupValues[1]

                            // Add group
                            val groupId = groupTitle.replace(" ", "_").lowercase()
                            groups.add(IPTVGroup(id = groupId, title = groupTitle))
                        }

                        // Try to extract EPG ID
                        var epgId: String? = null
                        val epgPattern = "\"epg(?:Id)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        val epgMatch = epgPattern.find(match.value)
                        if (epgMatch != null && epgMatch.groupValues.size > 1) {
                            epgId = epgMatch.groupValues[1]
                        }

                        val channel = IPTVChannel(
                            id = id,
                            name = name,
                            url = url,
                            logoUrl = logoUrl,
                            groupTitle = groupTitle,
                            epgId = epgId,
                            attributes = emptyMap()
                        )
                        channels.add(channel)
                    }
                }
            }
        }

        // Format 3: Object with groups property containing channels
        // {"groups": [{"name": "...", "channels": [{"id": "...", "name": "...", "url": "..."}]}]}
        if (channels.isEmpty() && content.contains("\"groups\"")) {
            val groupsStartPattern = "\"groups\"\\s*:\\s*\\[".toRegex()
            val groupsStartMatch = groupsStartPattern.find(content)

            if (groupsStartMatch != null) {
                val startIndex = groupsStartMatch.range.last + 1
                var endIndex = content.indexOf("]", startIndex)
                if (endIndex == -1) endIndex = content.length

                val groupsContent = content.substring(startIndex, endIndex)
                val groupPattern =
                    "\\{[^\\{\\}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\"channels\"\\s*:\\s*\\[([^\\[\\]]*)\\][^\\{\\}]*\\}".toRegex()
                val groupMatches = groupPattern.findAll(groupsContent)

                for (groupMatch in groupMatches) {
                    if (groupMatch.groupValues.size >= 3) {
                        val groupTitle = groupMatch.groupValues[1]
                        val groupId = groupTitle.replace(" ", "_").lowercase()
                        groups.add(IPTVGroup(id = groupId, title = groupTitle))

                        val channelsContent = groupMatch.groupValues[2]
                        val channelMatches = channelPattern.findAll(channelsContent)

                        for (match in channelMatches) {
                            if (match.groupValues.size >= 4) {
                                val id = match.groupValues[1]
                                val name = match.groupValues[2]
                                val url = match.groupValues[3]

                                // Try to extract logo URL
                                var logoUrl: String? = null
                                val logoPattern = "\"logo(?:Url)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                                val logoMatch = logoPattern.find(match.value)
                                if (logoMatch != null && logoMatch.groupValues.size > 1) {
                                    logoUrl = logoMatch.groupValues[1]
                                }

                                // Try to extract EPG ID
                                var epgId: String? = null
                                val epgPattern = "\"epg(?:Id)?\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                                val epgMatch = epgPattern.find(match.value)
                                if (epgMatch != null && epgMatch.groupValues.size > 1) {
                                    epgId = epgMatch.groupValues[1]
                                }

                                val channel = IPTVChannel(
                                    id = id,
                                    name = name,
                                    url = url,
                                    logoUrl = logoUrl,
                                    groupTitle = groupTitle,
                                    epgId = epgId,
                                    attributes = emptyMap()
                                )
                                channels.add(channel)
                            }
                        }
                    }
                }
            }
        }

        return IPTVPlaylist(
            name = playlistName,
            channels = channels,
            groups = groups.toList()
        )
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.JSON
    }
}
