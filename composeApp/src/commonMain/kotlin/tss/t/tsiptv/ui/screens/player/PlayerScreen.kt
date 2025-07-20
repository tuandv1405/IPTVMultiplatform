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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.ui.MediaPlayerView
import tss.t.tsiptv.ui.screens.home.widget.HomeChannelItem
import tss.t.tsiptv.ui.themes.TSColors
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
    mediaPlayer: MediaPlayer,
    playerControlState: PlayerUIState,
    relatedMediaItems: List<Channel> = emptyList(),
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
            state = scrollState
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
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(top = 16.dp, start = 16.dp),
                            text = mediaItem.title,
                            color = TSColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            modifier = Modifier.padding(top = 1.dp, start = 16.dp),
                            text = mediaItem.artist,
                            color = TSColors.TextSecondary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }

                    Image(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(32.dp)
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
            }

            item("BannerAdSpace") {

            }

            item("Interaction") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                }
            }

            items(relatedMediaItems) {
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
                items(relatedMediaItems) {
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
                items(relatedMediaItems) {
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
