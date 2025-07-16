package tss.t.tsiptv.core.parser

/**
 * Implementation of IPTVParser for XSPF format.
 * XSPF (XML Shareable Playlist Format) is an XML-based playlist format.
 */
class XSPFParser : IPTVParser {
    override fun parse(content: String): IPTVPlaylist {
        if (!content.trimStart().startsWith("<?xml") && !content.trimStart().contains("<playlist")) {
            throw IPTVParserException("Invalid XSPF format: missing XML header or <playlist> element")
        }

        val channels = mutableListOf<IPTVChannel>()
        val groups = mutableSetOf<IPTVGroup>()
        var playlistName = "IPTV Playlist"

        // Simple parsing of XML content
        val lines = content.lines()
        var currentTrack = false
        var currentTitle = ""
        var currentLocation = ""
        var currentImage: String? = null
        var currentId: String? = null
        var currentNodeTitle: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()

            // Extract playlist name
            if (trimmedLine.contains("<title>") && trimmedLine.contains("</title>") && !currentTrack) {
                val startIndex = trimmedLine.indexOf("<title>") + "<title>".length
                val endIndex = trimmedLine.indexOf("</title>")
                if (startIndex < endIndex) {
                    playlistName = trimmedLine.substring(startIndex, endIndex).trim()
                }
            }

            // Track start
            if (trimmedLine.contains("<track>")) {
                currentTrack = true
                currentTitle = ""
                currentLocation = ""
                currentImage = null
                currentId = null
            }
            // Track end
            else if (trimmedLine.contains("</track>")) {
                currentTrack = false
                
                if (currentTitle.isNotEmpty() && currentLocation.isNotEmpty()) {
                    val id = currentId ?: currentTitle.replace(" ", "_").lowercase()
                    
                    val channel = IPTVChannel(
                        id = id,
                        name = currentTitle,
                        url = currentLocation,
                        logoUrl = currentImage,
                        groupTitle = currentNodeTitle,
                        groupId = currentNodeTitle?.replace(" ", "_")?.lowercase(),
                        epgId = id,
                        attributes = emptyMap()
                    )
                    channels.add(channel)
                }
            }
            
            // Inside track
            if (currentTrack) {
                // Extract title
                if (trimmedLine.contains("<title>") && trimmedLine.contains("</title>")) {
                    val startIndex = trimmedLine.indexOf("<title>") + "<title>".length
                    val endIndex = trimmedLine.indexOf("</title>")
                    if (startIndex < endIndex) {
                        currentTitle = trimmedLine.substring(startIndex, endIndex).trim()
                    }
                }
                // Extract location (URL)
                else if (trimmedLine.contains("<location>") && trimmedLine.contains("</location>")) {
                    val startIndex = trimmedLine.indexOf("<location>") + "<location>".length
                    val endIndex = trimmedLine.indexOf("</location>")
                    if (startIndex < endIndex) {
                        currentLocation = trimmedLine.substring(startIndex, endIndex).trim()
                    }
                }
                // Extract image
                else if (trimmedLine.contains("<image>") && trimmedLine.contains("</image>")) {
                    val startIndex = trimmedLine.indexOf("<image>") + "<image>".length
                    val endIndex = trimmedLine.indexOf("</image>")
                    if (startIndex < endIndex) {
                        currentImage = trimmedLine.substring(startIndex, endIndex).trim()
                    }
                }
                // Extract ID
                else if (trimmedLine.contains("<vlc:id>") && trimmedLine.contains("</vlc:id>")) {
                    val startIndex = trimmedLine.indexOf("<vlc:id>") + "<vlc:id>".length
                    val endIndex = trimmedLine.indexOf("</vlc:id>")
                    if (startIndex < endIndex) {
                        currentId = trimmedLine.substring(startIndex, endIndex).trim()
                    }
                }
            }
            
            // Extract group information
            if (trimmedLine.contains("<vlc:node title=\"")) {
                val titleStart = trimmedLine.indexOf("title=\"") + "title=\"".length
                val titleEnd = trimmedLine.indexOf("\"", titleStart)
                if (titleStart < titleEnd) {
                    currentNodeTitle = trimmedLine.substring(titleStart, titleEnd).trim()
                    val groupId = currentNodeTitle.replace(" ", "_").lowercase()
                    groups.add(IPTVGroup(id = groupId, title = currentNodeTitle))
                }
            }
        }

        // Process channels to assign groups based on vlc:item references
        // This would require a more complex parsing approach to match vlc:item tid values with channel IDs
        // For simplicity, we're just using the node title as the group for all channels

        return IPTVPlaylist(
            name = playlistName,
            channels = channels,
            groups = groups.toList()
        )
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.XSPF
    }
}