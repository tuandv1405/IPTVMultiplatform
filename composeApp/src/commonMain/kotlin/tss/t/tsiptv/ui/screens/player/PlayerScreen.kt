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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_dislike
import tsiptv.composeapp.generated.resources.ic_like
import tsiptv.composeapp.generated.resources.ic_report
import tsiptv.composeapp.generated.resources.ic_share
import tsiptv.composeapp.generated.resources.now
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
                val degrees by animateFloatAsState(targetValue = if (showDetailsScreen) 180f else 0f)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .onSizeChanged {
                            detailsScreenPaddingTop = with(density) { it.height.toDp() }
                        }
                        .clickable {
                            showDetailsScreen = !showDetailsScreen
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
                            text = homeUIState.currentProgram?.title ?: mediaItem.artist,
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
                                showDetailsScreen = !showDetailsScreen
                            }
                            .padding(8.dp),
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(TSColors.TextSecondary)
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    TSColors.GradientGreen.copy(0.08f),
                                    TSColors.GradientBlue.copy(0.1f),
                                    TSColors.GradientGreen.copy(0.08f),
                                )
                            )
                        ),
                )
            }

            item("BannerAdSpace") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TSShapes.roundedShape16)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TSShapes.roundedShape16)
                        .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {

                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_like),
                            contentDescription = stringResource(Res.string.player_like),
                            colorFilter = ColorFilter.tint(TSColors.White.copy(0.8f))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            modifier = Modifier,
                            text = stringResource(Res.string.player_like),
                            style = TSTextStyles.normal13.copy(TSColors.White.copy(0.8f))
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_dislike),
                            contentDescription = stringResource(Res.string.player_dislike),
                            colorFilter = ColorFilter.tint(TSColors.White.copy(0.8f))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            modifier = Modifier,
                            text = stringResource(Res.string.player_dislike),
                            style = TSTextStyles.normal13.copy(TSColors.White.copy(0.8f))
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_share),
                            contentDescription = stringResource(Res.string.player_share),
                            colorFilter = ColorFilter.tint(TSColors.White.copy(0.8f))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            modifier = Modifier,
                            text = stringResource(Res.string.player_share),
                            style = TSTextStyles.normal13.copy(TSColors.White.copy(0.8f))
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_report),
                            contentDescription = stringResource(Res.string.player_report),
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(TSColors.White.copy(0.8f))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            modifier = Modifier,
                            text = stringResource(Res.string.player_report),
                            style = TSTextStyles.normal13.copy(TSColors.White.copy(0.8f))
                        )
                    }
                }
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

        var showDetailsUnderScreen by remember {
            mutableStateOf(false)
        }

        AnimatedVisibility(
            visible = showDetailsScreen,
            enter = slideInVertically(
                initialOffsetY = { -it },
            ),
            exit = slideOutVertically {
                -it
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(
                        top = paddingValues.calculateTopPadding() + detailsScreenPaddingTop,
                    )
                    .background(TSColors.BackgroundColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .fillMaxSize(),
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
                        channel = it,
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

        AnimatedVisibility(
            visible = showDetailsUnderScreen,
            enter = slideInVertically(
                initialOffsetY = { -it },
            ),
            exit = slideOutVertically {
                -it
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .background(TSColors.PlayerBackgroundColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + detailsScreenPaddingTop)
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

        AnimatedVisibility(
            visible = showTitleUnderPlayer,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
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
                        text = mediaItem.artist,
                        color = TSColors.TextSecondary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
                val degrees by animateFloatAsState(targetValue = if (showDetailsUnderScreen) 180f else 0f)
                Image(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .rotate(degrees)
                        .clickable {
                            showDetailsUnderScreen = !showDetailsUnderScreen
                        }
                        .padding(8.dp),
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(TSColors.TextSecondary)
                )
            }
        }
    }
}
