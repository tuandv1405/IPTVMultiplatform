package tss.t.tsiptv.ui.screens.programs.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.ui.screens.programs.uimodel.ProgramEvent
import tss.t.tsiptv.usecase.programs.GetCurrentProgramChannelList

class ProgramDetailViewModel(
    private val getCurrentProgramChannelUC: GetCurrentProgramChannelList,
) : ViewModel() {
    private val _uiState by lazy {
        MutableStateFlow(UIState())
    }

    val uiState: StateFlow<UIState>
        get() = _uiState

    private val _uiEvents by lazy {
        MutableSharedFlow<ProgramEvent>()
    }

    val uiEvent: StateFlow<UIState>
        get() = _uiState



    fun loadProgram(channel: ChannelWithProgramCount) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    channelId = channel.channelId,
                    isLoading = true,
                    programList = emptyList()
                )
            }
            runCatching {
                val programList = getCurrentProgramChannelUC(channel.channelId)
                _uiState.update {
                    it.copy(
                        programList = programList,
                        isLoading = false
                    )
                }
            }.onFailure {
                _uiState.update { it ->
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    data class UIState(
        val channelId: String = "",
        val programList: List<IPTVProgram> = emptyList(),
        val isLoading: Boolean = true,
    )
}