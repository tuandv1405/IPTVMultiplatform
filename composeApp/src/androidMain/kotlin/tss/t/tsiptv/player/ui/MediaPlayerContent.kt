package tss.t.tsiptv.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.service.MediaPlayerService

/**
 * Android implementation of MediaPlayerContent.
 * Uses Media3's PlayerView to display the video content.
 */
@OptIn(UnstableApi::class)
@Composable
actual fun MediaPlayerContent(
    player: MediaPlayer,
    modifier: Modifier,
) {
    val exoPlayer by MediaPlayerService.globalPlayer
        .collectAsStateWithLifecycle(initialValue = MediaPlayerService.getExoPlayer())
    var playerView: PlayerView? by remember {
        mutableStateOf(null)
    }

    LaunchedEffect(exoPlayer, playerView) {
        if (exoPlayer != null &&
            playerView != null &&
            playerView?.player != exoPlayer
        ) {
            playerView?.player = exoPlayer
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                exoPlayer?.let { exoPlayer ->
                    this.player = exoPlayer
                }
                useController = false
                playerView = this
            }
        },
        modifier = modifier,
        update = { playerView ->
            exoPlayer?.let { exoPlayer ->
                playerView.player = exoPlayer
            }
        },
        onRelease = {
            playerView?.player = null
            playerView = null
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            playerView?.player = null
            playerView = null
        }
    }
}
