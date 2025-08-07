package tss.t.tsiptv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_dislike
import tsiptv.composeapp.generated.resources.ic_like
import tsiptv.composeapp.generated.resources.ic_report
import tsiptv.composeapp.generated.resources.ic_share
import tsiptv.composeapp.generated.resources.player_dislike
import tsiptv.composeapp.generated.resources.player_like
import tsiptv.composeapp.generated.resources.player_report
import tsiptv.composeapp.generated.resources.player_share
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.ui.MediaPlayerView
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.home.widget.HomeChannelItem
import tss.t.tsiptv.ui.screens.programs.ProgramItem
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.ui.widgets.HorizontalDividersGradient
import tss.t.tsiptv.utils.KeepScreenOnState
import tss.t.tsiptv.utils.getScreenOrientationUtils


/**
 * A composable that displays a media player screen with video content and playback controls.
 *
 * @param mediaItem The media item to be played, containing title, artist and source information
 * @param mediaPlayer The media player instance that handles playback functionality
 * @param onEvent Callback to handle player events like play/pause, PiP mode etc.
 */
@Composable
fun PlayerScreen(
    mediaItem: MediaItem,
    homeUIState: HomeUiState,
    mediaPlayer: MediaPlayer,
    playerControlState: PlayerUIState,
    onEvent: (PlayerEvent) -> Unit,
) {
    val isPlaying by mediaPlayer.isPlaying.collectAsState()
    var showTitleUnderPlayer by remember {
        mutableStateOf(false)
    }
    var showDetailsScreen by remember {
        mutableStateOf(false)
    }
    var detailsScreenPaddingTop by remember {
        mutableStateOf(0.dp)
    }
    val scrollState = rememberLazyListState()
    val density = LocalDensity.current
    val titleHeight = remember {
        with(density) {
            -40.dp.toPx()
        }
    }
    val mediaItemDescription = remember(homeUIState.currentProgram, mediaItem) {
        homeUIState.currentProgram?.title ?: mediaItem.artist
    }
    val programListState = rememberLazyListState()
    val programListUnderStickyState = rememberLazyListState()

    KeepScreenOnState(rememberUpdatedState(isPlaying))
    DisposableEffect(Unit) {
        onDispose {
            onEvent(PlayerEvent.OnPictureInPicture)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo }
            .collect { layoutInfo ->
                showTitleUnderPlayer = layoutInfo.firstOrNull { itemInfo ->
                    itemInfo.key == "ItemTitle"
                }?.offset.let {
                    it == null || it <= titleHeight
                }
            }
    }
    LaunchedEffect(playerControlState.isFullScreen) {
        if (playerControlState.isFullScreen) {
            getScreenOrientationUtils().hideSystemUI()
        } else {
            getScreenOrientationUtils().showSystemUI()
        }
    }

    LaunchedEffect(homeUIState.currentProgram, homeUIState.currentProgramList) {
        if (homeUIState.currentProgramList.isNullOrEmpty() ||
            homeUIState.currentProgram == null
        ) {
            return@LaunchedEffect
        }

        val itemIndex = homeUIState.currentProgramList.indexOfFirst {
            it.id == homeUIState.currentProgram.id &&
                    it.channelId == homeUIState.currentProgram.channelId
        }
        if (itemIndex > 0) {
            programListState.scrollToItem(itemIndex)
        }
    }

    LaunchedEffect(
        homeUIState.currentProgram,
        homeUIState.currentProgramList
    ) {
        if (homeUIState.currentProgramList.isNullOrEmpty() ||
            homeUIState.currentProgram == null
        ) {
            return@LaunchedEffect
        }

        val itemIndex = homeUIState.currentProgramList.indexOfFirst {
            it.id == homeUIState.currentProgram.id &&
                    it.channelId == homeUIState.currentProgram.channelId
        }
        if (itemIndex > 0) {
            programListUnderStickyState.scrollToItem(itemIndex)
        }
    }

    if (playerControlState.isFullScreen) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .background(TSColors.PlayerBackgroundColor)
        ) {
            MediaPlayerView(
                mediaItem = mediaItem,
                currentProgram = homeUIState.currentProgram,
                player = mediaPlayer,
                modifier = Modifier.fillMaxWidth()
                    .padding(
                        start = if (playerControlState.isFitWidth ||
                            playerControlState.isFillScreen169
                        ) 0.dp else it.calculateStartPadding(LayoutDirection.Ltr),
                        end = if (playerControlState.isFitWidth ||
                            playerControlState.isFillScreen169
                        ) 0.dp else it.calculateRightPadding(LayoutDirection.Ltr),
                    ),
                playerUIState = playerControlState,
                onPlayerControl = onEvent
            )
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
            .background(TSColors.DeepBlue),
        topBar = {
            Column(modifier = Modifier) {
                Spacer(
                    modifier = Modifier.fillMaxWidth()
                        .background(TSColors.PlayerBackgroundColor)
                        .statusBarsPadding()
                )
                MediaPlayerView(
                    mediaItem = mediaItem,
                    player = mediaPlayer,
                    modifier = Modifier
                        .fillMaxWidth(),
                    playerUIState = playerControlState,
                    onPlayerControl = onEvent
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
            state = scrollState,
            userScrollEnabled = !showDetailsScreen
        ) {
            item("ItemTitle") {
                HeaderUnderPlayerItem(
                    mediaItem = mediaItem,
                    mediaItemDescription = mediaItemDescription,
                    showDetailsScreen = showDetailsScreen,
                    onSizeChanged = {
                        detailsScreenPaddingTop = it
                    },
                    onShowDetailsChanged = {
                        showDetailsScreen = it
                    }
                )

                HorizontalDividersGradient(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item("BannerAdSpace") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TSShapes.roundedShape16)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp)
                        .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Banner Ads Here"
                    )
                }
            }

            item("Interaction") {
                InteractionsSpace()
            }

            items(homeUIState.relatedChannels) {
                HomeChannelItem(
                    it,
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp),
                    onItemClick = {
                        onEvent(PlayerEvent.PlayIptv(it))
                    }
                )
            }
        }

        ProgramListVisibility(
            modifier = Modifier.padding(
                top = paddingValues.calculateTopPadding() + detailsScreenPaddingTop,
            ),
            visible = showDetailsScreen,
            containerColor = TSColors.BackgroundColor,
            paddingValues = paddingValues,
            detailsScreenPaddingTop = detailsScreenPaddingTop,
            programListState = programListState,
            homeUIState = homeUIState,
            contentPadding = remember(key1 = detailsScreenPaddingTop) {
                PaddingValues(
                    bottom = paddingValues.calculateBottomPadding()
                )
            },
            onEvent = onEvent
        )

        var showDetailsUnderStickyHeader by remember {
            mutableStateOf(false)
        }

        DynamicStickyHeader(
            showDetailsUnderStickyHeader = showDetailsUnderStickyHeader,
            paddingValues = paddingValues,
            detailsScreenPaddingTop = detailsScreenPaddingTop,
            programListUnderStickyState = programListUnderStickyState,
            homeUIState = homeUIState,
            onEvent = onEvent,
            showTitleUnderPlayer = showTitleUnderPlayer,
            mediaItem = mediaItem,
            mediaItemDescription = mediaItemDescription
        ) {
            showDetailsUnderStickyHeader = it
        }
    }
}

@Composable
private fun InteractionsSpace() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        InteractionItem(
            iconPainter = painterResource(Res.drawable.ic_like),
            title = stringResource(Res.string.player_like)
        ) {

        }
        InteractionItem(
            iconPainter = painterResource(Res.drawable.ic_dislike),
            title = stringResource(Res.string.player_dislike)
        ) {

        }
        InteractionItem(
            iconPainter = painterResource(Res.drawable.ic_share),
            title = stringResource(Res.string.player_share)
        ) {

        }
        InteractionItem(
            iconPainter = painterResource(Res.drawable.ic_report),
            title = stringResource(Res.string.player_report)
        ) {

        }
    }
}

@Composable
fun HeaderUnderPlayerItem(
    mediaItem: MediaItem,
    mediaItemDescription: String,
    showDetailsScreen: Boolean,
    onSizeChanged: (height: Dp) -> Unit,
    onShowDetailsChanged: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val degrees by animateFloatAsState(targetValue = if (showDetailsScreen) 180f else 0f)

    Row(
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged {
                onSizeChanged(with(density) { it.height.toDp() })
            }
            .clickable {
                onShowDetailsChanged(!showDetailsScreen)
            }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = mediaItem.title,
                style = TSTextStyles.bold17
                    .copy(TSColors.TextPrimary)
            )
            Text(
                modifier = Modifier.padding(top = 1.dp, start = 16.dp),
                text = mediaItemDescription,
                style = TSTextStyles.normal13
                    .copy(TSColors.TextSecondary)
            )
        }

        Image(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .rotate(degrees)
                .clickable {
                    onShowDetailsChanged(!showDetailsScreen)
                }
                .padding(8.dp),
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            colorFilter = ColorFilter.tint(TSColors.TextSecondary)
        )
    }
}

@Composable
private fun DynamicStickyHeader(
    showDetailsUnderStickyHeader: Boolean,
    paddingValues: PaddingValues,
    detailsScreenPaddingTop: Dp,
    programListUnderStickyState: LazyListState,
    homeUIState: HomeUiState,
    onEvent: (PlayerEvent) -> Unit,
    showTitleUnderPlayer: Boolean,
    mediaItem: MediaItem,
    mediaItemDescription: String,
    onShowProgramListChanged: (Boolean) -> Unit,
) {
    ProgramListVisibility(
        modifier = Modifier.padding(top = detailsScreenPaddingTop),
        visible = showDetailsUnderStickyHeader,
        paddingValues = paddingValues,
        detailsScreenPaddingTop = detailsScreenPaddingTop,
        programListState = programListUnderStickyState,
        homeUIState = homeUIState,
        contentPadding = remember {
            PaddingValues(
                top = paddingValues.calculateTopPadding() + detailsScreenPaddingTop,
                bottom = paddingValues.calculateBottomPadding()
            )
        },
        onEvent = onEvent
    )

    StickyChannelTitle(
        show = showTitleUnderPlayer,
        paddingValues = paddingValues,
        mediaItem = mediaItem,
        mediaItemDescription = mediaItemDescription,
        showDetailsUnderScreen = showDetailsUnderStickyHeader,
        onShowProgramListChanged = onShowProgramListChanged
    )
}

@Composable
private fun ProgramListVisibility(
    modifier: Modifier = Modifier,
    visible: Boolean,
    paddingValues: PaddingValues,
    detailsScreenPaddingTop: Dp,
    programListState: LazyListState,
    containerColor: Color = TSColors.PlayerBackgroundColor,
    homeUIState: HomeUiState,
    contentPadding: PaddingValues = remember {
        PaddingValues(
            top = paddingValues.calculateTopPadding() + detailsScreenPaddingTop,
            bottom = paddingValues.calculateBottomPadding()
        )
    },
    onEvent: (PlayerEvent) -> Unit,
) {
    AnimatedVisibility(
        modifier = Modifier,
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
        ),
        exit = slideOutVertically {
            -it
        }
    ) {
        LazyColumn(
            modifier = modifier
                .background(containerColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .fillMaxSize(),
            contentPadding = contentPadding,
            state = programListState
        ) {
            if (!homeUIState.currentProgramList.isNullOrEmpty()) {
                items(homeUIState.currentProgramList) {
                    ProgramItem(
                        program = it,
                        isCurrentProgram = homeUIState.currentProgram?.id == it.id,
                        paddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                return@LazyColumn
            }
            items(homeUIState.relatedChannels) {
                HomeChannelItem(
                    it,
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp),
                    onItemClick = { channel ->
                        onEvent(PlayerEvent.PlayIptv(channel))
                    }
                )
            }
        }
    }
}

@Composable
private fun StickyChannelTitle(
    show: Boolean,
    paddingValues: PaddingValues,
    mediaItem: MediaItem,
    mediaItemDescription: String,
    showDetailsUnderScreen: Boolean,
    onShowProgramListChanged: (Boolean) -> Unit,
) {
    val degrees by animateFloatAsState(targetValue = if (showDetailsUnderScreen) 180f else 0f)

    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    onClick = {
                        onShowProgramListChanged(!showDetailsUnderScreen)
                    }
                )
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(1f),
                            Color.Black.copy(1f),
                            Color.Black.copy(0.8f),
                            Color.Black.copy(0.5f),
                            Color.Black.copy(0.3f),
                            Color.Black.copy(0f),
                        )
                    )
                )
                .padding(vertical = 16.dp)
                .padding(start = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaItem.title,
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = mediaItemDescription,
                    color = TSColors.TextSecondary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                )
            }
            Image(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .rotate(degrees)
                    .clickable {
                        onShowProgramListChanged(!showDetailsUnderScreen)
                    }
                    .padding(8.dp),
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                colorFilter = ColorFilter.tint(TSColors.TextSecondary)
            )
        }
    }
}

@Composable
private fun RowScope.InteractionItem(
    iconPainter: Painter,
    title: String,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(TSShapes.roundedShape16)
            .clickable {
                onClick()
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Image(
            painter = iconPainter,
            contentDescription = title,
            colorFilter = ColorFilter.tint(TSColors.White.copy(0.8f))
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            modifier = Modifier,
            text = title,
            style = TSTextStyles.normal13.copy(TSColors.White.copy(0.8f))
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
