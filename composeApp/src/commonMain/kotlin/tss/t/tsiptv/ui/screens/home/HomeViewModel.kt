package tss.t.tsiptv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.database.Category
import tss.t.tsiptv.core.database.Channel
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.Playlist
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.IPTVParserFactory

class HomeViewModel(
    private val iptvDatabase: IPTVDatabase,
    private val networkClient: NetworkClient,
) : ViewModel() {

    private var _currentListChannel: List<Channel> = emptyList()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
    private val _homeEvent by lazy {
        MutableSharedFlow<HomeEvent>()
    }
    val homeUIEvent: Flow<HomeEvent> = _homeEvent

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlists = iptvDatabase.getAllPlaylists()
                playlists.collect { playlistList ->
                    if (playlistList.isNotEmpty()) {
                        val currentPlaylist = playlistList.first()
                        _uiState.update {
                            it.copy(
                                playListId = currentPlaylist.id,
                                playListName = currentPlaylist.name,
                                categories = iptvDatabase.getCategoriesByPlaylist(
                                    currentPlaylist.id
                                )
                            )
                        }
                        getAllChannelForIptvSource(playlistId = currentPlaylist.id)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e
                    )
                }
            }
        }
        viewModelScope.launch {
            _homeEvent.collect { event ->
                onHandleEvent(event)
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
            _uiState.update {
                it.copy(isLoading = true, error = null)
            }
            try {
                val content = networkClient.get(url)
                val parser = IPTVParserFactory.createParserForContent(content)
                val playlist = parser.parse(content)
                val newPlaylist = Playlist(
                    id = url.hashCode().toString(), // Use URL hash as ID
                    name = name,
                    url = url,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                iptvDatabase.insertPlaylist(newPlaylist)
                iptvDatabase.insertCategories(playlist.groups.map {
                    Category(
                        id = it.id,
                        name = it.title,
                        playlistId = newPlaylist.id
                    )
                })
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playListId = newPlaylist.id,
                        playListName = newPlaylist.name,
                        categories = iptvDatabase.getCategoriesByPlaylist(newPlaylist.id)
                    )
                }
                onEmitEvent(HomeEvent.OnParseIPTVSourceSuccess)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e)
                }
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
    fun getAllChannelForIptvSource(playlistId: String) {
        viewModelScope.launch {
            iptvDatabase
                .getAllChannelsByPlayListId(playlistId)
                .collect { channels ->
                    _currentListChannel = channels
                    _uiState.update {
                        it.copy(
                            listChannels = channels,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    /**
     * Refresh the channels for a specific IPTV source
     *
     * @param playlistId The ID of the playlist to refresh
     */
    fun refreshIPTVChannel(playlistId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null
                )
            }
            try {
                val playlist = iptvDatabase.getPlaylistById(playlistId)
                if (playlist != null) {
                    val content = networkClient.get(playlist.url)
                    val parser = IPTVParserFactory.createParserForContent(content)
                    val parsedPlaylist = parser.parse(content)
                    val updatedPlaylist = playlist.copy(
                        lastUpdated = Clock.System.now().toEpochMilliseconds()
                    )
                    if (parsedPlaylist.channels.isNotEmpty()) {
                        iptvDatabase.deletePlaylistById(playlistId)
                    }
                    iptvDatabase.deleteChannelsInPlaylist(playlistId)
                    iptvDatabase.insertPlaylist(updatedPlaylist)

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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playListId = playlistId,
                            playListName = playlist.name,
                            categories = iptvDatabase.getCategoriesByPlaylist(playlistId),
                            listChannels = channels
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = Throwable("Playlist not found")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e)
                }
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
                _uiState.update {
                    it.copy(isLoading = false, error = e)
                }
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playListId = playlistId,
                            playListName = playlist.name
                        )
                    }
                    getAllChannelForIptvSource(playlistId = playlistId)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = Throwable("Playlist not found")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e
                    )
                }
            }
        }
    }

    fun onEmitEvent(event: HomeEvent) {
        viewModelScope.launch {
            _homeEvent.emit(event)
        }
    }

    fun onHandleEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.RefreshIPTVSource -> refreshIPTVChannel(_uiState.value.playListId!!)
            HomeEvent.OnBackPressed -> {}
            HomeEvent.OnAddIPTVSourcePressed -> {}
            is HomeEvent.OnFavouriteIPTVChannelPressed -> {
                favouriteIPTVChannel(
                    channelId = event.channel.id,
                    isFavorite = event.channel.isFavorite.not()
                )
            }

            HomeEvent.OnChangeIPTVChannelPressed -> {}
            HomeEvent.OnSettingsPressed -> {}
            HomeEvent.OnAboutPressed -> {}
            HomeEvent.OnSearchPressed -> {}
            HomeEvent.OnClearFilterCategory -> {
                _uiState.update {
                    it.copy(
                        selectedCategory = null,
                        listChannels = _currentListChannel
                    )
                }
            }

            HomeEvent.OnDismissErrorDialog -> {
                _uiState.update {
                    it.copy(error = null)
                }
            }

            is HomeEvent.OnCategorySelected -> {
                _uiState.update {
                    it.copy(
                        selectedCategory = event.category,
                        listChannels = _currentListChannel.filter { channel ->
                            channel.categoryId == event.category.id ||
                                    channel.categoryId == event.category.name
                        }
                    )
                }
            }

            else -> {}
        }
    }
}

/**
 * Represents the UI state for the Home screen
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val playListId: String? = null,
    val playListName: String? = null,
    val nowWatching: Channel? = null,
    val nowWatchingCategory: Category? = null,
    val lastWatchedList: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val listChannels: List<Channel> = emptyList(),
    val error: Throwable? = null,
)

sealed interface HomeEvent {
    data object RefreshIPTVSource : HomeEvent
    data object OnBackPressed : HomeEvent
    data object OnAddIPTVSourcePressed : HomeEvent
    data class OnFavouriteIPTVChannelPressed(
        val channel: Channel,
    ) : HomeEvent

    data object OnChangeIPTVChannelPressed : HomeEvent
    data object OnSettingsPressed : HomeEvent
    data object OnAboutPressed : HomeEvent
    data object OnSearchPressed : HomeEvent

    data object OnDismissErrorDialog : HomeEvent

    data object OnClearFilterCategory : HomeEvent
    data class OnCategorySelected(val category: Category) : HomeEvent

    data class OnParseIPTVSource(
        val name: String,
        val url: String,
    ) : HomeEvent

    data class OnOpenVideoPlayer(val channel: Channel) : HomeEvent {
    }

    data object OnCancelParseIPTVSource : HomeEvent
    data object OnParseIPTVSourceSuccess : HomeEvent
}
