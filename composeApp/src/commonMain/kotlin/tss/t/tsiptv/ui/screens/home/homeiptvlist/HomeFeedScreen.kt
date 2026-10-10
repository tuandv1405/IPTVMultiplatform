package tss.t.tsiptv.ui.screens.home.homeiptvlist

import tss.t.tsiptv.core.ads.AdsPolicy
import tss.t.tsiptv.ui.ads.rememberAdsState
import tss.t.tsiptv.core.ads.AdPlacement
import tss.t.tsiptv.ui.ads.BannerAdSlot
import tss.t.tsiptv.ui.screens.ads.rememberShopeeFallback
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.HOME_BANNER_ITEM_KEY
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
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
    val adsViewModel = koinViewModel<AdsViewModel>(
        viewModelStoreOwner = viewModelStoreOwner
    )

    LaunchedEffect(Unit) {
        adsViewModel.loadAds()
    }
    // PRD R5: native ad slots in the channel list (none during the first 24 h or on TV).
    val adsState = rememberAdsState()
    val channelRows = remember(homeUiState.listChannels, adsState.any) {
        AdsPolicy.interleave(homeUiState.listChannels, withAds = adsState.any)
    }
    // PRD R3: the sticky banner overlay's height (the list keeps that much room for it) and the
    // pinned category chips' height (the banner pins right under them).
    var bannerHeightPx by remember { mutableStateOf(0) }
    var stickyHeaderHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val bannerSpace = with(density) { if (bannerHeightPx > 0) bannerHeightPx.toDp() + 8.dp else 0.dp }

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
                                    channelRows = channelRows,
                                    homeUiState = homeUiState,
                                    playerUIState = playerUIState,
                                    onHomeEvent = onHomeEvent,
                                    categoryListState = categoryListState,
                                    bannerSpace = bannerSpace,
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

                    // PRD R3: ONE banner for the screen. It sits on its list item under the category chips
                    // and, once that scrolls up, stays pinned under the sticky chips (never re-created).
                    if (!isEmpty && !targetState) {
                        val topPx = with(density) { it.calculateTopPadding().roundToPx() }
                        // N1: composed (and so requested) only once its row or the pinned position is on
                        // screen; from then on the same AdView is kept for the screen.
                        // "On screen" = its top is above the bottom bar. It must stay there for a
                        // moment: the rows above it (Now Playing, Continue watching) load a little
                        // after the channels and push it down again on a cold start.
                        val bottomBarPx = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
                        // A new width (rotation) means a new AdView and request: wait again until
                        // the banner is on screen at that width (QC AdMob r3, R3-1).
                        val windowWidthPx = LocalWindowInfo.current.containerSize.width
                        var bannerStarted by remember(windowWidthPx) { mutableStateOf(false) }
                        LaunchedEffect(scrollState, bottomBarPx, windowWidthPx) {
                            snapshotFlow {
                                val y = homeBannerY(scrollState, pinnedY = 0)
                                y != BANNER_OFF_SCREEN &&
                                    y < scrollState.layoutInfo.viewportEndOffset - bottomBarPx
                            }.collectLatest { onScreen ->
                                if (onScreen && !bannerStarted) {
                                    delay(BANNER_START_DELAY_MS)
                                    bannerStarted = true
                                }
                            }
                        }
                        if (bannerStarted) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .offset {
                                        val y = homeBannerY(
                                            scrollState,
                                            pinnedY = if (showStickyHeader) stickyHeaderHeightPx else topPx,
                                        )
                                        IntOffset(
                                            0,
                                            if (y == BANNER_OFF_SCREEN) {
                                                scrollState.layoutInfo.viewportEndOffset * 2
                                            } else {
                                                y
                                            },
                                        )
                                    }
                                    .onSizeChanged { size -> bannerHeightPx = size.height }
                                    .background(TSColors.BackgroundColor)
                                    // N3: a swipe that starts on the banner scrolls the list; taps still
                                    // reach the ad.
                                    .scrollListOnDrag(scrollState)
                            ) {
                                BannerAdSlot(
                                    placement = AdPlacement.HOME_BANNER,
                                    // A gap above and below (part of the overlay, not the ad) where a
                                    // swipe always scrolls the list.
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    fallback = rememberShopeeFallback(adsViewModel),
                                    removeAdsLink = true,
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
                        .onSizeChanged { size -> stickyHeaderHeightPx = size.height }
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

/**
 * Where the sticky Home banner goes: on its list item while that is below [pinnedY], pinned at
 * [pinnedY] once the item has scrolled up past it, and off screen while the item is still further
 * down (or the channel list is not shown).
 */
private const val BANNER_OFF_SCREEN = Int.MIN_VALUE

/** The banner row must be on screen this long before the banner is created (and requested). */
private const val BANNER_START_DELAY_MS = 500L

/**
 * Where the sticky Home banner goes: on its list item while that is below [pinnedY], pinned at
 * [pinnedY] once the item has scrolled up past it, and [BANNER_OFF_SCREEN] while the item is still
 * further down (or the channel list is not shown).
 */
private fun homeBannerY(listState: LazyListState, pinnedY: Int): Int {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == HOME_BANNER_ITEM_KEY }
    if (item != null) return maxOf(item.offset, pinnedY)
    val scrolledPast = info.visibleItemsInfo.any {
        val key = it.key as? String ?: return@any false
        key.startsWith("ch:") || key.startsWith("ad:")
    }
    return if (scrolledPast) pinnedY else BANNER_OFF_SCREEN
}

/**
 * Vertical drags that start on this element scroll [listState] (with fling) instead of going to
 * the child view. Only a drag past the touch slop is consumed, so taps are untouched and the ad's
 * own click handling stays as it is; a consumed drag cancels the touch for the ad view, so a swipe
 * never turns into an accidental click.
 */
@Composable
private fun Modifier.scrollListOnDrag(listState: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    return pointerInput(listState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            var pending = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                val dy = change.positionChange().y
                if (!dragging) {
                    pending += dy
                    if (abs(pending) > viewConfiguration.touchSlop) {
                        dragging = true
                        listState.dispatchRawDelta(-pending)
                    }
                } else {
                    listState.dispatchRawDelta(-dy)
                }
                if (dragging) change.consume()
            }
            if (dragging) {
                val velocity = tracker.calculateVelocity().y
                scope.launch { listState.scroll { with(fling) { performFling(-velocity) } } }
            }
        }
    }
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
