package tss.t.tsiptv.usecase.di

import org.koin.dsl.module
import tss.t.tsiptv.usecase.playlist.GetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.playlist.PlaylistImporter
import tss.t.tsiptv.usecase.playlist.SetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.programs.GetChannelsWithValidProgramCounts
import tss.t.tsiptv.usecase.programs.GetCurrentProgramChannelList
import tss.t.tsiptv.usecase.programs.ParseProgramListUseCase

val useCaseModule = module {
    factory<GetCurrentPlaylistUseCase> {
        GetCurrentPlaylistUseCase(
            get(),
            get(),
            get()
        )
    }

    factory<SetCurrentPlaylistUseCase> {
        SetCurrentPlaylistUseCase(
            get(),
            get()
        )
    }

    factory<GetChannelsWithValidProgramCounts> {
        GetChannelsWithValidProgramCounts(
            get()
        )
    }

    factory<GetCurrentProgramChannelList> {
        GetCurrentProgramChannelList(
            get()
        )
    }

    factory<PlaylistImporter> {
        PlaylistImporter(
            database = get(),
            networkClient = get(),
            sources = get(),
        )
    }

    factory<ParseProgramListUseCase> {
        ParseProgramListUseCase(
            get(),
            get(),
            get()
        )
    }
}
