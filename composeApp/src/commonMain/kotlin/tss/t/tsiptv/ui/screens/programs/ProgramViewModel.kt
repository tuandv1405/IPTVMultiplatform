package tss.t.tsiptv.ui.screens.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.parser.IPTVProgram

class ProgramViewModel(
    private val iptvDatabase: IPTVDatabase,
) : ViewModel() {
    private val _listProgramUIState by lazy {
        MutableStateFlow(UIState())
    }
    val listProgramUIState: StateFlow<UIState>
        get() = _listProgramUIState

    data class UIState(
        val programList: List<IPTVProgram> = emptyList(),
        val page: Int = 0,
        val totalItem: Int = 0,
        val isLoading: Boolean = false,
    )

    fun getListProgram() {
        viewModelScope.launch(Dispatchers.IO) {
            iptvDatabase.programDao.getAllPrograms()
                .collect {

                }
        }
    }
}
