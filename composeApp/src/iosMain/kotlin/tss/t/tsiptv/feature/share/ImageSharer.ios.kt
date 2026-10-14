package tss.t.tsiptv.feature.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * iOS: the system activity sheet lists Facebook, Instagram, Zalo, Telegram, Save Image and the rest
 * by itself, so it is the only target. Save Image needs NSPhotoLibraryAddUsageDescription.
 */
class IosImageSharer : ImageSharer {

    override fun targets(): List<ShareTarget> = listOf(ShareTarget.MORE)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun share(target: ShareTarget, image: ImageBitmap, text: String): ShareResult = withContext(Dispatchers.Main) {
        val bytes = image.encodeJpeg()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val picture = UIImage.imageWithData(data) ?: return@withContext ShareResult.Failed
        val top = topViewController() ?: return@withContext ShareResult.Failed
        val sheet = UIActivityViewController(activityItems = listOf(picture, text), applicationActivities = null)
        // iPad shows the sheet as a popover, which must be anchored or UIKit throws.
        sheet.popoverPresentationController?.let { popover ->
            popover.sourceView = top.view
            popover.sourceRect = top.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
        }
        top.presentViewController(sheet, animated = true, completion = null)
        ShareResult.Opened
    }

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: return top
    }
}

actual fun getImageSharer(): ImageSharer = IosImageSharer()

actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
        ?: error("JPEG encoding failed")
