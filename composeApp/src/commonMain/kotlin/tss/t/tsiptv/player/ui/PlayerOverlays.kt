package tss.t.tsiptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.drm_license_failed
import tsiptv.composeapp.generated.resources.drm_not_supported_config
import tsiptv.composeapp.generated.resources.drm_not_supported_device
import tsiptv.composeapp.generated.resources.player_back
import tsiptv.composeapp.generated.resources.stream_error_forbidden_headers
import tsiptv.composeapp.generated.resources.stream_error_generic
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.ChannelLogo
import tss.t.tsiptv.ui.widgets.GrayButton

@Composable
fun playbackErrorMessage(error: PlaybackError): String = stringResource(
    when (error) {
        PlaybackError.DRM_NOT_SUPPORTED_DEVICE -> Res.string.drm_not_supported_device
        PlaybackError.DRM_NOT_SUPPORTED_CONFIG -> Res.string.drm_not_supported_config
        PlaybackError.DRM_LICENSE_FAILED -> Res.string.drm_license_failed
        PlaybackError.FORBIDDEN_HEADERS -> Res.string.stream_error_forbidden_headers
        PlaybackError.STREAM_FAILED -> Res.string.stream_error_generic
    }
)

/**
 * A radio channel has no picture: show its logo on the player background instead of a black
 * video area. Audio keeps playing underneath.
 */
@Composable
fun RadioArtwork(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    logoSize: Dp = 120.dp,
) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(listOf(TSColors.SecondaryBackgroundColor, TSColors.PlayerBackgroundColor))
        ),
        contentAlignment = Alignment.Center,
    ) {
        ChannelLogo(
            name = mediaItem.title,
            logoUrl = mediaItem.artworkUri,
            modifier = Modifier.size(logoSize),
        )
    }
}

/**
 * Why the channel does not play, over the video area.
 *
 * @param onBack Shows a Back button when set. The TV player passes null: there the remote's
 * Back leaves, and ▲ / ▼ keep zapping, so nothing on the overlay may take focus.
 */
@Composable
fun PlaybackErrorOverlay(
    error: PlaybackError,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = TSColors.White,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = playbackErrorMessage(error),
                color = TSColors.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            if (onBack != null) {
                Spacer(Modifier.height(16.dp))
                GrayButton(
                    text = stringResource(Res.string.player_back),
                    onClick = onBack,
                    modifier = Modifier.widthIn(min = 140.dp),
                )
            }
        }
    }
}
