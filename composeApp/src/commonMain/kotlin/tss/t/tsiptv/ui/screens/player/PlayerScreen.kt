package tss.t.tsiptv.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.PlaybackState
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
    mediaItem: MediaItem,
    mediaPlayer: MediaPlayer,
    playbackState: PlaybackState,
    onEvent: (PlayerEvent) -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose {
            onEvent(PlayerEvent.OnPictureInPicture)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = mediaItem.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Player view
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            MediaPlayerView(
                mediaItem = mediaItem,
                player = mediaPlayer,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
