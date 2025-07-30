package tss.t.tsiptv.player.ui

import androidx.annotation.IntRange
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_back_navigation
import tsiptv.composeapp.generated.resources.ic_enter_full_screen
import tsiptv.composeapp.generated.resources.ic_exit_full_screen
import tsiptv.composeapp.generated.resources.ic_full_screen_fill
import tsiptv.composeapp.generated.resources.ic_full_screen_fit_width
import tsiptv.composeapp.generated.resources.ic_settings
import tsiptv.composeapp.generated.resources.ic_volume
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.ui.screens.player.PlayerEvent
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.utils.PlatformUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A composable that displays a media player with controls.
 *
 * @param mediaItem The media item to play.
 * @param modifier The modifier to apply to this layout.
 */
@Composable
fun MediaPlayerView(
    mediaItem: MediaItem,
    currentProgram: IPTVProgram? = null,
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
    val volume by player.volume.collectAsState()
    val isMuted by player.isMuted.collectAsState()
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
                isMuted = isMuted,
                volume = volume,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    isMuted: Boolean = false,
    volume: Float = 1f,
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
                        text = remember(currentPosition) {
                            formatTime(currentPosition)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_volume),
                        contentDescription = "Volume",
                        modifier = Modifier.size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                onPlayerControl(PlayerEvent.ToggleMute)
                            }
                            .padding(4.dp),
                        colorFilter = ColorFilter.tint(if (isMuted) TSColors.TextSecondary else TSColors.White)
                    )

                    val colors = SliderDefaults.colors(
                        thumbColor = TSColors.White,
                        activeTrackColor = TSColors.White,
                        inactiveTrackColor = TSColors.White.copy(0.5f),
                    )
                    val interactionSource: MutableInteractionSource =
                        remember { MutableInteractionSource() }

                    Slider(
                        value = if (isMuted) 0f else volume,
                        onValueChange = {
                            onPlayerControl(PlayerEvent.SetVolume(it))
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier.width(100.dp),
                        track = {
                            SliderDefaults.Track(
                                colors = colors,
                                sliderState = it,
                                thumbTrackGapSize = 1.dp,
                                trackInsideCornerSize = 2.dp
                            )
                        },
                        thumb = {
                            SliderDefaults.Thumb(
                                colors = colors,
                                interactionSource = interactionSource,
                                thumbSize = DpSize(4.dp, 16.dp),
                            )
                        }
                    )
                }

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
                                colorFilter = ColorFilter.tint(TSColors.White)
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
                                colorFilter = ColorFilter.tint(TSColors.TextSecondary)
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
                                colorFilter = ColorFilter.tint(TSColors.White)
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
                                colorFilter = ColorFilter.tint(TSColors.TextSecondary)
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

class SliderState(
    value: Float = 0f,
    @IntRange(from = 0) val steps: Int = 0,
    var onValueChangeFinished: (() -> Unit)? = null,
    val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) : DraggableState {

    private var valueState by mutableFloatStateOf(value)

    /**
     * [Float] that indicates the current value that the thumb currently is in respect to the track.
     */
    var value: Float
        set(newVal) {
            val coercedValue = newVal.coerceIn(valueRange.start, valueRange.endInclusive)
            val snappedValue =
                snapValueToTick(
                    coercedValue,
                    tickFractions,
                    valueRange.start,
                    valueRange.endInclusive
                )
            valueState = snappedValue
        }
        get() = valueState

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit,
    ): Unit = coroutineScope {
        isDragging = true
        scrollMutex.mutateWith(dragScope, dragPriority, block)
        isDragging = false
    }

    override fun dispatchRawDelta(delta: Float) {
        val maxPx = max(totalWidth - thumbWidth / 2, 0f)
        val minPx = min(thumbWidth / 2, maxPx)
        rawOffset = (rawOffset + delta + pressOffset)
        pressOffset = 0f
        val offsetInTrack = tickFractions
            .minByOrNull { abs(lerp(minPx, maxPx, it) - rawOffset) }
            ?.run { lerp(minPx, maxPx, this) } ?: rawOffset

        val scaledUserValue = scaleToUserValue(minPx, maxPx, offsetInTrack)
        if (scaledUserValue != this.value) {
            if (onValueChange != null) {
                onValueChange?.let { it(scaledUserValue) }
            } else {
                this.value = scaledUserValue
            }
        }
    }

    /** callback in which value should be updated */
    internal var onValueChange: ((Float) -> Unit)? = null

    internal val tickFractions = stepsToTickFractions(steps)
    private var totalWidth by mutableIntStateOf(0)
    internal var isRtl = false
    internal var trackHeight by mutableFloatStateOf(0f)
    internal var thumbWidth by mutableFloatStateOf(0f)

    internal val coercedValueAsFraction
        get() =
            calcFraction(
                valueRange.start,
                valueRange.endInclusive,
                value.coerceIn(valueRange.start, valueRange.endInclusive)
            )

    internal var isDragging by mutableStateOf(false)
        private set

    internal fun updateDimensions(newTrackHeight: Float, newTotalWidth: Int) {
        trackHeight = newTrackHeight
        totalWidth = newTotalWidth
    }

    internal val gestureEndAction = {
        if (!isDragging) {
            // check isDragging in case the change is still in progress (touch -> drag case)
            this.onValueChangeFinished?.invoke()
        }
    }

    internal fun onPress(pos: Offset) {
        val to = if (isRtl) totalWidth - pos.x else pos.x
        pressOffset = to - rawOffset
    }

    private var rawOffset by mutableFloatStateOf(scaleToOffset(0f, 0f, value))
    private var pressOffset by mutableFloatStateOf(0f)
    private val dragScope: DragScope =
        object : DragScope {
            override fun dragBy(pixels: Float): Unit = dispatchRawDelta(pixels)
        }

    private val scrollMutex = MutatorMutex()

    private fun scaleToUserValue(minPx: Float, maxPx: Float, offset: Float) =
        scale(minPx, maxPx, offset, valueRange.start, valueRange.endInclusive)

    private fun scaleToOffset(minPx: Float, maxPx: Float, userValue: Float) =
        scale(valueRange.start, valueRange.endInclusive, userValue, minPx, maxPx)
}


private fun stepsToTickFractions(steps: Int): FloatArray {
    return if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }
}

// Scale x1 from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x1: Float, a2: Float, b2: Float) =
    lerp(a2, b2, calcFraction(a1, b1, x1))

// Calculate the 0..1 fraction that `pos` value represents between `a` and `b`
private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)

private fun snapValueToTick(
    current: Float,
    tickFractions: FloatArray,
    minPx: Float,
    maxPx: Float,
): Float {
    // target is a closest anchor to the `current`, if exists
    return tickFractions
        .minByOrNull { abs(lerp(minPx, maxPx, it) - current) }
        ?.run { lerp(minPx, maxPx, this) } ?: current
}
