package tss.t.tsiptv.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.all_channels_title
import tsiptv.composeapp.generated.resources.tv_player_hint
import tsiptv.composeapp.generated.resources.tv_player_hint_options
import tsiptv.composeapp.generated.resources.tv_player_hint_options_vod
import tsiptv.composeapp.generated.resources.tv_player_hint_vod
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.ui.MediaPlayerContent
import tss.t.tsiptv.player.ui.PlaybackErrorOverlay
import tss.t.tsiptv.player.ui.RadioArtwork
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.player.PlayerEvent
import tss.t.tsiptv.ui.screens.player.isAddonItemId
import androidx.compose.material3.LinearProgressIndicator
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.utils.KeepScreenOnState
import tss.t.tsiptv.utils.getScreenOrientationUtils

private const val INFO_VISIBLE_MS = 4_000L
private const val SEEK_STEP_MS = 10_000L

/** F2: position / duration bar under the banner for addon VOD items. */
@Composable
private fun VodProgressBar(position: Long, duration: Long) {
    fun time(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val mmss = "${((s % 3600) / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
        return if (s >= 3600) "${s / 3600}:$mmss" else mmss
    }
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = TvDefaults.overscanHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time(position), color = TSColors.TextPrimary, fontSize = 16.sp)
        LinearProgressIndicator(
            progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp).height(6.dp),
            color = TSColors.AccentCyan,
            trackColor = Color.White.copy(alpha = 0.25f),
        )
        Text(time(duration), color = TSColors.TextPrimary, fontSize = 16.sp)
    }
}

/**
 * Full-screen player for the TV layout. There are no on-screen controls to reach
 * with a D-pad; the remote's keys act directly:
 *
 * - Up / Down, Channel+ / Channel-: previous / next channel in the current list
 * - OK / Enter: open the channel list on the left
 * - Play/Pause media keys: toggle playback
 * - Left / Right / Info: show the channel banner
 * - Back: close the channel list, otherwise leave the player
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvPlayerScreen(
    mediaItem: MediaItem,
    homeUIState: HomeUiState,
    mediaPlayer: MediaPlayer,
    onEvent: (PlayerEvent) -> Unit,
    /** F3: the options dialog (streams, subtitles) is open; focus returns here when it closes. */
    optionsVisible: Boolean = false,
    /** F3: the channel playing has several streams to choose from. */
    hasStreamChoice: Boolean = false,
) {
    // F3 (QC r2 #2): options only when there is something to choose; otherwise the keys keep
    // showing the banner, as before.
    val hasTextTracks by produceState(false, mediaItem.id, mediaPlayer) {
        while (true) {
            value = mediaPlayer.textTracks().isNotEmpty()
            delay(1_000)
        }
    }
    val hasOptions = hasStreamChoice || hasTextTracks
    val latestHasOptions by rememberUpdatedState(hasOptions)
    val isPlaying by mediaPlayer.isPlaying.collectAsState()
    val isBuffering by mediaPlayer.isBuffering.collectAsState()
    val playbackError by mediaPlayer.playbackError.collectAsState()
    // F2: addon movies/episodes/tv items are not channels: no zapping or channel list;
    // ◀ / ▶ seek ±10 s (VOD), OK toggles play/pause, and a progress bar replaces the banner.
    val isAddonItem = isAddonItemId(mediaItem.id)
    val position by mediaPlayer.currentPosition.collectAsState()
    val duration by mediaPlayer.duration.collectAsState()
    val channels = if (isAddonItem) emptyList() else (homeUIState.zapChannels ?: homeUIState.listChannels)
    val currentIndex = remember(channels, mediaItem.id) {
        channels.indexOfFirst { it.id == mediaItem.id }
    }
    val rootFocus = remember { FocusRequester() }

    var showChannelList by remember { mutableStateOf(false) }
    // Bumped on every key press that should (re)show the banner; restarts the hide timer.
    var infoRequest by remember { mutableIntStateOf(1) }
    var showInfo by remember { mutableStateOf(true) }

    val latestChannels by rememberUpdatedState(channels)
    val latestIndex by rememberUpdatedState(currentIndex)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    fun play(channel: Channel) {
        onEvent(PlayerEvent.PlayIptv(channel))
        infoRequest++
    }

    fun zap(step: Int) {
        val list = latestChannels
        if (list.isEmpty()) return
        // A channel resumed from history may not be in the filtered list; start at its edge.
        val from = if (latestIndex < 0) (if (step > 0) -1 else 0) else latestIndex
        play(list[(from + step).mod(list.size)])
    }

    KeepScreenOnState(rememberUpdatedState(isPlaying))

    DisposableEffect(Unit) {
        getScreenOrientationUtils().hideSystemUI()
        onDispose { getScreenOrientationUtils().showSystemUI() }
    }

    LaunchedEffect(infoRequest) {
        showInfo = true
        delay(INFO_VISIBLE_MS)
        showInfo = false
    }

    // Back closes the channel list first; with it closed, the NavHost pops the
    // player. A key handler cannot do this: with predictive back (targetSdk 36)
    // Back goes to the back dispatcher, not to the view's key events.
    BackHandler(enabled = showChannelList) { showChannelList = false }

    LaunchedEffect(showChannelList, optionsVisible) {
        if (!showChannelList && !optionsVisible) rootFocus.requestFocusAfterLayout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TSColors.PlayerBackgroundColor)
    ) {
        MediaPlayerContent(
            player = mediaPlayer,
            modifier = Modifier.fillMaxSize()
        )
        if (mediaItem.isRadio) {
            RadioArtwork(mediaItem = mediaItem, modifier = Modifier.fillMaxSize(), logoSize = 220.dp)
        }
        // Text only, never focusable: ▲ / ▼ must keep zapping away from a channel that
        // cannot play, and Back still leaves.
        playbackError?.let { PlaybackErrorOverlay(error = it, modifier = Modifier.fillMaxSize()) }

        // Key target while the channel list is closed. Sits above the video
        // (an AndroidView, which would otherwise take focus) but draws nothing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .onPreviewKeyEvent { event ->
                    if (showChannelList || event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    // F3: streams and subtitles (MENU / captions keys everywhere; ▶ on channels, ▲ on VOD).
                    if (latestHasOptions && (event.key == Key.Menu || event.key == Key.Captions || event.key == Key.Settings)) {
                        onEvent(PlayerEvent.OnSettings)
                        return@onPreviewKeyEvent true
                    }
                    if (isAddonItem) {
                        when (event.key) {
                            Key.DirectionLeft, Key.MediaRewind -> { if (duration > 0) onEvent(PlayerEvent.SeekBy(-SEEK_STEP_MS)); infoRequest++ }
                            Key.DirectionRight, Key.MediaFastForward -> { if (duration > 0) onEvent(PlayerEvent.SeekBy(SEEK_STEP_MS)); infoRequest++ }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                                onEvent(if (latestIsPlaying) PlayerEvent.Pause else PlayerEvent.Play)
                                infoRequest++
                            }
                            Key.DirectionUp -> if (latestHasOptions) onEvent(PlayerEvent.OnSettings) else infoRequest++
                            Key.DirectionDown, Key.Info, Key.Guide -> infoRequest++
                            Key.MediaPlay -> onEvent(PlayerEvent.Play)
                            Key.MediaPause, Key.MediaStop -> onEvent(PlayerEvent.Pause)
                            else -> return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp, Key.PageUp -> zap(-1)
                        Key.DirectionDown, Key.ChannelDown, Key.PageDown -> zap(+1)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> showChannelList = true
                        Key.DirectionRight -> if (latestHasOptions) onEvent(PlayerEvent.OnSettings) else infoRequest++
                        Key.DirectionLeft, Key.Info, Key.Guide -> infoRequest++

                        Key.MediaPlayPause, Key.Spacebar -> {
                            onEvent(if (latestIsPlaying) PlayerEvent.Pause else PlayerEvent.Play)
                            infoRequest++
                        }

                        Key.MediaPlay -> onEvent(PlayerEvent.Play)
                        Key.MediaPause, Key.MediaStop -> onEvent(PlayerEvent.Pause)
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .focusable()
        )

        if (playbackError != null) {
            // The overlay explains; a spinner or pause icon would contradict it.
        } else if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(56.dp),
                color = TSColors.AccentCyan
            )
        } else if (!isPlaying) {
            Icon(
                imageVector = Icons.Rounded.Pause,
                contentDescription = null,
                tint = TSColors.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .clip(TvDefaults.cardShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp)
            )
        }

        AnimatedVisibility(
            visible = showInfo && !showChannelList,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                ChannelBanner(
                    mediaItem = mediaItem,
                    channelNumber = channels.getOrNull(currentIndex)?.number,
                    programTitle = if (isAddonItem) mediaItem.artist.ifBlank { null } else homeUIState.currentProgram?.title,
                    position = if (currentIndex >= 0) "${currentIndex + 1}/${channels.size}" else null,
                    optionsHint = when {
                        !hasOptions -> null
                        isAddonItem -> Res.string.tv_player_hint_options_vod
                        else -> Res.string.tv_player_hint_options
                    },
                    hintRes = when {
                        !isAddonItem -> Res.string.tv_player_hint
                        duration > 0 -> Res.string.tv_player_hint_vod
                        else -> null
                    },
                )
                if (isAddonItem && duration > 0) {
                    VodProgressBar(position = position, duration = duration)
                }
            }
        }

        AnimatedVisibility(
            visible = showChannelList,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChannelListPanel(
                title = homeUIState.selectedCategory?.name
                    ?: stringResource(Res.string.all_channels_title),
                channels = channels,
                currentIndex = currentIndex,
                onPick = { channel ->
                    showChannelList = false
                    if (channel.id != mediaItem.id) play(channel)
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        rootFocus.requestFocusAfterLayout()
    }
}

@Composable
private fun ChannelBanner(
    mediaItem: MediaItem,
    channelNumber: Int?,
    programTitle: String?,
    position: String?,
    /** Key hint: channels zap; addon VOD seeks; addon live items have no hint (no zapping, no seeking). */
    hintRes: org.jetbrains.compose.resources.StringResource? = Res.string.tv_player_hint,
    optionsHint: org.jetbrains.compose.resources.StringResource? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(
                horizontal = TvDefaults.overscanHorizontal,
                vertical = TvDefaults.overscanVertical
            )
            .padding(top = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 72.dp)
                .clip(TvDefaults.cardShape)
                .background(TSColors.SecondaryBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            ChannelLogo(
                name = mediaItem.title,
                logoUrl = mediaItem.artworkUri,
                modifier = Modifier.fillMaxSize().padding(10.dp)
            )
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (position != null) {
                    Text(
                        text = position,
                        color = TSColors.AccentCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = channelNumber?.let { "$it  ${mediaItem.title}" } ?: mediaItem.title,
                    color = TSColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!programTitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = programTitle,
                    color = TSColors.TextSecondaryLight,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hintRes != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(hintRes) + (optionsHint?.let { " · " + stringResource(it) } ?: ""),
                    color = TSColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ChannelListPanel(
    title: String,
    channels: List<Channel>,
    currentIndex: Int,
    onPick: (Channel) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0)
    )
    val currentFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    listOf(TSColors.DeepBlue.copy(alpha = 0.97f), TSColors.DeepBlue.copy(alpha = 0.85f))
                )
            )
            .padding(
                start = TvDefaults.overscanHorizontal / 2,
                end = 12.dp,
                top = TvDefaults.overscanVertical,
                bottom = TvDefaults.overscanVertical
            )
    ) {
        Text(
            text = title,
            color = TSColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(channels, key = { _, it -> it.id }) { index, channel ->
                val isCurrent = index == currentIndex
                TvFocusableSurface(
                    onClick = { onPick(channel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCurrent || (currentIndex < 0 && index == 0)) {
                                Modifier.focusRequester(currentFocus)
                            } else {
                                Modifier
                            }
                        ),
                    focusedScale = 1.02f,
                    color = Color.Transparent,
                    selected = isCurrent,
                ) { focused ->
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = TSColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.width(44.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 40.dp)
                                .clip(TvDefaults.cardShape)
                                .background(TSColors.SecondaryBackgroundColor),
                            contentAlignment = Alignment.Center
                        ) {
                            ChannelLogo(
                                name = channel.name,
                                logoUrl = channel.logoUrl,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = channel.name,
                            color = if (focused || isCurrent) TSColors.TextPrimary else TSColors.TextSecondaryLight,
                            fontSize = 17.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // The current row has to be composed before it can take focus.
        if (currentIndex >= 0) listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
        currentFocus.requestFocusAfterLayout()
    }
}
