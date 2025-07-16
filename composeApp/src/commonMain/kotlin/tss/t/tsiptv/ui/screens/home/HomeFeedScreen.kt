package tss.t.tsiptv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.all_channels_title
import tsiptv.composeapp.generated.resources.btn_add_iptv_source_title
import tsiptv.composeapp.generated.resources.empty_iptv_source_title
import tsiptv.composeapp.generated.resources.ic_home_empty
import tsiptv.composeapp.generated.resources.ic_info
import tsiptv.composeapp.generated.resources.iptv_help_title
import tsiptv.composeapp.generated.resources.what_is_iptv_desc
import tsiptv.composeapp.generated.resources.what_is_iptv_title
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.ui.MediaPlayerContent
import tss.t.tsiptv.player.ui.MediaPlayerView
import tss.t.tsiptv.ui.screens.home.widget.HomeCategoryItem
import tss.t.tsiptv.ui.screens.home.widget.HomeChannelItem
import tss.t.tsiptv.ui.screens.home.widget.NowPlayingCard
import tss.t.tsiptv.ui.screens.login.provider.LocalAuthProvider
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.GradientButton1
import tss.t.tsiptv.ui.widgets.HeaderWithAvatar


/**
 * Home feed screen showing the list of channels
 */
@Composable
fun HomeFeedScreen(
    navController: NavHostController,
    parentNavController: NavHostController,
    hazeState: HazeState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    homeUiState: HomeUiState,
    onHomeEvent: (HomeEvent) -> Unit = {},
) {
    val scrollState = rememberLazyListState()
    val authState = LocalAuthProvider.current

    val helloTitle = remember(authState) {
        "Hello${authState?.user?.displayName?.let { " $it" } ?: ""}"
    }
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
    val viewStoreOwner = LocalViewModelStoreOwner.current!!
    val playerViewModel = viewModel<PlayerViewModel>(viewStoreOwner) {
        PlayerViewModel(
            _mediaPlayer = mediaPlayer,
            _iptvDatabase = iptvDatabase
        )
    }
    val mediaItem by playerViewModel.mediaItemState.collectAsStateWithLifecycle()
    var showMiniPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo to isEmpty }
            .collect { layoutInfo ->
                val isEmpty = layoutInfo.second
                if (isEmpty) {
                    showStickyHeader = false
                    return@collect
                }
                val isVisible = layoutInfo.first.any { itemInfo ->
                    itemInfo.key == "GroupChannelsTitle"
                }
                if (showStickyHeader) {
                    if (isVisible) {
                        showStickyHeader = false
                    }
                } else {
                    if (!isVisible) {
                        showStickyHeader = true
                    }
                }
            }
    }

    LifecycleResumeEffect(mediaItem) {
        showMiniPlayer = mediaItem != MediaItem.EMPTY
        onPauseOrDispose {
            showMiniPlayer = false
        }
    }

    Scaffold(
        topBar = {
            Column {
                HeaderWithAvatar(
                    modifier = Modifier
                        .background(TSColors.BackgroundColor)
                        .hazeEffect(hazeState)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    helloTitle = helloTitle,
                    name = name,
                    notificationCount = 10,
                    onSettingClick = {
                        parentNavController.navigate(NavRoutes.LanguageSettings())
                    },
                    onNotificationClick = {

                    },
                    onAvatarClick = {
                        navController.navigate(NavRoutes.HomeScreens.PROFILE)
                    }
                )

                AnimatedVisibility(showStickyHeader) {
                    if (showStickyHeader) {
                        CategoryRow(
                            homeUiState = homeUiState,
                            modifier = Modifier.fillMaxWidth()
                                .background(TSColors.BackgroundColor)
                                .hazeEffect(hazeState)
                                .padding(vertical = 12.dp),
                            onHomeEvent = onHomeEvent,
                            listState = categoryListState
                        )
                    }
                }
            }
        }
    ) {
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
                        homeInitLoading(hazeState)
                    }

                    else -> {
                        homeItemList(
                            homeUiState = homeUiState,
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
            }

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
                            .size(160.dp, 90.dp)
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
                            text = mediaItem.id,
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
                                    showMiniPlayer = false
                                    playerViewModel.stopMedia()
                                }
                            },
                        colorFilter = ColorFilter.tint(TSColors.TextPrimary)
                    )
                }
            }
        }
    }
}

private fun LazyListScope.homeItemList(
    homeUiState: HomeUiState,
    categoryListState: LazyListState,
    onHomeEvent: (HomeEvent) -> Unit,
) {
    item("NowWatchingTitle") {
    }
    if (homeUiState.nowWatching != null && homeUiState.nowWatchingCategory != null) {
        item("NowWatchingCard") {
            NowPlayingCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                channel = homeUiState.nowWatching,
                category = homeUiState.nowWatchingCategory,
                onEvent = onHomeEvent,
            )
        }
    }
    item("HistoryTitle") {
    }
    item("HistoryCard") {}

    item("GroupChannelsTitle") {
        CategoryRow(
            homeUiState = homeUiState,
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 16.dp),
            onHomeEvent = onHomeEvent,
            listState = categoryListState
        )
    }

    items(homeUiState.listChannels) { channel ->
        HomeChannelItem(
            channel = channel,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            onHomeEvent(HomeEvent.OnOpenVideoPlayer(it))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryRow(
    homeUiState: HomeUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onHomeEvent: (HomeEvent) -> Unit,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        state = listState
    ) {
        item {
            Spacer(Modifier.width(8.dp))
        }

        items(homeUiState.categories.size) {
            if (it == 0) {
                HomeCategoryItem(
                    categoryName = stringResource(Res.string.all_channels_title),
                    isSelected = remember(homeUiState.selectedCategory) {
                        homeUiState.selectedCategory == null
                    },
                    onItemClick = {
                        onHomeEvent(HomeEvent.OnClearFilterCategory)
                    }
                )
                return@items
            }
            val item = remember(it) {
                homeUiState.categories[it - 1]
            }
            HomeCategoryItem(
                categoryName = item.name,
                isSelected = remember(homeUiState.selectedCategory) {
                    homeUiState.selectedCategory == item
                },
                onItemClick = {
                    onHomeEvent(HomeEvent.OnCategorySelected(item))
                }
            )
        }
    }
}

private fun LazyListScope.homeInitLoading(hazeState: HazeState) {

}

private fun LazyListScope.homeEmptyIptvSource(
    navController: NavHostController,
    parentNavController: NavHostController,
) {
    item("EmptyIptvSourceCard") {
        EmptyIptvSourceCard(
            navController = navController,
            parentNavController = parentNavController,
        )
    }
    item("EmptyIptvHelp") {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(Res.string.iptv_help_title),
            style = MaterialTheme.typography.titleLarge
                .copy(
                    color = TSColors.TextSecondary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    textDecoration = TextDecoration.Underline
                ),
            modifier = Modifier.clickable {
                val router =
                    NavRoutes.WebView("https://dvt1405.github.io/iMediaReleasePages/")
                parentNavController.navigate(
                    router,
                ) {
                    launchSingleTop = true
                }
            }
        )
        Spacer(Modifier.height(20.dp))
    }

    item("EmptyIPTVIntroduce") {
        EmptyIPTVIntroduce()
    }
}

@Composable
private fun EmptyIptvSourceCard(
    navController: NavHostController,
    parentNavController: NavHostController,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.clip(CircleShape)
                .background(TSColors.IconContainerColor)
                .size(64.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_home_empty),
                contentDescription = "Save",
                modifier = Modifier.size(26.dp)
                    .align(Alignment.Center),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(Res.string.empty_iptv_source_title),
            style = MaterialTheme.typography.titleMedium
                .copy(
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp
                )
        )
        Spacer(Modifier.height(16.dp))

        GradientButton1(
            text = stringResource(Res.string.btn_add_iptv_source_title),
        ) {
            parentNavController.navigate(NavRoutes.ImportIptv) {
                launchSingleTop = true
            }
        }
    }
}

@Composable
fun EmptyIPTVIntroduce() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(vertical = 24.dp, horizontal = 24.dp)
    ) {
        Image(
            painterResource(Res.drawable.ic_info),
            contentDescription = "Logo",
            modifier = Modifier.size(32.dp)
                .clip(CircleShape)
                .background(Color(0x333B82F6))
                .padding(8.dp),
            colorFilter = ColorFilter.tint(Color(0xFF60A5FA))
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                stringResource(Res.string.what_is_iptv_title),
                style = MaterialTheme.typography.titleMedium
                    .copy(
                        color = TSColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.what_is_iptv_desc),
                style = MaterialTheme.typography.bodyMedium
                    .copy(
                        color = TSColors.TextSecondary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                    )
            )
        }
    }
}
