package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVGroup
import tss.t.tsiptv.core.parser.model.IPTVPlaylist
import tss.t.tsiptv.core.parser.model.exception.IPTVParserException

/**
 * Implementation of IPTVParser for XML format.
 * This is a simple implementation that doesn't use a proper XML parser.
 * In a real implementation, we would use a proper XML parser library.
 */
class XMLParser : IPTVParser {
    override fun parse(content: String): IPTVPlaylist {
        if (!content.trimStart().startsWith("<?xml") && !content.trimStart().startsWith("<tv")) {
            throw IPTVParserException("Invalid XML format: missing XML header or <tv> root element")
        }

        val channels = mutableListOf<IPTVChannel>()
        val groups = mutableSetOf<IPTVGroup>()
        var playlistName = "IPTV Playlist"

        // Simple parsing of XML content
        val lines = content.lines()
        var currentChannel: IPTVChannel? = null
        var currentId = ""
        var currentName = ""
        var currentUrl = ""
        var currentLogoUrl: String? = null
        var currentGroupTitle: String? = null
        var currentGroupId: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()

            // Extract playlist name
            if (trimmedLine.contains("<title>") && trimmedLine.contains("</title>")) {
                val startIndex = trimmedLine.indexOf("<title>") + "<title>".length
                val endIndex = trimmedLine.indexOf("</title>")
                if (startIndex < endIndex) {
                    playlistName = trimmedLine.substring(startIndex, endIndex).trim()
                }
            }

            // Extract channel info
            if (trimmedLine.startsWith("<channel")) {
                // Start of a new channel
                val idStart = trimmedLine.indexOf("id=\"")
                if (idStart != -1) {
                    val idEnd = trimmedLine.indexOf("\"", idStart + 4)
                    if (idEnd != -1) {
                        currentId = trimmedLine.substring(idStart + 4, idEnd)
                    }
                }
            } else if (trimmedLine.contains("<display-name>") && trimmedLine.contains("</display-name>")) {
                val startIndex = trimmedLine.indexOf("<display-name>") + "<display-name>".length
                val endIndex = trimmedLine.indexOf("</display-name>")
                if (startIndex < endIndex) {
                    currentName = trimmedLine.substring(startIndex, endIndex).trim()
                }
            } else if (trimmedLine.contains("<icon") && trimmedLine.contains("src=\"")) {
                val srcStart = trimmedLine.indexOf("src=\"")
                if (srcStart != -1) {
                    val srcEnd = trimmedLine.indexOf("\"", srcStart + 5)
                    if (srcEnd != -1) {
                        currentLogoUrl = trimmedLine.substring(srcStart + 5, srcEnd)
                    }
                }
            } else if (trimmedLine.contains("<group>") && trimmedLine.contains("</group>")) {
                val startIndex = trimmedLine.indexOf("<group>") + "<group>".length
                val endIndex = trimmedLine.indexOf("</group>")
                if (startIndex < endIndex) {
                    currentGroupTitle = trimmedLine.substring(startIndex, endIndex).trim()
                }
            } else if (trimmedLine.contains("<url>") && trimmedLine.contains("</url>")) {
                val startIndex = trimmedLine.indexOf("<url>") + "<url>".length
                val endIndex = trimmedLine.indexOf("</url>")
                if (startIndex < endIndex) {
                    currentUrl = trimmedLine.substring(startIndex, endIndex).trim()
                }
            } else if (trimmedLine.startsWith("</channel>")) {
                // End of a channel
                if (currentId.isNotEmpty() && currentName.isNotEmpty() && currentUrl.isNotEmpty()) {
                    if (currentGroupTitle != null) {
                        val groupId = currentGroupTitle.replace(" ", "_").lowercase()
                        currentGroupId = groupId
                        groups.add(IPTVGroup(id = groupId, title = currentGroupTitle))
                    }

                    val channel = IPTVChannel(
                        id = currentId,
                        name = currentName,
                        url = currentUrl,
                        logoUrl = currentLogoUrl,
                        groupId = currentGroupId,
                        groupTitle = currentGroupTitle,
                        epgId = currentId,
                        attributes = emptyMap()
                    )
                    channels.add(channel)
                }

                // Reset for next channel
                currentId = ""
                currentName = ""
                currentUrl = ""
                currentLogoUrl = null
                currentGroupTitle = null
                currentGroupId = null
            }
        }

        return IPTVPlaylist(
            name = playlistName,
            channels = channels,
            groups = groups.toList()
        )
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.XML
    }
}
