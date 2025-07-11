package tss.t.tsiptv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tss.t.tsiptv.player.MediaItem
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.PlaybackState
import tss.t.tsiptv.player.ui.MediaPlayerView

/**
 * Player screen for playing IPTV channels.
 *
 * @param channelId The ID of the channel to play
 * @param channelName The name of the channel to play
 * @param channelUrl The URL of the channel to play
 * @param onBack Callback for when the user wants to go back
 */
@Composable
fun PlayerScreen(
    channelId: String,
    channelName: String,
    channelUrl: String,
    onBack: () -> Unit,
) {
    // Create a MediaPlayer instance
    val coroutineScope = rememberCoroutineScope()
    val mediaPlayer = koinInject<MediaPlayer>()

    // Create a MediaItem from the channel
    val mediaItem = remember {
        MediaItem(
            id = channelId,
            uri = channelUrl,
            title = channelName
        )
    }

    // Set up the player
    LaunchedEffect(mediaItem) {
        mediaPlayer.prepare(mediaItem)
        mediaPlayer.play()
    }

    // Clean up when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                mediaPlayer.release()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Channel info
        Text(
            text = channelName,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Player view
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f)
        ) {
            MediaPlayerView(
                mediaItem = mediaItem,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val playbackState by mediaPlayer.playbackState.collectAsState()
            val isPlaying by derivedStateOf { playbackState == PlaybackState.PLAYING }
            // Play/Pause button
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (playbackState == PlaybackState.PLAYING) {
                            mediaPlayer.pause()
                        } else {
                            mediaPlayer.play()
                        }
                    }
                }
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }

            // Stop button
            Button(
                onClick = {
                    coroutineScope.launch {
                        mediaPlayer.stop()
                    }
                }
            ) {
                Text("Stop")
            }

            // Back button
            Button(
                onClick = onBack
            ) {
                Text("Back")
            }
        }
    }
}
