package tss.t.tsiptv.core.share

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.popoverPresentationController
import platform.UIKit.UIWindow

class IosShareManager : ShareManager {
    @OptIn(ExperimentalForeignApi::class)
    override fun shareTextAndImage(title: String, text: String, imageBytes: ByteArray?) {
        val items = mutableListOf<Any>(text)
        
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            val data = imageBytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), imageBytes.size.toULong())
            }
            val image = UIImage.imageWithData(data)
            if (image != null) {
                items.add(image)
            }
        }
        
        val activityViewController = UIActivityViewController(activityItems = items, applicationActivities = null)
        
        val window = UIApplication.sharedApplication.windows.firstOrNull { it is UIWindow } as? UIWindow
        var rootViewController = window?.rootViewController
        while (rootViewController?.presentedViewController != null) {
            rootViewController = rootViewController.presentedViewController
        }
        
        activityViewController.popoverPresentationController?.apply {
            sourceView = rootViewController?.view
        }
        rootViewController?.presentViewController(activityViewController, animated = true, completion = null)
    }
}
