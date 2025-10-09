package tss.t.tsiptv.ui.screens.programs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.accept
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.home_nav_programs
import tsiptv.composeapp.generated.resources.popup_refresh_program_description
import tsiptv.composeapp.generated.resources.popup_refresh_program_title
import tss.t.tsiptv.ui.screens.ads.AdsViewModel
import tss.t.tsiptv.ui.screens.programs.ProgramViewModel.UIState
import tss.t.tsiptv.ui.screens.programs.uimodel.ProgramEvent
import tss.t.tsiptv.ui.screens.programs.widgets.ChannelInProgramItem
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.AdsItem
import tss.t.tsiptv.ui.widgets.DialogButtonFlowLayout
import tss.t.tsiptv.ui.widgets.HorizontalDividersGradient
import tss.t.tsiptv.ui.widgets.TSAppBar
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.utils.LocalAppViewModelStoreOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelXProgramListScreen(
    uiState: UIState,
    adsViewModel: AdsViewModel,
) {
    val viewModelStoreOwner = LocalAppViewModelStoreOwner.current!!
    val viewModel = koinViewModel<ProgramViewModel>(
        viewModelStoreOwner = viewModelStoreOwner
    )
    var showDialogAskRefresh by remember {
        mutableStateOf(false)
    }
    val infiniteTransition = rememberInfiniteTransition("Loading")
    val shimmerColor by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquidFlow"
    )

    LaunchedEffect(Unit) {
        adsViewModel.loadAds()
    }

    Scaffold(
        topBar = {
            TSAppBar(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.home_nav_programs)
            ) {
                Image(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = {
                            showDialogAskRefresh = true
                        })
                        .padding(8.dp)
                        .size(24.dp),
                    colorFilter = ColorFilter.tint(TSColors.TextSecondary)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(it)
        ) {
            HorizontalDividersGradient()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                userScrollEnabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    items(50) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(40.dp)
                                .blur(20.dp)
                                .background(
                                    TSColors.White.copy(alpha = shimmerColor),
                                    TSShapes.roundedShape12
                                )
                        )
                        HorizontalDividersGradient()
                    }
                }
                items(uiState.programList.size) {
                    val item = uiState.programList[it]
                    ChannelInProgramItem(item) {
                        viewModel.navigateToSelectedProgramList(it)
                    }
                    HorizontalDividersGradient()

                    val showAds = remember(it, item) {
                        if (it % 5 == 0) {
                            item
                        } else {
                            null
                        }
                    }
                    showAds?.let {
                        adsViewModel.RefreshAdsForKeyWithLifeCycle(it.channelId + it.programCount) {
                            AdsItem(it)
                        }
                    }
                }
            }
        }
    }


    if (showDialogAskRefresh) {
        TSDialog(
            title = stringResource(Res.string.popup_refresh_program_title),
            message = stringResource(Res.string.popup_refresh_program_description),
            icon = null,
            buttonFlowLayout = DialogButtonFlowLayout.Row,
            positiveButtonText = stringResource(Res.string.accept),
            negativeButtonText = stringResource(Res.string.cancel),
            onPositiveClick = {
                viewModel.sendEvent(ProgramEvent.RefreshProgram)
                showDialogAskRefresh = false
            },
            onNegativeClick = {
                showDialogAskRefresh = false
            },
            onDismissRequest = {
                showDialogAskRefresh = false
            }
        )
    }
}
