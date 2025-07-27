package tss.t.tsiptv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.Program
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.EPGParserFactory
import tss.t.tsiptv.core.parser.IPTVParserFactory
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.core.repository.IHistoryRepository
import tss.t.tsiptv.player.models.MediaItem

class HomeViewModel(
    private val iptvDatabase: IPTVDatabase,
    private val networkClient: NetworkClient,
    private val historyRepository: IHistoryRepository,
) : ViewModel() {

    private var _currentListChannel: List<Channel> = emptyList()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    val relatedChannels: StateFlow<List<Channel>> = _uiState.map {
        it.relatedChannels
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _homeEvent by lazy {
        MutableSharedFlow<HomeEvent>()
    }
    val homeUIEvent: Flow<HomeEvent> = _homeEvent
    val currentTime = Clock.System.now().toEpochMilliseconds()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlists = iptvDatabase.getAllPlaylists()
                playlists.collect { playlistList ->
                    if (playlistList.isNotEmpty()) {
                        val currentPlaylist = playlistList.first()
                        onHandleEvent(HomeEvent.LoadHistory)
                        _uiState.update {
                            it.copy(
                                playListId = currentPlaylist.id,
                                playListName = currentPlaylist.name,
                                categories = iptvDatabase.getCategoriesByPlaylist(
                                    currentPlaylist.id
                                ),
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
        viewModelScope.launch(Dispatchers.IO) {
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
                    epgUrl = playlist.epgUrl,
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
                        categories = iptvDatabase.getCategoriesByPlaylist(newPlaylist.id),
                        listChannels = channels
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
                    val runningTime = Clock.System.now().toEpochMilliseconds()
                    delay(
                        (700 - runningTime)
                            .coerceAtLeast(0)
                    )
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
                        lastUpdated = Clock.System.now().toEpochMilliseconds(),
                        epgUrl = parsedPlaylist.epgUrl
                    )
                    if (parsedPlaylist.channels.isNotEmpty()) {
                        iptvDatabase.deletePlaylistById(playlistId)
                    }
                    parsePlaylistEpg(
                        playListId = playlistId,
                        playListEpgUrl = parsedPlaylist.epgUrl
                    )
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

    fun parsePlaylistEpg(
        playListId: String,
        playListEpgUrl: String?,
    ) {
        val epgUrl = playListEpgUrl ?: return
        if (epgUrl.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val content = networkClient.getManualGzipIfNeed(
                epgUrl,
                mapOf("Content-Encoding" to "gzip")
            )
            val epgParser = EPGParserFactory.createParserForContent(content)
            val epg = epgParser.parse(content)
            println(epg.size)
            iptvDatabase.insertPrograms(epg.map {
                it.toProgram(playListId)
            })
        }
    }

    private fun IPTVProgram.toProgram(playListId: String): Program {
        return Program(
            id = this.id,
            title = this.title,
            description = this.description,
            category = this.category,
            startTime = this.startTime,
            endTime = this.endTime,
            playlistId = playListId,
            channelId = this.channelId
        )
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
                    loadHistoryData(playlistId)
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

    private fun onHandleEvent(event: HomeEvent) {
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
                viewModelScope.launch(Dispatchers.IO) {
                    _uiState.update {
                        it.copy(
                            selectedCategory = null,
                            listChannels = searchWithFilter(
                                searchKey = uiState.value.searchText,
                                category = null
                            )
                        )
                    }
                }
            }

            HomeEvent.OnDismissErrorDialog -> {
                _uiState.update {
                    it.copy(error = null)
                }
            }

            HomeEvent.LoadHistory -> {
                _uiState.value.playListId?.let {
                    loadHistoryData(it)
                }
            }

            is HomeEvent.OnCategorySelected -> {
                viewModelScope.launch(Dispatchers.IO) {
                    _uiState.update {
                        it.copy(
                            selectedCategory = event.category,
                            listChannels = searchWithFilter(
                                searchKey = uiState.value.searchText,
                                category = event.category
                            )
                        )
                    }
                }
            }

            is HomeEvent.OnSearchKeyChange -> {
                val searchKey = event.key
                viewModelScope.launch(Dispatchers.IO) {
                    _uiState.update {
                        val category = it.selectedCategory
                        it.copy(
                            searchText = searchKey,
                            listChannels = searchWithFilter(searchKey, category)
                        )
                    }
                }
            }

            else -> {}
        }
    }

    private fun searchWithFilter(
        searchKey: String,
        category: Category?,
    ): List<Channel> = _currentListChannel
        .filter {
            if (searchKey.trim().isEmpty()) {
                return@filter true
            }
            it.name.lowercase()
                .contains(searchKey.lowercase()) ||
                    true == it.categoryId?.lowercase()
                ?.contains(searchKey.lowercase())
        }
        .filter { channel ->
            if (category == null) {
                return@filter true
            }
            channel.categoryId == category.id ||
                    channel.categoryId == category.name
        }

    fun getRelatedChannels(channel: Channel) {
        viewModelScope.launch(Dispatchers.IO) {
            val categoryId = channel.categoryId ?: return@launch
            iptvDatabase.getChannelsByCategory(categoryId)
                .catch {
                    emit(_uiState.value.listChannels)
                }
                .map {
                    it.ifEmpty {
                        _uiState.value.listChannels
                    }
                }
                .collect { channels ->
                    _uiState.update {
                        it.copy(
                            relatedChannels = channels
                        )
                    }
                }
        }
    }

    /**
     * Loads history data for the specified playlist and updates UI state.
     */
    private fun loadHistoryData(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Load last played channel (now playing)
            val lastPlayedChannel = historyRepository.getLastWatchedChannelWithDetails(playlistId)

            if (lastPlayedChannel != null) {
                _uiState.update { currentState ->
                    currentState.copy(
                        nowPlayingChannel = lastPlayedChannel,
                    )
                }
            }
            // Load top 3 most played channels
            historyRepository.getLastTop3WatchedChannelsWithDetails(playlistId)
                .collect { top3List ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            nowPlayingChannel = lastPlayedChannel,
                            mostPlayedChannel = top3List.firstOrNull(),
                            top3MostPlayedChannels = top3List
                        )
                    }
                }
        }
    }
}

/**
 * Represents the UI state for the Home screen
 */
data class HomeUiState(
    val searchText: String = "",
    val isLoading: Boolean = true,
    val playListId: String? = null,
    val playListName: String? = null,
    val nowWatching: Channel? = null,
    val nowWatchingCategory: Category? = null,
    val lastWatchedList: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val listChannels: List<Channel> = emptyList(),
    val relatedChannels: List<Channel> = emptyList(),
    val error: Throwable? = null,
    // History-related fields
    val nowPlayingChannel: ChannelWithHistory? = null, // Last played channel from history
    val top3MostPlayedChannels: List<ChannelWithHistory> = emptyList(), // Top 3 most played channels
    val mostPlayedChannel: ChannelWithHistory? = null, // Most played channel
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
    data class OnSearchKeyChange(val key: String) : HomeEvent

    data object OnDismissErrorDialog : HomeEvent

    data object OnClearFilterCategory : HomeEvent
    data class OnCategorySelected(val category: Category) : HomeEvent

    data class OnParseIPTVSource(
        val name: String,
        val url: String,
    ) : HomeEvent

    data class OnOpenVideoPlayer(val channel: Channel) : HomeEvent
    data class OnResumeMediaItem(val mediaItem: MediaItem) : HomeEvent

    data object OnCancelParseIPTVSource : HomeEvent
    data object OnParseIPTVSourceSuccess : HomeEvent
    data class OnPlayNowPlaying(val channel: Channel) : HomeEvent

    data class OnPauseNowPlaying(val channel: Channel) : HomeEvent {}

    data object LoadHistory : HomeEvent {}
}
