package tss.t.tsiptv.usecase.di

import org.koin.dsl.module
import tss.t.tsiptv.usecase.playlist.GetCurrentPlaylistUseCase
import tss.t.tsiptv.usecase.playlist.SetCurrentPlaylistUseCase

val useCaseModule = module {
    factory<GetCurrentPlaylistUseCase> {
        GetCurrentPlaylistUseCase(
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
}
