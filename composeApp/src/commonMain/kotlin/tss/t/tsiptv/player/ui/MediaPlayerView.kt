package tss.t.tsiptv.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_back_navigation
import tsiptv.composeapp.generated.resources.ic_enter_full_screen
import tsiptv.composeapp.generated.resources.ic_exit_full_screen
import tsiptv.composeapp.generated.resources.ic_settings
import tsiptv.composeapp.generated.resources.ic_volume
import tsiptv.composeapp.generated.resources.ic_full_screen_fit_width
import tsiptv.composeapp.generated.resources.ic_full_screen_fill
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.ui.screens.player.PlayerEvent
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.utils.PlatformUtils

/**
 * A composable that displays a media player with controls.
 *
 * @param mediaItem The media item to play.
 * @param modifier The modifier to apply to this layout.
 */
@Composable
fun MediaPlayerView(
    mediaItem: MediaItem,
    player: MediaPlayer = koinInject<MediaPlayer>(),
    modifier: Modifier = Modifier,
    playerUIState: PlayerUIState,
    onPlayerControl: (PlayerEvent) -> Unit = {},
) {
    val playbackState by player.playbackState.collectAsState()
    val currentPosition by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val showCustomControls = remember { !PlatformUtils.platform.isIOS }
    var autoShowControls by remember { mutableStateOf(true) }
    var resetAutoHideTimer by remember { mutableIntStateOf(0) }

    LaunchedEffect(mediaItem.id) {
        autoShowControls = true
    }

    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                onPlayerControl(PlayerEvent.OnPictureInPicture)
            }
        }
    }

    LaunchedEffect(
        key1 = autoShowControls,
        key2 = resetAutoHideTimer
    ) {
        if (autoShowControls) {
            delay(7000)
            autoShowControls = false
        }
    }
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val screenHeight = LocalWindowInfo.current.containerSize.height

    Box(
        modifier.fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures {
                    autoShowControls = !autoShowControls
                }

                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                    },
                    onDragEnd = {
                    },
                    onDragCancel = {
                    },
                    onDragStart = {

                    }
                )
            }
    ) {
        if (playerUIState.isFullScreen) {
            MediaPlayerContent(
                player = player,
                modifier = when {
                    playerUIState.isFillScreen169 -> {
                        Modifier
                            .background(TSColors.PlayerBackgroundColor)
                            .fillMaxSize()
                            .align(Alignment.Center)
                    }

                    playerUIState.isFitWidth -> {
                        Modifier
                            .background(TSColors.PlayerBackgroundColor)
                            .fillMaxWidth()
                            .aspectRatio(16 / 9f)
                    }

                    else -> {
                        Modifier
                            .background(TSColors.PlayerBackgroundColor)
                            .fillMaxWidth()
                    }
                }
            )
        } else {
            MediaPlayerContent(
                player = player,
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(16 / 9f)
            )
        }
        if (showCustomControls) {
            MediaPlayerControls(
                playbackState = playbackState,
                currentPosition = currentPosition,
                duration = duration,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                isFullScreen = playerUIState.isFullScreen,
                isFitWidth = playerUIState.isFitWidth,
                isFillScreen169 = playerUIState.isFillScreen169,
                showControls = if (!isPlaying) {
                    true
                } else {
                    autoShowControls
                },
                onPlayerControl = {
                    resetAutoHideTimer++
                    onPlayerControl(it)
                }
            )
        }
    }
}

@Composable
fun BoxScope.MediaPlayerControls(
    playbackState: PlaybackState,
    showControls: Boolean = true,
    currentPosition: Long,
    duration: Long,
    isBuffering: Boolean,
    isFullScreen: Boolean,
    isFitWidth: Boolean = false,
    isFillScreen169: Boolean = false,
    isPlaying: Boolean = playbackState == PlaybackState.PLAYING,
    onPlayerControl: (PlayerEvent) -> Unit = {},
) {
    val progress: Float = remember(currentPosition, duration) {
        if (duration > 0) {
            (currentPosition.toDouble() / duration).toFloat()
        } else 1f
    }

    val isLive = remember(duration) { duration == 0L }

    AnimatedVisibility(
        visible = showControls,
        modifier = Modifier.align(Alignment.TopCenter)
            .fillMaxWidth(),
        enter = fadeIn(
            animationSpec = tween(durationMillis = 500)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 500)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0f),
                        )
                    )
                )
                .padding(horizontal = 8.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_back_navigation),
                contentDescription = "Back",
                modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (isFullScreen) {
                            onPlayerControl(PlayerEvent.OnExitFullScreen)
                        } else {
                            onPlayerControl(PlayerEvent.OnVerticalPlayerBack)
                        }
                    }
                    .padding(16.dp)
            )
        }
    }

    AnimatedVisibility(
        visible = showControls,
        modifier = Modifier.align(Alignment.Center)
            .padding(bottom = 20.dp)
            .size(64.dp)
            .clip(CircleShape),
        enter = fadeIn(
            animationSpec = tween(durationMillis = 500)
        ) + scaleIn(
            animationSpec = tween(durationMillis = 500),
            initialScale = 0.7f
        ),
        exit = fadeOut()
    ) {
        AnimatedContent(
            targetState = isPlaying,
        ) {
            if (it) {
                Box(
                    modifier = Modifier.size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable {
                            onPlayerControl(PlayerEvent.Pause)
                        }
                ) {
                    Image(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "pause",
                        modifier = Modifier.size(32.dp)
                            .align(Alignment.Center),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable {
                            onPlayerControl(PlayerEvent.Play)
                        }
                ) {
                    Image(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(32.dp)
                            .align(Alignment.Center),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showControls,
        modifier = Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth(),
        enter = fadeIn(
            animationSpec = tween(durationMillis = 500)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PlayerProgress(
                progress = progress,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                progressHeigh = 4.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLive) {
                    Text(
                        "LIVE",
                        modifier = Modifier
                            .clip(TSShapes.roundedShape4)
                            .background(Color(0xFFDC2626), TSShapes.roundedShape4)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                } else {
                    Text(
                        text = formatTime(currentPosition),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Image(
                    painter = painterResource(Res.drawable.ic_volume),
                    contentDescription = "Back",
                    modifier = Modifier.size(28.dp)
                        .clip(CircleShape)
                        .clickable {}
                        .padding(4.dp)
                )
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = "Settings",
                    modifier = Modifier.size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            onPlayerControl(PlayerEvent.OnSettings)
                        }
                        .padding(8.dp)
                )
                Spacer(Modifier.width(4.dp))

                if (remember(isFullScreen) { isFullScreen }) {
                    AnimatedContent(isFillScreen169) {
                        if (it) {
                            Image(
                                painter = painterResource(Res.drawable.ic_full_screen_fill),
                                contentDescription = "FullScreen",
                                modifier = Modifier.size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onPlayerControl(PlayerEvent.OnPlayerViewExitFillScreen169)
                                    }
                                    .padding(8.dp),
                                colorFilter = ColorFilter.tint(TSColors.TextSecondary)
                            )
                        } else {
                            Image(
                                painter = painterResource(Res.drawable.ic_full_screen_fill),
                                contentDescription = "FullScreen",
                                modifier = Modifier.size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onPlayerControl(PlayerEvent.OnPlayerViewFillScreen169)
                                    }
                                    .padding(8.dp),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }

                    AnimatedContent(isFitWidth) {
                        if (it) {
                            Image(
                                painter = painterResource(Res.drawable.ic_full_screen_fit_width),
                                contentDescription = "FullScreen",
                                modifier = Modifier.size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onPlayerControl(PlayerEvent.OnPlayerViewExitFitWidth)
                                    }
                                    .padding(8.dp),
                                colorFilter = ColorFilter.tint(TSColors.TextSecondary)
                            )
                        } else {
                            Image(
                                painter = painterResource(Res.drawable.ic_full_screen_fit_width),
                                contentDescription = "FullScreen",
                                modifier = Modifier.size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onPlayerControl(PlayerEvent.OnPlayerViewFitWidth)
                                    }
                                    .padding(8.dp),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }
                }

                AnimatedContent(isFullScreen) {
                    if (isFullScreen) {
                        Image(
                            painter = painterResource(Res.drawable.ic_exit_full_screen),
                            contentDescription = "FullScreen",
                            modifier = Modifier.size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onPlayerControl(PlayerEvent.OnExitFullScreen)
                                }
                                .padding(8.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                    } else {
                        Image(
                            painter = painterResource(Res.drawable.ic_enter_full_screen),
                            contentDescription = "FullScreen",
                            modifier = Modifier.size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onPlayerControl(PlayerEvent.OnEnterFullScreen)
                                }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Format time in milliseconds to a string in the format "mm:ss".
 */
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/**
 * Platform-specific implementation of the media player content.
 * This will be implemented differently on each platform.
 */
@Composable
expect fun MediaPlayerContent(
    player: MediaPlayer,
    modifier: Modifier = Modifier,
)
