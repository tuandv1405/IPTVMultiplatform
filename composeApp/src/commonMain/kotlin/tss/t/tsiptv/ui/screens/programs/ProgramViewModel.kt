package tss.t.tsiptv.ui.screens.programs

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.ui.screens.programs.uimodel.ProgramEvent
import tss.t.tsiptv.usecase.playlist.GetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.playlist.SetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.programs.GetChannelsWithValidProgramCounts
import tss.t.tsiptv.usecase.programs.GetCurrentProgramChannelList
import tss.t.tsiptv.usecase.programs.ParseProgramListUseCase

class ProgramViewModel(
    private val getCurrentPlaylistUC: GetCurrentPlaylistUseCase,
    private val setCurrentPlaylistUC: SetCurrentPlaylistUseCase,
    private val getChannelsWithValidProgramCounts: GetChannelsWithValidProgramCounts,
    private val getCurrentProgramChannelUC: GetCurrentProgramChannelList,
    private val parseProgramListUC: ParseProgramListUseCase,
) : ViewModel() {
    private val _listProgramUIState by lazy {
        MutableStateFlow(UIState())
    }
    val listProgramUIState: StateFlow<UIState>
        get() = _listProgramUIState

    private val _event by lazy {
        MutableSharedFlow<ProgramEvent>()
    }

    val event: SharedFlow<ProgramEvent>
        get() = _event.asSharedFlow()

    init {
        getListProgram()
        viewModelScope.launch {
            event.collect {
                if (it == ProgramEvent.RefreshProgram) {
                    refreshProgram()
                }
            }
        }
    }

    fun getListProgram() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_listProgramUIState.value.isLoading) {
                _listProgramUIState.update {
                    it.copy(isLoading = true)
                }
            }
            val currentPlaylist = getCurrentPlaylistUC().takeIf {
                !it.isNullOrEmpty()
            }
            if (currentPlaylist.isNullOrEmpty()) {
                _listProgramUIState.update {
                    it.copy(isLoading = false)
                }
                return@launch
            }
            val channelWithProgramCount = getChannelsWithValidProgramCounts(currentPlaylist)
            _listProgramUIState.update {
                it.copy(
                    programList = channelWithProgramCount,
                    isLoading = false
                )
            }
        }
    }

    fun navigateToSelectedProgramList(
        currentChannelWithProgramCount: ChannelWithProgramCount,
    ) {
        sendEvent(ProgramEvent.NavigateToDetail(currentChannelWithProgramCount))
    }

    fun sendEvent(event: ProgramEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }

    fun refreshProgram() {
        viewModelScope.launch(Dispatchers.IO) {
            _listProgramUIState.update {
                it.copy(isLoading = true)
            }
            val currentPlaylist = getCurrentPlaylistUC.playlist()

            if (currentPlaylist == null) {
                _listProgramUIState.update {
                    it.copy(isLoading = false)
                }
                return@launch
            }
            val url = currentPlaylist.epgUrl ?: return@launch
            parseProgramListUC(
                playListId = currentPlaylist.id,
                epgUrl = url
            )
            getListProgram()
        }
    }

    @Stable
    data class UIState(
        val programList: List<ChannelWithProgramCount> = emptyList(),
        val page: Int = 0,
        val totalItem: Int = 0,
        val isLoading: Boolean = false,
        val selectedChannel: ChannelWithProgramCount? = null,
    )
}
