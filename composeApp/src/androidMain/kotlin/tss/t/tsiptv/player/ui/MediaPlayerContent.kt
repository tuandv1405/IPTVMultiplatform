package tss.t.tsiptv.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import tss.t.tsiptv.player.AndroidMediaPlayer
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.service.MediaPlayerService

/**
 * Android implementation of MediaPlayerContent.
 * Uses Media3's PlayerView to display the video content.
 */
@Composable
actual fun MediaPlayerContent(
    player: MediaPlayer,
    modifier: Modifier
) {
    val context = LocalContext.current
    
    // Create a PlayerView to display the video
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Set player from service
                MediaPlayerService.getExoPlayer()?.let { exoPlayer ->
                    this.player = exoPlayer
                }
                
                // Set use controller to false as we're providing our own controls
                useController = false
            }
        },
        modifier = modifier,
        update = { playerView ->
            // Update the player when it changes
            MediaPlayerService.getExoPlayer()?.let { exoPlayer ->
                playerView.player = exoPlayer
            }
        }
    )
    
    // Clean up when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            // Nothing to do here as the player is managed by the service
        }
    }
}