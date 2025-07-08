package tss.t.tsiptv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.database.Channel
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.Playlist
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.IPTVParser
import tss.t.tsiptv.core.parser.IPTVParserFactory

class HomeViewModel(
    private val channelRepository: IPTVParser,
    private val iptvDatabase: IPTVDatabase,
    private val networkClient: NetworkClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _currentPlaylistId = MutableStateFlow<String?>(null)
    val currentPlaylistId: StateFlow<String?> = _currentPlaylistId.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val playlists = iptvDatabase.getAllPlaylists()
                playlists.collect { playlistList ->
                    if (playlistList.isNotEmpty() && _currentPlaylistId.value == null) {
                        _currentPlaylistId.value = playlistList.first().id
                    }
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to load playlists: ${e.message}")
            }
        }
    }

    /**
     * Parse an IPTV source from a URL and save it to the database
     * 
     * @param name The name of the IPTV source
     * @param url The URL of the IPTV source
     */
    fun parseIptvSource(name: String, url: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                // Fetch the content from the URL
                val content = networkClient.get(url)

                // Detect the format and create a parser
                val parser = IPTVParserFactory.createParserForContent(content)

                // Parse the content
                val playlist = parser.parse(content)

                // Create a new playlist entity
                val newPlaylist = Playlist(
                    id = url.hashCode().toString(), // Use URL hash as ID
                    name = name,
                    url = url,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )

                // Save the playlist to the database
                iptvDatabase.insertPlaylist(newPlaylist)

                // Save the channels and categories to the database
                val channels = playlist.channels.map { iptvChannel ->
                    Channel(
                        id = iptvChannel.id,
                        name = iptvChannel.name,
                        url = iptvChannel.url,
                        logoUrl = iptvChannel.logoUrl,
                        categoryId = iptvChannel.groupTitle,
                        playlistId = newPlaylist.id,
                        isFavorite = false,
                        lastWatched = null
                    )
                }
                iptvDatabase.insertChannels(channels)

                // Set the current playlist to the newly added one
                _currentPlaylistId.value = newPlaylist.id

                _uiState.value = HomeUiState.Success
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to parse IPTV source: ${e.message}")
            }
        }
    }

    /**
     * Get all IPTV sources from the database
     * 
     * @return Flow of list of playlists
     */
    fun getAllIptvSource(): Flow<List<Playlist>> {
        return iptvDatabase.getAllPlaylists()
    }

    /**
     * Get all channels for a specific IPTV source
     * 
     * @param playlistId The ID of the playlist
     * @return Flow of list of channels
     */
    fun getAllChannelForIptvSource(playlistId: String): Flow<List<Channel>> {
        return iptvDatabase.getAllChannels().map { channels ->
            channels.filter { it.playlistId == playlistId }
        }
    }

    /**
     * Refresh the channels for a specific IPTV source
     * 
     * @param playlistId The ID of the playlist to refresh
     */
    fun refreshIPTVChannel(playlistId: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val playlist = iptvDatabase.getPlaylistById(playlistId)
                if (playlist != null) {
                    // Fetch the content from the URL
                    val content = networkClient.get(playlist.url)

                    // Detect the format and create a parser
                    val parser = IPTVParserFactory.createParserForContent(content)

                    // Parse the content
                    val parsedPlaylist = parser.parse(content)

                    // Update the playlist's last updated timestamp
                    val updatedPlaylist = playlist.copy(
                        lastUpdated = Clock.System.now().toEpochMilliseconds()
                    )
                    iptvDatabase.insertPlaylist(updatedPlaylist)

                    // Delete existing channels for this playlist
                    iptvDatabase.getAllChannels()
                        .map { channels -> channels.filter { it.playlistId == playlistId } }
                        .collect { channels ->
                            channels.forEach { channel ->
                                iptvDatabase.deleteChannel(channel)
                            }
                        }

                    // Save the new channels to the database
                    val channels = parsedPlaylist.channels.map { iptvChannel ->
                        Channel(
                            id = iptvChannel.id,
                            name = iptvChannel.name,
                            url = iptvChannel.url,
                            logoUrl = iptvChannel.logoUrl,
                            categoryId = iptvChannel.groupTitle,
                            playlistId = playlistId,
                            isFavorite = false,
                            lastWatched = null
                        )
                    }
                    iptvDatabase.insertChannels(channels)

                    _uiState.value = HomeUiState.Success
                } else {
                    _uiState.value = HomeUiState.Error("Playlist not found")
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to refresh channels: ${e.message}")
            }
        }
    }

    /**
     * Mark a channel as favorite or remove it from favorites
     * 
     * @param channelId The ID of the channel
     * @param isFavorite Whether the channel should be marked as favorite
     */
    fun favouriteIPTVChannel(channelId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                val channel = iptvDatabase.getChannelById(channelId)
                if (channel != null) {
                    val updatedChannel = channel.copy(isFavorite = isFavorite)
                    iptvDatabase.insertChannel(updatedChannel)
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to update favorite status: ${e.message}")
            }
        }
    }

    /**
     * Change the current IPTV source
     * 
     * @param playlistId The ID of the playlist to switch to
     */
    fun changeIPTVChannelList(playlistId: String) {
        viewModelScope.launch {
            try {
                val playlist = iptvDatabase.getPlaylistById(playlistId)
                if (playlist != null) {
                    _currentPlaylistId.value = playlistId
                } else {
                    _uiState.value = HomeUiState.Error("Playlist not found")
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to change IPTV source: ${e.message}")
            }
        }
    }
}

/**
 * Represents the UI state for the Home screen
 */
sealed class HomeUiState {
    object Loading : HomeUiState()
    object Success : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
