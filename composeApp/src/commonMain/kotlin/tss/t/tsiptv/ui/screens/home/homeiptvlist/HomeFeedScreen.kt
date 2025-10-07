package tss.t.tsiptv.ui.screens.home.homeiptvlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.hello_format
import tsiptv.composeapp.generated.resources.home_search_placeholder
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.ui.screens.ads.AdsViewModel
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.CategoryRow
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.HomeMiniPlayer
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.MiniPlayerHeight
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.homeEmptyIptvSource
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.homeItemList
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.screens.login.provider.LocalAuthProvider
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.HeaderWithAvatar
import tss.t.tsiptv.ui.widgets.SearchWidget
import tss.t.tsiptv.utils.LocalAppViewModelStoreOwner


/**
 * Home feed screen showing the list of channel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeIPTVPlaylistScreen(
    navController: NavHostController,
    parentNavController: NavHostController,
    hazeState: HazeState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    onHomeEvent: (HomeEvent) -> Unit = {},
) {
    val scrollState = rememberLazyListState()
    val authState = LocalAuthProvider.current

    val name = remember(authState) {
        authState?.user?.email ?: ""
    }

    val isInitLoading = remember(homeUiState) {
        homeUiState.isLoading
    }
    val isEmpty = remember(
        homeUiState.listChannels,
        homeUiState.isLoading
    ) {
        !homeUiState.isLoading &&
                homeUiState.listChannels.isEmpty() &&
                homeUiState.playListId.isNullOrEmpty()
    }

    var showStickyHeader by remember { mutableStateOf(false) }
    val categoryListState: LazyListState = rememberLazyListState()
    val viewModelStoreOwner = LocalAppViewModelStoreOwner.current!!
    val playerViewModel = koinViewModel<PlayerViewModel>(viewModelStoreOwner = viewModelStoreOwner)

    val mediaItem by playerViewModel.mediaItemState.collectAsStateWithLifecycle()
    var showMiniPlayer by remember { mutableStateOf(false) }
    var searchOffset by remember {
        mutableStateOf(0)
    }
    val adsViewModel = koinViewModel<AdsViewModel>()

    LaunchedEffect(Unit) {
        adsViewModel.loadAds()
    }

    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo to isEmpty }
            .collect { layoutInfo ->
                val isEmpty = layoutInfo.second
                if (isEmpty && showStickyHeader) {
                    showStickyHeader = false
                    return@collect
                }
                val isVisible = layoutInfo.first.any { itemInfo ->
                    itemInfo.key == "GroupChannelsTitle"
                }
                if (isVisible) {
                    layoutInfo.first.firstOrNull {
                        it.key == "GroupChannelsTitle"
                    }?.offset
                        ?.let {
                            it < searchOffset
                        }?.let {
                            showStickyHeader = it
                        }
                } else if (!homeUiState.isLoading) {
                    showStickyHeader = true
                }
            }
    }

    LifecycleResumeEffect(key1 = mediaItem) {
        showMiniPlayer = mediaItem != MediaItem.EMPTY
        onHomeEvent(HomeEvent.LoadHistory)
        onPauseOrDispose {
            showMiniPlayer = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlass")
    val shimmerColor by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquidFlow"
    )

    // Main content with Scaffold
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HomeFeedTopAppBar(
                hazeState,
                authState,
                name,
                onHomeEvent,
                navController,
                homeUiState,
                searchOffset
            ) {
                searchOffset = it
            }
        },
        content = {
            AnimatedContent(
                modifier = Modifier
                    .fillMaxSize(),
                targetState = isInitLoading,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(220, delayMillis = 90)
                    ).togetherWith(
                        exit = fadeOut(
                            animationSpec = tween(90)
                        )
                    )
                }
            ) { targetState ->
                Box {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize()
                            .hazeSource(hazeState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {

                        item("HomeSpaceTop") {
                            Spacer(Modifier.height(it.calculateTopPadding()))
                        }

                        when {
                            isEmpty -> {
                                homeEmptyIptvSource(navController, parentNavController)
                            }

                            isInitLoading -> {
                                homeLoadingItemList(shimmerColor)
                            }

                            else -> {
                                homeItemList(
                                    adsViewModel = adsViewModel,
                                    homeUiState = homeUiState,
                                    playerUIState = playerUIState,
                                    onHomeEvent = onHomeEvent,
                                    categoryListState = categoryListState
                                )
                            }
                        }

                        item {
                            Spacer(
                                Modifier
                                    .navigationBarsPadding()
                                    .height(contentPadding.calculateBottomPadding() + 12.dp)
                            )
                        }

                        if (showMiniPlayer) {
                            item {
                                Spacer(
                                    Modifier.height(MiniPlayerHeight)
                                )
                            }
                        }
                    }

                    HomeMiniPlayer(
                        showMiniPlayer = showMiniPlayer,
                        contentPadding = contentPadding,
                        onHomeEvent = onHomeEvent,
                        mediaItem = mediaItem,
                        hazeState = hazeState,
                        program = homeUiState.currentProgram
                    ) {
                        showMiniPlayer = false
                    }
                }
            }

            AnimatedVisibility(
                visible = showStickyHeader,
                enter = slideInVertically { -it / 5 },
                exit = fadeOut(tween(90))
            ) {
                CategoryRow(
                    homeUiState = homeUiState,
                    modifier = Modifier.fillMaxWidth()
                        .background(TSColors.BackgroundColor)
                        .hazeEffect(hazeState)
                        .padding(top = it.calculateTopPadding())
                        .padding(bottom = 12.dp),
                    onHomeEvent = onHomeEvent,
                    listState = categoryListState
                )
            }
        }
    )
}

private fun LazyListScope.homeLoadingItemList(shimmerColor: Float) {
    items(10) { size ->
        if (size % 3 == 0) {
            Box(
                modifier = Modifier.padding(16.dp)
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(TSShapes.roundedShape12)
                    .background(
                        TSColors.White.copy(alpha = shimmerColor),
                        TSShapes.roundedShape12
                    )
                    .blur(20.dp)
            )
        } else {
            Box(
                modifier = Modifier.padding(16.dp)
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(TSShapes.roundedShape12)
                    .blur(20.dp)
                    .background(
                        TSColors.White.copy(alpha = shimmerColor),
                        TSShapes.roundedShape12
                    )
            )
        }
    }
}

@Composable
private fun HomeFeedTopAppBar(
    hazeState: HazeState,
    authState: AuthUiState?,
    name: String,
    onHomeEvent: (HomeEvent) -> Unit,
    navController: NavHostController,
    homeUiState: HomeUiState,
    searchOffset: Int,
    onSearchOffsetChange: (Int) -> Unit,
) {
    Column {
        HeaderWithAvatar(
            modifier = Modifier
                .background(TSColors.BackgroundColor)
                .clickable(
                    indication = null,
                    onClick = {},
                    interactionSource = remember {
                        MutableInteractionSource()
                    }
                )
                .hazeEffect(hazeState)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            helloTitle = stringResource(
                Res.string.hello_format,
                authState?.user?.displayName?.let { " $it" } ?: ""),
            name = name,
            notificationCount = 10,
            onSettingClick = {
                onHomeEvent(HomeEvent.OnHomeFeedSettingPressed)
            },
            onNotificationClick = {
                onHomeEvent(HomeEvent.OnHomeFeedNotificationPressed)
            },
            onAvatarClick = {
                navController.navigate(NavRoutes.HomeScreens.PROFILE)
            }
        )

        SearchWidget(
            modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned {
                    onSearchOffsetChange(
                        it.positionOnScreen().y.toInt()
                            .coerceAtLeast(searchOffset)
                    )
                }
                .background(TSColors.BackgroundColor)
                .hazeEffect(hazeState)
                .padding(horizontal = 16.dp)
                .padding(vertical = 16.dp),
            initText = homeUiState.searchText,
            placeholder = stringResource(Res.string.home_search_placeholder),
            onValueChange = {
                onHomeEvent(HomeEvent.OnSearchKeyChange(it))
            },
            onClear = {
                onHomeEvent(HomeEvent.OnSearchKeyChange(""))
            }
        )
    }
}
