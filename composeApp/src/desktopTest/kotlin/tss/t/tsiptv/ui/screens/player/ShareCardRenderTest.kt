package tss.t.tsiptv.ui.screens.player

import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.feature.share.FrameCheck
import tss.t.tsiptv.feature.share.encodeJpeg
import tss.t.tsiptv.player.VideoSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The picture sent is the card recorded into the graphics layer: at a 1x desktop density it must
 * still come out [SHARE_CARD_WIDTH_PX] wide, with the video picture's aspect.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ShareCardRenderTest {

    private fun render(snapshot: VideoSnapshot?, title: String): ImageBitmap {
        var layer: GraphicsLayer? = null
        val scene = ImageComposeScene(width = 520, height = 600, density = Density(1f)) {
            val recorded = rememberGraphicsLayer()
            layer = recorded
            ShareCard(
                snapshot = snapshot,
                title = title,
                logoUrl = null,
                modifier = Modifier.width(480.dp).recordInto(recorded, SHARE_CARD_WIDTH_PX),
            )
        }
        try {
            repeat(3) { scene.render() }
            return runBlocking { layer!!.toImageBitmap() }
        } finally {
            scene.close()
        }
    }

    private fun picture(width: Int, height: Int): ImageBitmap = ImageBitmap(width, height).also { image ->
        val canvas = Canvas(image)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { color = Color(0xFF1E3A8A) })
        canvas.drawCircle(Offset(width / 2f, height / 2f), height / 3f, Paint().apply { color = Color(0xFFF59E0B) })
    }

    @Test
    fun cardWithVideoPictureIsSentAtFullWidth() {
        val card = render(VideoSnapshot(picture(640, 360), 16f / 9f), "VTV1 HD")
        assertEquals(SHARE_CARD_WIDTH_PX, card.width)
        // 16:9 picture plus the footer.
        assertTrue(card.height > SHARE_CARD_WIDTH_PX * 9 / 16)
        assertFalse(FrameCheck.isBlank(card))
        save(card, "share-card-frame.jpg")
    }

    @Test
    fun cardWithoutPictureShowsTheAppIconAndTitle() {
        val card = render(null, "A very long channel name that does not fit on one line of the card at all")
        assertEquals(SHARE_CARD_WIDTH_PX, card.width)
        assertFalse(FrameCheck.isBlank(card))
        save(card, "share-card-logo.jpg")
    }

    private fun save(card: ImageBitmap, name: String) {
        val dir = System.getenv("SHARE_CARD_OUT") ?: return
        File(dir, name).writeBytes(card.encodeJpeg())
    }
}
