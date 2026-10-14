package tss.t.tsiptv.feature.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import tss.t.tsiptv.utils.getUrlOpener
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Desktop: no system share sheet and no app can take a picture from us, so Facebook and Telegram
 * get their web share page (with the Play Store link) and the picture goes on the clipboard, to be
 * pasted into the post. Instagram and Zalo have no web share for this.
 */
class DesktopImageSharer : ImageSharer {

    override fun targets(): List<ShareTarget> =
        listOf(ShareTarget.FACEBOOK, ShareTarget.TELEGRAM, ShareTarget.COPY, ShareTarget.SAVE)

    @OptIn(ExperimentalTime::class)
    override suspend fun share(target: ShareTarget, image: ImageBitmap, text: String): ShareResult = withContext(Dispatchers.IO) {
        try {
            when (target) {
                ShareTarget.COPY -> {
                    copyToClipboard(image)
                    ShareResult.ImageCopied
                }

                ShareTarget.FACEBOOK, ShareTarget.TELEGRAM -> {
                    copyToClipboard(image)
                    val url = if (target == ShareTarget.FACEBOOK) ShareLinks.facebook() else ShareLinks.telegram(text)
                    if (getUrlOpener().openUrl(url)) ShareResult.OpenedImageCopied else ShareResult.ImageCopied
                }

                ShareTarget.SAVE -> {
                    val dir = File(System.getProperty("user.home"), "Pictures${File.separator}TS IPTV").apply { mkdirs() }
                    val file = File(dir, "tsiptv-${Clock.System.now().toEpochMilliseconds()}.jpg")
                    file.writeBytes(image.encodeJpeg())
                    ShareResult.Saved(file.absolutePath)
                }

                else -> ShareResult.Failed
            }
        } catch (_: Exception) {
            ShareResult.Failed
        }
    }

    private fun copyToClipboard(image: ImageBitmap) {
        // Through JPEG: no alpha channel, which some Windows apps paste as black.
        val rgb = ImageIO.read(ByteArrayInputStream(image.encodeJpeg())) ?: error("decode")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(rgb), null)
    }

    private class ImageSelection(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any =
            if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
    }
}

actual fun getImageSharer(): ImageSharer = DesktopImageSharer()

actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
        ?: error("JPEG encoding failed")
