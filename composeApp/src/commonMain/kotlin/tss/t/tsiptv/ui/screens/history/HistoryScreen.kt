package tss.t.tsiptv.ui.screens.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.btn_add_iptv_source_title
import tsiptv.composeapp.generated.resources.empty_history
import tsiptv.composeapp.generated.resources.home_nav_history
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.ui.screens.ads.AdsViewModel
import tss.t.tsiptv.ui.screens.history.widgets.HistoryItems
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.ui.widgets.AdsItem
import tss.t.tsiptv.ui.widgets.GradientButton1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavHostController,
    parentNavController: NavHostController,
    hazeState: HazeState,
    mediaItem: MediaItem? = null,
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    contentPadding: PaddingValues,
    onHomeEvent: (HomeEvent) -> Unit = {},
    onPlay: (Channel) -> Unit = {},
    onPause: (Channel) -> Unit = {},
) {
    val isEmpty = remember(homeUiState.allPlayedChannels.size) {
        homeUiState.allPlayedChannels.isEmpty()
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberTopAppBarState(),
    )
    val adsViewModel = koinViewModel<AdsViewModel>()
    LaunchedEffect(Unit) {
        adsViewModel.loadAds()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.fillMaxWidth()
                    .hazeEffect(hazeState),
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = TSColors.Transparent,
                    scrolledContainerColor = TSColors.Transparent
                ),
                title = {
                    Text(
                        text = stringResource(resource = Res.string.home_nav_history),
                        style = TSTextStyles.primaryToolbarTitle
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.hazeSource(hazeState),
            contentPadding = it
        ) {
            if (isEmpty && !homeUiState.isLoading) {
                item("EmptyIptvHelp") {
                    Column(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .padding(horizontal = 16.dp)
                            .clip(TSShapes.roundedShape16)
                            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
                            .padding(vertical = 32.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.Companion.clip(CircleShape)
                                .background(TSColors.IconContainerColor)
                                .size(64.dp)
                        ) {
                            Image(
                                imageVector = Icons.Rounded.Bookmarks,
                                contentDescription = "Save",
                                modifier = Modifier.Companion.size(26.dp)
                                    .align(Alignment.Companion.Center),
                                colorFilter = ColorFilter.Companion.tint(Color.Companion.White)
                            )
                        }

                        Spacer(Modifier.Companion.height(20.dp))

                        Text(
                            stringResource(Res.string.empty_history),
                            style = MaterialTheme.typography.titleMedium
                                .copy(
                                    color = TSColors.TextPrimary,
                                    fontWeight = FontWeight.Companion.Medium,
                                    fontSize = 18.sp
                                )
                        )
                        Spacer(Modifier.Companion.height(16.dp))

                        GradientButton1(
                            text = stringResource(Res.string.btn_add_iptv_source_title),
                        ) {
                            parentNavController.navigate(NavRoutes.ImportIptv) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }

            item("Ads") {
                val ads by adsViewModel.displayAd.collectAsStateWithLifecycle()
                ads?.let {
                    AdsItem(it)
                }
            }

            items(homeUiState.allPlayedChannels) {
                HistoryItems(
                    channelWithHistory = it,
                    paddingValues = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp
                    ),
                    isPlaying = playerUIState.isPlaying && mediaItem?.id == it.channelId,
                    onItemClick = {
                        onHomeEvent(HomeEvent.OnOpenVideoPlayer(it.getChannel()))
                    }
                ) {
                    if (playerUIState.isPlaying) {
                        onPause(it.getChannel())
                    } else {
                        onPlay(it.getChannel())
                    }
                }
            }

            item {
                Spacer(
                    Modifier
                        .navigationBarsPadding()
                        .height(contentPadding.calculateBottomPadding() + 12.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = it.calculateTopPadding()),
            color = TSColors.White.copy(alpha = 0.08f)
        )
    }
}