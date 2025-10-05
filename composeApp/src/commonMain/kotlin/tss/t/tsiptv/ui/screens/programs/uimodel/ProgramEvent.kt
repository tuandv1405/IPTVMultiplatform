package tss.t.tsiptv.ui.screens.programs.uimodel

import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount

sealed interface ProgramEvent {
    data class NavigateToDetail(val channel: ChannelWithProgramCount) : ProgramEvent
    data object RefreshProgram : ProgramEvent
}
