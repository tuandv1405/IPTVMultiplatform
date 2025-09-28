package tss.t.tsiptv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.EPGParserFactory
import tss.t.tsiptv.core.parser.IPTVParserFactory
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.core.repository.IHistoryRepository
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.usecase.playlist.GetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.playlist.SetCurrentPlaylistUseCase
import tss.t.tsiptv.utils.isToday
import kotlin.time.ExperimentalTime

class HomeViewModel(
    private val iptvDatabase: IPTVDatabase,
    private val networkClient: NetworkClient,
    private val historyRepository: IHistoryRepository,
    private val historyTracker: ChannelHistoryTracker,
    private val keyValueStorage: KeyValueStorage,
    private val getCurrentPlaylistUC: GetCurrentPlaylistUseCase,
    private val setCurrentPlaylistUC: SetCurrentPlaylistUseCase,
) : ViewModel() {

    private var _currentListChannel: List<Channel> = emptyList()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _homeEvent by lazy {
        MutableSharedFlow<HomeEvent>()
    }
    val homeUIEvent: Flow<HomeEvent> = _homeEvent

    private val _totalChannelList = MutableStateFlow<List<PlaylistWithChannelCount>>(emptyList())
    val totalChannelList: StateFlow<List<PlaylistWithChannelCount>>
        get() = _totalChannelList

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = getCurrentPlaylistUC() ?: return@launch
                val currentPlaylist = iptvDatabase.getPlaylistById(playlistId)
                if (currentPlaylist != null) {
                    onHandleEvent(HomeEvent.LoadHistory)
                    _uiState.update {
                        it.copy(
                            playListId = playlistId,
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
        loadAddChannelPlaylist()
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
                it.copy(isLoading = true)
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
                parsePlaylistEpg(
                    playListId = newPlaylist.id,
                    playListEpgUrl = newPlaylist.epgUrl
                )
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
                setCurrentPlaylistUC(newPlaylist.id)
                onEmitEvent(HomeEvent.OnParseIPTVSourceSuccess)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e)
                }
            }
        }
    }


    /**
     * Loads all playlists from the database and updates the total channel list state.
     * This method is called during initialization to populate the list of available playlists.
     * The loading is performed on the IO dispatcher to avoid blocking the main thread.
     */
    private fun loadAddChannelPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            iptvDatabase.playlistDao
                .getAllPlaylistsWithCount()
                .collect { rs ->
                    _totalChannelList.update {
                        rs
                    }
                    println("TotalPlayList: ${rs.size} ")
                }
        }
    }

    /**
     * Get all channel for a specific IPTV source
     *
     * @param playlistId The ID of the playlist
     * @return Flow of list of channel
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
                            isLoading = false,
                            listChannels = channels
                        )
                    }
                }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun refreshEpg() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentPlayList = _uiState.value.playListId ?: return@launch
            val currPlaylist = iptvDatabase.getPlaylistById(currentPlayList)
            if (currPlaylist == null) {
                return@launch
            }
            val lastFetchEpgSuccess = keyValueStorage.getLong(
                key = currentPlayList,
                defaultValue = 0L
            )
            if (lastFetchEpgSuccess.isToday()) {
                return@launch
            }
            val validCount = iptvDatabase.countValidPrograms(currentPlayList)
            if (validCount > 0) {
                return@launch
            }
            parsePlaylistEpg(
                playListId = currPlaylist.id,
                playListEpgUrl = currPlaylist.epgUrl
            )
        }
    }

    /**
     * Refresh the channel for a specific IPTV source
     *
     * @param playlistId The ID of the playlist to refresh
     */
    fun refreshIPTVChannel(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currPlaylist = iptvDatabase.getPlaylistById(playlistId)
                if (currPlaylist == null) {
                    return@launch
                }
                val content = networkClient.get(currPlaylist.url)
                val parser = IPTVParserFactory.createParserForContent(content)
                val playlist = parser.parse(content)
                val newPlaylist = currPlaylist.copy(
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
                iptvDatabase.deleteChannelsInPlaylist(playlistId)
                iptvDatabase.insertChannels(channels)
                loadHistoryData(newPlaylist.id)
                parsePlaylistEpg(
                    playListId = newPlaylist.id,
                    playListEpgUrl = newPlaylist.epgUrl
                )
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
            iptvDatabase.deleteProgramsForPlaylist(playListId)
            iptvDatabase.insertPrograms(epg, playListId)
            keyValueStorage.putLong(playListId, Clock.System.now().toEpochMilliseconds())
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

            HomeEvent.OnSettingsPressed -> {}
            HomeEvent.OnAboutPressed -> {}
            HomeEvent.OnSearchPressed -> {}
            HomeEvent.OnClearFilterCategory -> {
                viewModelScope.launch(Dispatchers.IO) {
                    _uiState.update {
                        it.copy(
                            listChannels = searchWithFilter(
                                searchKey = uiState.value.searchText,
                                category = null
                            ),
                            selectedCategory = null
                        )
                    }
                }
            }

            is HomeEvent.OnResumeMediaItem -> {
                onResumeMediaItem(event)
            }

            is HomeEvent.OnPlayNowPlaying -> {
                getRelatedChannels(event.channel)
                loadProgramForChannel(event.channel)
            }

            HomeEvent.OnDismissErrorDialog -> {
                _uiState.update {
                    it.copy()
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

            HomeEvent.RefreshEpgIfNeed -> {
                refreshEpg()
            }

            is HomeEvent.OnRequestChangePlaylist -> {
                val currentPlaylist = event.playlist
                viewModelScope.launch {
                    val category = iptvDatabase.getCategoriesByPlaylist(
                        currentPlaylist.id
                    )
                    _uiState.update {
                        it.copy(
                            playListId = currentPlaylist.id,
                            playListName = currentPlaylist.name,
                            categories = category,
                            selectedCategory = null
                        )
                    }
                    setCurrentPlaylistUC(currentPlaylist.id)
                    getAllChannelForIptvSource(playlistId = currentPlaylist.id)
                    loadHistoryData(playlistId = currentPlaylist.id)
                }
            }

            else -> {}
        }
    }

    private fun onResumeMediaItem(event: HomeEvent.OnResumeMediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMediaItem = _uiState.value.nowPlayingChannel
            val playlistId = _uiState.value.playListId ?: return@launch
            var channel = currentMediaItem?.getChannel()
            if (event.mediaItem.id != currentMediaItem?.channelId) {
                channel = iptvDatabase.getChannelById(event.mediaItem.id) ?: return@launch
            }
            channel ?: return@launch
            historyTracker.onChannelPlay(channel, playlistId)
            getRelatedChannels(channel)
            loadProgramForChannel(channel)
            loadHistoryData(playlistId)
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
            val lastPlayedChannel = historyRepository.getLastWatchedChannelWithDetails(playlistId)

            if (lastPlayedChannel != null) {
                _uiState.update { currentState ->
                    currentState.copy(
                        nowPlayingChannel = lastPlayedChannel,
                    )
                }
            }
            historyRepository.getAllWatchedChannelsWithDetails(playlistId)
                .collect { histories ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            top3MostPlayedChannels = histories.subList(
                                fromIndex = 0,
                                toIndex = histories.size.coerceAtMost(3)
                            ),
                            allPlayedChannels = histories
                        )
                    }
                }
        }
    }

    fun loadProgramForChannel(channel: Channel) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.currentProgram?.channelId != channel.id) {
                _uiState.update {
                    it.copy(
                        currentProgram = null,
                        currentProgramList = null
                    )
                }
            }

            val currentProgram = iptvDatabase.getCurrentProgramForChannel(
                channelId = channel.id,
                currentTime = Clock.System.now().toEpochMilliseconds()
            )
            val programForChannel = iptvDatabase.getProgramsForChannel(channel.id)
            if (currentProgram == null) {
                return@launch
            }
            _uiState.update {
                it.copy(
                    currentProgram = currentProgram,
                    currentProgramList = programForChannel
                )
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
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val listChannels: List<Channel> = emptyList(),
    val relatedChannels: List<Channel> = emptyList(),
    val currentProgram: IPTVProgram? = null,
    val currentProgramList: List<IPTVProgram>? = null,
    val error: Throwable? = null,
    val nowPlayingChannel: ChannelWithHistory? = null,
    val top3MostPlayedChannels: List<ChannelWithHistory> = emptyList(),
    val allPlayedChannels: List<ChannelWithHistory> = emptyList(),
)

sealed interface HomeEvent {
    data object RefreshIPTVSource : HomeEvent
    data object OnBackPressed : HomeEvent
    data object OnAddIPTVSourcePressed : HomeEvent
    data class OnFavouriteIPTVChannelPressed(
        val channel: Channel,
    ) : HomeEvent

    data object OnHomeFeedSettingPressed : HomeEvent
    data object OnHomeFeedNotificationPressed : HomeEvent
    data object OnChangeIPTVSourcePressed : HomeEvent
    data class OnRequestChangePlaylist(val playlist: Playlist) : HomeEvent
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
    data object RefreshEpgIfNeed : HomeEvent
}
