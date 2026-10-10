@file:OptIn(ExperimentalTime::class)

package tss.t.tsiptv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.core.database.entity.shiftedBy
import tss.t.tsiptv.core.firebase.analystics.AnalyticsConstants
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.core.parser.model.SkipReason
import tss.t.tsiptv.core.repository.IHistoryRepository
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.platform.PickedPlaylistFile
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.usecase.playlist.GetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.playlist.ImportError
import tss.t.tsiptv.usecase.playlist.ImportOutcome
import tss.t.tsiptv.usecase.playlist.ImportResult
import tss.t.tsiptv.usecase.playlist.PlaylistImportException
import tss.t.tsiptv.usecase.playlist.PlaylistImporter
import tss.t.tsiptv.usecase.playlist.SetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.playlist.TsiptvRefreshFailedException
import tss.t.tsiptv.core.tsiptv.TsiptvNothingLoadedException
import tss.t.tsiptv.core.tsiptv.TsiptvPendingImport
import tss.t.tsiptv.core.tsiptv.TsiptvRefreshResult
import tss.t.tsiptv.core.tsiptv.TsiptvSourcePreview
import tss.t.tsiptv.core.tsiptv.TsiptvSourceRejectedException
import tss.t.tsiptv.core.tsiptv.TsiptvSourceService
import tss.t.tsiptv.core.tsiptv.TsiptvStoreResult
import tss.t.tsiptv.core.parser.tsiptv.TsiptvValidationReport
import tss.t.tsiptv.utils.isToday
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class HomeViewModel(
    private val iptvDatabase: IPTVDatabase,
    private val historyRepository: IHistoryRepository,
    private val historyTracker: ChannelHistoryTracker,
    private val keyValueStorage: KeyValueStorage,
    private val getCurrentPlaylistUC: GetCurrentPlaylistUseCase,
    private val setCurrentPlaylistUC: SetCurrentPlaylistUseCase,
    private val playlistImporter: PlaylistImporter,
    private val sourceService: TsiptvSourceService,
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

    private var importJob: Job? = null
    private var channelsJob: Job? = null

    /** The picked file waiting for "Replace?"; up to 20 MiB, so kept out of the UI state. */
    private var pendingFile: Pair<String, PickedPlaylistFile.Picked>? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = getCurrentPlaylistUC() ?: return@launch
                // Opening the playlist is when the daily refresh runs (and, after the v4
                // migration, the one-off re-parse that brings in headers and DRM).
                val currentPlaylist = playlistImporter.refreshIfStale(playlistId)
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
    fun parseIptvSource(name: String, url: String, fromLan: Boolean = false) {
        importFromLan = fromLan
        importName = name
        runImport {
            when (val outcome = playlistImporter.importFromUrl(name, url)) {
                is ImportOutcome.SingleStream -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingSingleStream = PendingSingleStream(outcome.name, outcome.url)
                    )
                }

                is ImportOutcome.Imported -> onImported(outcome.result)
                is ImportOutcome.SourcePreview -> onSourcePreview(outcome.preview)
            }
        }
    }

    /**
     * Import a playlist or `.strm` file the user picked. A file with the same display name as an
     * earlier import asks before replacing it.
     */
    fun importFile(name: String, file: PickedPlaylistFile, confirmedReplace: Boolean = false, fromLan: Boolean = false) {
        importFromLan = fromLan
        importName = name
        when (file) {
            PickedPlaylistFile.Cancelled -> Unit
            is PickedPlaylistFile.TooLarge -> _uiState.update {
                it.copy(error = PlaylistImportException(ImportError.FILE_TOO_LARGE))
            }

            PickedPlaylistFile.ReadFailed -> _uiState.update {
                it.copy(error = PlaylistImportException(ImportError.FILE_READ))
            }

            is PickedPlaylistFile.Picked -> runImport {
                val id = PlaylistImporter.filePlaylistId(file.displayName)
                if (!confirmedReplace && iptvDatabase.getPlaylistById(id) != null) {
                    pendingFile = name to file
                    _uiState.update {
                        it.copy(isLoading = false, pendingFileReplace = file.displayName)
                    }
                    return@runImport
                }
                when (val outcome = playlistImporter.importFileOutcome(name, file.displayName, file.bytes)) {
                    is ImportOutcome.Imported -> onImported(outcome.result)
                    is ImportOutcome.SourcePreview -> onSourcePreview(outcome.preview)
                    is ImportOutcome.SingleStream -> Unit
                }
            }
        }
    }

    /** The running (or last) import came from a phone (TV: imported in the background from Home). */
    private var importFromLan = false
    private var importName: String? = null

    private fun runImport(block: suspend () -> Unit) {
        importJob?.cancel()
        val fromLan = importFromLan
        _uiState.update { it.copy(isLoading = true, importFromLan = fromLan, importName = importName) }
        importJob = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isLoading = false) }
                throw e
            } catch (e: TsiptvSourceRejectedException) {
                _uiState.update { it.copy(isLoading = false, sourceImport = SourceImportState.Rejected(e.report)) }
            } catch (e: TsiptvNothingLoadedException) {
                _uiState.update { it.copy(isLoading = false, sourceImport = SourceImportState.Rejected(null)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e, sourceImport = null) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // F3: TS IPTV Source import (preview -> includes -> 18+ -> store)
    // ---------------------------------------------------------------------------------------------

    private var pendingSource: TsiptvPendingImport? = null

    /** The playlist that was active before the current one (Remove source goes back to it). */
    private var previousPlaylistId: String? = null

    private fun rememberPrevious(newPlaylistId: String) {
        _uiState.value.playListId?.takeIf { it != newPlaylistId }?.let {
            previousPlaylistId = it
            // Kept across restarts (QC F3 r2): Remove source still goes back to it.
            viewModelScope.launch { keyValueStorage.putString(KEY_PREVIOUS_PLAYLIST, it) }
        }
    }

    private fun onSourcePreview(preview: TsiptvSourcePreview) {
        _uiState.update { it.copy(isLoading = false, sourceImport = SourceImportState.Preview(preview)) }
    }

    /** "Import" in the preview: fetch the includes, then ask 18+ for adult includes, then store. */
    private fun importSource(preview: TsiptvSourcePreview, adultConfirmed: Boolean) {
        runImport {
            _uiState.update { it.copy(sourceImport = SourceImportState.Fetching(0, preview.includeCount)) }
            val pending = sourceService.resolve(preview) { done, total ->
                _uiState.update { s ->
                    if (s.sourceImport is SourceImportState.Fetching) s.copy(sourceImport = SourceImportState.Fetching(done, total)) else s
                }
            }
            if (pending.nothingLoaded) throw TsiptvNothingLoadedException()
            val confirmedBefore = adultConfirmed || preview.existing?.adultConfirmed == true
            if (pending.adultIncludePaths.isNotEmpty() && !confirmedBefore) {
                pendingSource = pending
                _uiState.update { it.copy(isLoading = false, sourceImport = SourceImportState.AdultInclude) }
                return@runImport
            }
            storeSource(pending, adultConfirmed)
        }
    }

    private suspend fun storeSource(pending: TsiptvPendingImport, adultConfirmed: Boolean) {
        pendingSource = null
        val result = sourceService.store(pending, adultConfirmed)
        onSourceStored(result, fromRefresh = false)
    }

    private suspend fun onSourceStored(result: TsiptvStoreResult, fromRefresh: Boolean) {
        val playlist = result.playlist
        rememberPrevious(playlist.id)
        // Format, size and the sanitized link (AnalyticsLinks); never names or the raw link.
        if (!fromRefresh) {
            Firebase.analytics.logEvent(
                AnalyticsConstants.EVENT_ADD_IPTV,
                mapOf(
                    AnalyticsConstants.PARAMS_IPTV_FORMAT to tss.t.tsiptv.core.parser.model.IPTVFormat.TSIPTV_SOURCE.name,
                    AnalyticsConstants.PARAMS_IPTV_CHANNEL_COUNT to result.channelCount,
                ) + AnalyticsConstants.addSourceParams(
                    rawLink = playlist.url.takeIf { playlist.sourceType == PlaylistSourceType.URL },
                    hasEpg = result.hasGuide,
                    includeCount = result.includeCount,
                )
            )
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                sourceImport = null,
                playListId = playlist.id,
                playListName = playlist.name,
                categories = iptvDatabase.getCategoriesByPlaylist(playlist.id),
                selectedCategory = null,
                importSummary = ImportSummary(
                    channelCount = result.channelCount,
                    skipped = emptyMap(),
                    fromRefresh = fromRefresh,
                    fromLan = importFromLan && !fromRefresh,
                    source = SourceImportSummary(result.channelCount, result.movieCount, result.seriesCount, result.skippedCount, result.report),
                ),
            )
        }
        getAllChannelForIptvSource(playlist.id)
        setCurrentPlaylistUC(playlist.id)
        loadHistoryData(playlist.id)
        if (!fromRefresh) onEmitEvent(HomeEvent.OnParseIPTVSourceSuccess)
    }

    /** "Confirm" 18+ in About: merges the withheld content. */
    fun confirmSourceAdult(playlistId: String) {
        viewModelScope.launch {
            val result = try {
                sourceService.confirmAdult(playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (result is TsiptvRefreshResult.Stored) onSourceStored(result.result, fromRefresh = true)
        }
    }

    /** "Remove source" in About. Another playlist (or none) becomes active. */
    fun removeSource(playlistId: String) {
        viewModelScope.launch {
            try {
                sourceService.remove(playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e) }
                return@launch
            }
            if (_uiState.value.playListId != playlistId) return@launch
            // Back to the playlist that was active before this one (QC F3 #22), else any other.
            val previous = previousPlaylistId ?: keyValueStorage.getString(KEY_PREVIOUS_PLAYLIST).takeIf { it.isNotEmpty() }
            val next = previous?.takeIf { it != playlistId }?.let { iptvDatabase.getPlaylistById(it) }
                ?: iptvDatabase.getAllPlaylists().first().firstOrNull { it.id != playlistId }
            if (next != null) {
                onHandleEvent(HomeEvent.OnRequestChangePlaylist(next))
            } else {
                channelsJob?.cancel()
                _uiState.update {
                    it.copy(playListId = null, playListName = null, categories = emptyList(), listChannels = emptyList(), zapChannels = null)
                }
            }
        }
    }

    private suspend fun onImported(result: ImportResult) {
        val playlist = result.playlist
        rememberPrevious(playlist.id)
        // Format, size and the sanitized link: raw links carry tokens and |Authorization=
        // suffixes (AnalyticsLinks strips them), and the name the user typed is never sent.
        Firebase.analytics.logEvent(
            AnalyticsConstants.EVENT_ADD_IPTV,
            mapOf(
                AnalyticsConstants.PARAMS_IPTV_FORMAT to result.format.name,
                AnalyticsConstants.PARAMS_IPTV_CHANNEL_COUNT to result.channelCount,
            ) + AnalyticsConstants.addSourceParams(
                rawLink = playlist.url.takeIf { playlist.sourceType == PlaylistSourceType.URL },
                hasEpg = playlist.epgUrls.isNotEmpty(),
            )
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                playListId = playlist.id,
                playListName = playlist.name,
                categories = iptvDatabase.getCategoriesByPlaylist(playlist.id),
                selectedCategory = null,
                importSummary = ImportSummary(result.channelCount, result.skipped, fromRefresh = false, fromLan = importFromLan),
            )
        }
        getAllChannelForIptvSource(playlist.id)
        setCurrentPlaylistUC(playlist.id)
        parsePlaylistEpg(playListId = playlist.id, epgUrls = playlist.epgUrls)
        onEmitEvent(HomeEvent.OnParseIPTVSourceSuccess)
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
        // One collector at a time; each playlist switch used to add another.
        channelsJob?.cancel()
        channelsJob = viewModelScope.launch {
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
                            listChannels = searchWithFilter(it.searchText, it.selectedCategory)
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
                epgUrls = currPlaylist.epgUrls
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
                val result = playlistImporter.refresh(playlistId)
                val newPlaylist = result.playlist
                val top3Watched = iptvDatabase.channelHistoryDao
                    .getLastTop3WatchedChannelsWithDetailsSync(playlistId)
                parsePlaylistEpg(
                    playListId = newPlaylist.id,
                    epgUrls = newPlaylist.epgUrls
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playListId = newPlaylist.id,
                        playListName = newPlaylist.name,
                        categories = iptvDatabase.getCategoriesByPlaylist(newPlaylist.id),
                        top3MostPlayedChannels = top3Watched,
                        importSummary = ImportSummary(result.channelCount, result.skipped, fromRefresh = true),
                    )
                }
                onEmitEvent(HomeEvent.OnParseIPTVSourceSuccess)
            } catch (e: TsiptvRefreshFailedException) {
                // The root failed, but the includes may have been stored (QC F3 r2 #4): reload what
                // Home shows from the database, then tell the user.
                val playlist = iptvDatabase.getPlaylistById(playlistId)
                val categories = iptvDatabase.getCategoriesByPlaylist(playlistId)
                val top3Watched = iptvDatabase.channelHistoryDao.getLastTop3WatchedChannelsWithDetailsSync(playlistId)
                _uiState.update {
                    if (it.playListId != playlistId) return@update it.copy(isLoading = false, notice = HomeNotice.SOURCE_REFRESH_FAILED)
                    it.copy(
                        isLoading = false,
                        notice = HomeNotice.SOURCE_REFRESH_FAILED,
                        playListName = playlist?.name ?: it.playListName,
                        categories = categories,
                        top3MostPlayedChannels = top3Watched,
                    )
                }
                if (_uiState.value.playListId == playlistId) getAllChannelForIptvSource(playlistId)
            } catch (e: PlaylistImportException) {
                if (e.error == ImportError.FILE_REFRESH) {
                    _uiState.update { it.copy(isLoading = false, notice = HomeNotice.FILE_REFRESH_HINT) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = e) }
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
        epgUrls: List<String>,
    ) {
        if (epgUrls.none { it.isNotBlank() }) return
        viewModelScope.launch(Dispatchers.IO) {
            playlistImporter.fetchEpg(playListId, epgUrls) ?: return@launch
            keyValueStorage.putLong(playListId, Clock.System.now().toEpochMilliseconds())
            val channel = uiState.value.nowPlayingChannel
            channel?.getChannel()?.let {
                loadProgramForChannel(it)
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

    fun onEmitEvent(event: HomeEvent) {
        viewModelScope.launch {
            _homeEvent.emit(event)
        }
    }

    private fun onHandleEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.RefreshIPTVSource -> _uiState.value.playListId?.let(::refreshIPTVChannel)
            HomeEvent.OnBackPressed -> {}
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
                    it.copy(
                        error = null
                    )
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
                rememberPrevious(currentPlaylist.id)
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
                    // Opening a playlist runs its daily refresh; the channel flow picks up
                    // the new rows when it lands.
                    if (playlistImporter.refreshIfStale(currentPlaylist.id) != null) {
                        _uiState.update {
                            if (it.playListId != currentPlaylist.id) return@update it
                            it.copy(categories = iptvDatabase.getCategoriesByPlaylist(currentPlaylist.id))
                        }
                    }
                }
            }

            HomeEvent.OnCancelParseIPTVSource -> {
                importJob?.cancel()
                _uiState.update { it.copy(isLoading = false) }
            }

            is HomeEvent.OnImportFile -> importFile(event.name, event.file)

            HomeEvent.OnConfirmReplaceFile -> {
                val (name, file) = pendingFile ?: return
                pendingFile = null
                _uiState.update { it.copy(pendingFileReplace = null) }
                importFile(name, file, confirmedReplace = true)
            }

            HomeEvent.OnCancelReplaceFile -> {
                pendingFile = null
                _uiState.update { it.copy(pendingFileReplace = null) }
            }

            HomeEvent.OnConfirmSingleStream -> {
                val pending = _uiState.value.pendingSingleStream ?: return
                _uiState.update { it.copy(pendingSingleStream = null) }
                runImport { onImported(playlistImporter.importSingleStream(pending.name, pending.url)) }
            }

            HomeEvent.OnCancelSingleStream -> _uiState.update { it.copy(pendingSingleStream = null) }
            HomeEvent.OnDismissNotice -> _uiState.update { it.copy(notice = null) }
            HomeEvent.OnDismissImportSummary -> _uiState.update { it.copy(importSummary = null) }
            is HomeEvent.OnSourceImport -> {
                val preview = (_uiState.value.sourceImport as? SourceImportState.Preview)?.preview ?: return
                importSource(preview, event.adultConfirmed)
            }

            HomeEvent.OnSourceAdultIncludeConfirmed -> {
                val pending = pendingSource ?: return
                runImport { storeSource(pending, adultConfirmed = true) }
            }

            HomeEvent.OnSourceImportDismiss -> {
                if (_uiState.value.sourceImport is SourceImportState.Fetching) importJob?.cancel()
                pendingSource = null
                _uiState.update { it.copy(isLoading = false, sourceImport = null) }
            }

            // A channel opened anywhere else zaps through the channel list again.
            is HomeEvent.OnOpenVideoPlayer -> _uiState.update { it.copy(zapChannels = null) }
            is HomeEvent.OnPlaySourceChannel -> _uiState.update { it.copy(zapChannels = event.zapList.takeIf { list -> list.size > 1 }) }
            is HomeEvent.OnConfirmSourceAdult -> confirmSourceAdult(event.playlistId)
            is HomeEvent.OnRemoveSource -> removeSource(event.playlistId)

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

    /** A channel is listed under every group it belongs to, not only its first. */
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
                    channel.categoryId == category.name ||
                    channel.groups.any { it.equals(category.name, ignoreCase = true) }
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
            if (_uiState.value.currentProgram?.channelId != channel.guideId) {
                _uiState.update {
                    it.copy(
                        currentProgram = null,
                        currentProgramList = null
                    )
                }
            }

            // History rows carry only part of a channel; the shift lives on the full row.
            val shiftHours = (iptvDatabase.getChannelById(channel.id) ?: channel).epgShiftHours ?: 0.0
            val shiftMs = (shiftHours * 3_600_000).toLong()
            val currentProgram = iptvDatabase.getCurrentProgramForChannel(
                channelId = channel.guideId,
                currentTime = Clock.System.now().toEpochMilliseconds() - shiftMs
            )?.shiftedBy(shiftMs)
            val programForChannel = iptvDatabase.getProgramsForChannel(channel.guideId)
                .map { it.shiftedBy(shiftMs) }
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

private const val KEY_PREVIOUS_PLAYLIST = "home_previous_playlist_id"

/** What the last import or refresh stored, for the result dialog. */
data class ImportSummary(
    val channelCount: Int,
    val skipped: Map<SkipReason, Int>,
    val fromRefresh: Boolean,
    /** Sent from a phone and imported in the background on the TV (shown on top of any screen). */
    val fromLan: Boolean = false,
    /** F3: set for a TS IPTV Source (its own message and Details). */
    val source: SourceImportSummary? = null,
) {
    val skippedTotal: Int get() = source?.skippedCount ?: skipped.values.sum()
}

/** What a stored TS IPTV Source holds, for the "Source added" dialog. */
data class SourceImportSummary(
    val channelCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val skippedCount: Int,
    val report: TsiptvValidationReport,
)

/** The TS IPTV Source import flow (PRD F3 section 1). */
sealed interface SourceImportState {
    class Preview(val preview: TsiptvSourcePreview) : SourceImportState
    data class Fetching(val done: Int, val total: Int) : SourceImportState

    /** An include is adult: ask 18+ before storing. */
    data object AdultInclude : SourceImportState

    /** A document error ([report]) or nothing could be loaded (null). */
    class Rejected(val report: TsiptvValidationReport?) : SourceImportState
}

data class PendingSingleStream(val name: String, val url: String)

enum class HomeNotice {
    FILE_REFRESH_HINT,
    SOURCE_REFRESH_FAILED,
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
    val importSummary: ImportSummary? = null,
    /** The running (or last) import is a playlist accepted from a phone (TV). */
    val importFromLan: Boolean = false,
    /** Name of the running (or last) import, shown in an error about a playlist from a phone. */
    val importName: String? = null,
    val pendingSingleStream: PendingSingleStream? = null,
    /** Display name of a picked file that would replace an earlier import. */
    val pendingFileReplace: String? = null,
    val notice: HomeNotice? = null,
    /** F3: the TS IPTV Source import in progress. */
    val sourceImport: SourceImportState? = null,
    /** F3: the channels up/down zaps through when playback started from a Source Home section. */
    val zapChannels: List<Channel>? = null,
) {
    /** The active playlist is a TS IPTV Source (Source Home instead of the channel list). */
    val isSourcePlaylist: Boolean get() = tss.t.tsiptv.core.tsiptv.TsiptvSourceIds.isSourcePlaylist(playListId)
}

sealed interface HomeEvent {
    data object RefreshIPTVSource : HomeEvent
    data object OnBackPressed : HomeEvent
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

    data class OnImportFile(val name: String, val file: PickedPlaylistFile) : HomeEvent
    data object OnConfirmReplaceFile : HomeEvent
    data object OnCancelReplaceFile : HomeEvent
    data object OnConfirmSingleStream : HomeEvent
    data object OnCancelSingleStream : HomeEvent
    data object OnDismissNotice : HomeEvent
    data object OnDismissImportSummary : HomeEvent

    data class OnOpenVideoPlayer(val channel: Channel) : HomeEvent
    data class OnResumeMediaItem(val mediaItem: MediaItem) : HomeEvent

    data object OnCancelParseIPTVSource : HomeEvent
    data object OnParseIPTVSourceSuccess : HomeEvent
    data class OnPlayNowPlaying(val channel: Channel) : HomeEvent

    data class OnPauseNowPlaying(val channel: Channel) : HomeEvent {}

    data object LoadHistory : HomeEvent {}
    data object RefreshEpgIfNeed : HomeEvent

    // F3: TS IPTV Sources
    data class OnSourceImport(val adultConfirmed: Boolean) : HomeEvent
    data object OnSourceAdultIncludeConfirmed : HomeEvent
    data object OnSourceImportDismiss : HomeEvent

    /** A channel was opened from Source Home; [zapList] is its section (TV zapping). */
    data class OnPlaySourceChannel(val channel: Channel, val zapList: List<Channel>) : HomeEvent
    data class OnConfirmSourceAdult(val playlistId: String) : HomeEvent
    data class OnRemoveSource(val playlistId: String) : HomeEvent
}
