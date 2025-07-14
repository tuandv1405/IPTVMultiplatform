package tss.t.tsiptv.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.PlaybackState

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video view is platform-specific and will be implemented in expect/actual
        MediaPlayerContent(
            player = player,
            modifier = Modifier.fillMaxWidth()
                .aspectRatio(16 / 9f)
        )

        // Media info
        Text(
            text = mediaItem.title,
            style = MaterialTheme.typography.titleLarge
        )

        if (mediaItem.artist.isNotEmpty()) {
            Text(
                text = mediaItem.artist,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Progress bar
        if (duration > 0) {
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = {
                    coroutineScope.launch {
                        player.seekTo(it.toLong())
                    }
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // Time display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition))
                Text(formatTime(duration))
            }
        }

        // Playback controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        player.stop()
                    }
                }
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }

            IconButton(
                onClick = {
                    coroutineScope.launch {
                        if (playbackState == PlaybackState.PLAYING) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    }
                }
            ) {
                if (playbackState == PlaybackState.PLAYING) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
        }

        // Buffering indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 8.dp)
            )
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
