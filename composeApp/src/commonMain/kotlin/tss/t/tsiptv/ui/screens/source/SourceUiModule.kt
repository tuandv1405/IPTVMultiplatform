package tss.t.tsiptv.ui.screens.source

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** F3 screens: Source Home (and See all) and About this source. */
val tsiptvUiModule = module {
    viewModelOf(::SourceHomeViewModel)
    viewModelOf(::SourceAboutViewModel)
}
