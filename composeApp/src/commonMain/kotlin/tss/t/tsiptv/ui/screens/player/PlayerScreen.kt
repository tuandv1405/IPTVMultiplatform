package tss.t.tsiptv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.player.ui.MediaPlayerView
import tss.t.tsiptv.ui.themes.TSColors

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

    Scaffold(
        topBar = {
            Column {
                Spacer(Modifier.statusBarsPadding())
                MediaPlayerView(
                    mediaItem = mediaItem,
                    player = mediaPlayer,
                    modifier = Modifier
                        .background(Color.Black)
                        .fillMaxWidth()
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues
        ) {
            item("ItemTitle") {
                Text(
                    modifier = Modifier.padding(top = 16.dp, start = 16.dp),
                    text = mediaItem.title,
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            item("Interaction") {
                Row(modifier = Modifier.fillMaxWidth()) {

                }
            }
        }
    }
}
