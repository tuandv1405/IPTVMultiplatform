package tss.t.tsiptv.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tss.t.tsiptv.player.DesktopMediaPlayer
import tss.t.tsiptv.player.MediaPlayer
import kotlin.math.roundToInt

/**
 * Desktop implementation of MediaPlayerContent: draws the frames libVLC decodes
 * ([DesktopMediaPlayer.videoFrame]) fitted inside the bounds, on black. Being plain Compose
 * drawing, the player controls stay on top and receive clicks.
 */
@Composable
actual fun MediaPlayerContent(
    player: MediaPlayer,
    modifier: Modifier
) {
    val desktopPlayer = player as? DesktopMediaPlayer
    if (desktopPlayer == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }
    val frame = desktopPlayer.videoFrame.collectAsState()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Read here, in the draw phase: a new frame redraws without recomposing.
        val current = frame.value ?: return@Canvas
        val image = current.image
        if (image.width == 0 || image.height == 0 || size.width <= 0f || size.height <= 0f) return@Canvas
        val aspect = current.aspect.takeIf { it > 0f } ?: (image.width.toFloat() / image.height)
        var width = size.width
        var height = width / aspect
        if (height > size.height) {
            height = size.height
            width = height * aspect
        }
        drawImage(
            image = image,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}
