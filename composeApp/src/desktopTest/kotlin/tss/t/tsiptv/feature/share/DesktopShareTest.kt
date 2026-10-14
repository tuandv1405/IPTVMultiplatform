package tss.t.tsiptv.feature.share

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopShareTest {

    @Test
    fun cardEncodesToAJpegOtherAppsCanRead() {
        val image = ImageBitmap(64, 36)
        Canvas(image).drawRect(Rect(0f, 0f, 64f, 36f), Paint().apply { color = Color(0xFF2050A0) })

        val bytes = image.encodeJpeg()
        assertEquals(listOf(0xFF, 0xD8), bytes.take(2).map { it.toInt() and 0xFF })
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(64, decoded.width)
        assertEquals(36, decoded.height)
        assertFalse(FrameCheck.isBlank(image))
    }

    @Test
    fun anUndrawnPictureIsBlank() {
        val image = ImageBitmap(32, 18)
        Canvas(image).drawRect(Rect(0f, 0f, 32f, 18f), Paint().apply { color = Color.Black })
        assertTrue(FrameCheck.isBlank(image))
    }

    @Test
    fun desktopOffersWebShareClipboardAndSave() {
        assertEquals(
            listOf(ShareTarget.FACEBOOK, ShareTarget.TELEGRAM, ShareTarget.COPY, ShareTarget.SAVE),
            getImageSharer().targets(),
        )
    }
}
