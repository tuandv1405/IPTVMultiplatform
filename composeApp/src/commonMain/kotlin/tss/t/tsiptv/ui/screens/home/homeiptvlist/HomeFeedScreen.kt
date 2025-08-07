package tss.t.tsiptv.ui.screens.home.homeiptvlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.hello_format
import tsiptv.composeapp.generated.resources.home_search_placeholder
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.ui.MediaPlayerContent
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.CategoryRow
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.homeEmptyIptvSource
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.homeItemList
import tss.t.tsiptv.ui.screens.login.provider.LocalAuthProvider
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.HeaderWithAvatar
import tss.t.tsiptv.ui.widgets.SearchWidget


/**
 * Home feed screen showing the list of channel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
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
    val mediaPlayer = koinInject<MediaPlayer>()
    val iptvDatabase = koinInject<IPTVDatabase>()
    val historyTracker = koinInject<ChannelHistoryTracker>()
    val viewStoreOwner = LocalViewModelStoreOwner.current!!
    val playerViewModel = viewModel<PlayerViewModel>(viewStoreOwner) {
        PlayerViewModel(
            _mediaPlayer = mediaPlayer,
            _iptvDatabase = iptvDatabase,
            historyTracker = historyTracker
        )
    }
    val mediaItem by playerViewModel.mediaItemState.collectAsStateWithLifecycle()
    var showMiniPlayer by remember { mutableStateOf(false) }
    var searchOffset by remember {
        mutableStateOf(0)
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

    // Dialog-based bottom sheet state - BEST PRACTICE for dialog behavior
    var showBottomSheet by remember { mutableStateOf(false) }

    // Main content with Scaffold
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
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
                        showBottomSheet = true
                    },
                    onNotificationClick = {
                        showBottomSheet = true
                    },
                    onAvatarClick = {
                        navController.navigate(NavRoutes.HomeScreens.PROFILE)
                    }
                )

                SearchWidget(
                    modifier = Modifier.fillMaxWidth()
                        .onGloballyPositioned {
                            searchOffset = it.positionOnScreen().y.toInt()
                                .coerceAtLeast(searchOffset)
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
                if (targetState) {
                    if (isInitLoading) {
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

                        Column(
                            modifier = Modifier
                                .padding(top = it.calculateTopPadding())
                                .fillMaxSize(),
                        ) {
                            repeat(2) {
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
                } else {
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
                                }

                                else -> {
                                    homeItemList(
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
                            program = homeUiState.currentProgram,
                            hazeState = hazeState,
                            playerViewModel = playerViewModel,
                            onHideMiniPlayer = {
                                showMiniPlayer = false
                            }
                        )
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

    if (showBottomSheet) {
        SettingOptionsBottomSheet(
            parentNavController = parentNavController,
            onDismissRequest = {
                showBottomSheet = false
            },
            onHomeEvent = onHomeEvent
        )
    }
}

private val MiniPlayerHeight = 90.dp

@Composable
private fun BoxScope.HomeMiniPlayer(
    showMiniPlayer: Boolean,
    contentPadding: PaddingValues,
    onHomeEvent: (HomeEvent) -> Unit,
    mediaItem: MediaItem,
    hazeState: HazeState,
    program: IPTVProgram?,
    playerViewModel: PlayerViewModel,
    onHideMiniPlayer: () -> Unit,
) {
    AnimatedVisibility(
        visible = showMiniPlayer,
        modifier = Modifier
            .align(Alignment.BottomCenter),
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = 300,
                easing = LinearOutSlowInEasing
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(
                durationMillis = 300,
                easing = LinearOutSlowInEasing
            )
        ) + fadeOut(
            targetAlpha = 0.5f,
            animationSpec = tween(
                durationMillis = 300,
                easing = LinearOutSlowInEasing
            )
        )
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(TSShapes.roundedShape8)
                .clickable {
                    onHomeEvent(HomeEvent.OnResumeMediaItem(mediaItem))
                }
                .hazeEffect(hazeState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaPlayerContent(
                player = playerViewModel.player,
                modifier = Modifier
                    .size(160.dp, MiniPlayerHeight)
                    .clip(TSShapes.roundedShape8)
                    .background(
                        color = TSColors.PlayerBackgroundColor,
                        shape = TSShapes.roundedShape8
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = mediaItem.title,
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = program?.title ?: mediaItem.id,
                    color = TSColors.TextSecondaryLight,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    lineHeight = 14.sp
                )
            }
            Image(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                modifier = Modifier.size(32.dp)
                    .clip(CircleShape)
                    .padding(4.dp)
                    .clickable {
                        if (showMiniPlayer) {
                            onHideMiniPlayer()
                            playerViewModel.stopMedia()
                        }
                    },
                colorFilter = ColorFilter.tint(TSColors.TextPrimary)
            )
        }
    }
}
