package tss.t.tsiptv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tss.t.tsiptv.core.player.MediaItem
import tss.t.tsiptv.core.player.SimpleIMediaPlayer
import tss.t.tsiptv.core.player.createMediaPlayer

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
    onBack: () -> Unit
) {
    // Create a MediaPlayer instance
    val mediaPlayer = remember { createMediaPlayer() }

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
        mediaPlayer.setMediaItem(mediaItem)
        mediaPlayer.prepare()
        mediaPlayer.play()
    }

    // Clean up when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
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
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            mediaPlayer.PlayerView(
                modifier = Modifier.fillMaxSize()
            )
        }

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPlaying by mediaPlayer.isPlaying.collectAsState()

            // Play/Pause button
            Button(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer.pause()
                    } else {
                        mediaPlayer.play()
                    }
                }
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }

            // Stop button
            Button(
                onClick = {
                    mediaPlayer.stop()
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
