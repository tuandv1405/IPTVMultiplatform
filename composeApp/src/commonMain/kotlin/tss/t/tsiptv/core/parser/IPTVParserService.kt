package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.network.NetworkClient

/**
 * Service for parsing IPTV playlists and program schedules.
 * This class combines the M3UParser and EPGParser to provide a complete IPTV parsing solution.
 */
class IPTVParserService(private val networkClient: NetworkClient) {
    /**
     * Parses an IPTV playlist from a string and fetches program schedules if available.
     *
     * @param content The playlist content as a string
     * @return The parsed playlist with program schedules
     * @throws IPTVParserException if parsing fails
     */
    suspend fun parsePlaylist(content: String): IPTVPlaylist {
        // Parse the M3U file
        val format = IPTVParserFactory.detectFormat(content)
        val parser = IPTVParserFactory.createParser(format)
        val playlist = parser.parse(content)

        // If the playlist has an EPG URL, fetch and parse the EPG data
        val epgUrl = playlist.epgUrl
        if (epgUrl != null) {
            try {
                val epgContent = networkClient.get(epgUrl)
                return parsePlaylistWithEPG(playlist, epgContent)
            } catch (e: Exception) {
                // Log the error but continue without program data
                println("Failed to fetch or parse EPG data: ${e.message}")
            }
        }

        return playlist
    }

    /**
     * Parses an IPTV playlist from a URL and fetches program schedules if available.
     *
     * @param url The URL of the playlist
     * @return The parsed playlist with program schedules
     * @throws IPTVParserException if parsing fails
     */
    suspend fun parsePlaylistFromUrl(url: String): IPTVPlaylist {
        val content = networkClient.get(url)
        return parsePlaylist(content)
    }

    /**
     * Parses an IPTV playlist with EPG data.
     * This method is useful for testing purposes.
     *
     * @param playlist The playlist to add program schedules to
     * @param epgContent The EPG content as a string
     * @return The parsed playlist with program schedules
     * @throws EPGParserException if parsing fails
     */
    fun parsePlaylistWithEPG(playlist: IPTVPlaylist, epgContent: String): IPTVPlaylist {
        val epgParser = EPGParserFactory.createParserForContent(epgContent)
        val programs = epgParser.parse(epgContent)

        // Return a new playlist with the programs added
        return playlist.copy(programs = programs)
    }
}
