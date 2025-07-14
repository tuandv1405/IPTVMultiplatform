package tss.t.tsiptv.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.utils.PlatformUtils

/**
 * A composable that displays a media player with controls.
 *
 * @param mediaItem The media item to play.
 * @param coroutineScope The coroutine scope to use for asynchronous operations.
 * @param modifier The modifier to apply to this layout.
 */
@Composable
fun MediaPlayerView(
    mediaItem: MediaItem,
    player: MediaPlayer = koinInject<MediaPlayer>(),
    modifier: Modifier = Modifier,
) {
    val playbackState by player.playbackState.collectAsState()
    val currentPosition by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(mediaItem) {
        player.prepare(mediaItem)
    }

    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                player.release()
            }
        }
    }
    val showCustomControls = remember {
        !PlatformUtils.platform.isIOS
    }

    Box(
        modifier.fillMaxWidth()
    ) {
        if (showCustomControls) {
            MediaPlayerControls(
                playbackState,
                currentPosition,
                duration,
                isBuffering,
            )
        }
        MediaPlayerContent(
            player = player,
            modifier = Modifier.fillMaxWidth()
                .aspectRatio(16 / 9f)
        )
    }
}

@Composable
fun BoxScope.MediaPlayerControls(
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    isBuffering: Boolean,
) {
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
