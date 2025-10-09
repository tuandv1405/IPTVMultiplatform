package tss.t.tsiptv.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS implementation of UrlOpener.
 */
class IosUrlOpener : UrlOpener {

    override suspend fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val result = suspendCancellableCoroutine { cont ->
            UIApplication.sharedApplication()
                .openURL(
                    url = nsUrl,
                    options = mapOf<Any?, Any>(),
                    completionHandler = {
                        cont.resumeWith(Result.success(it))
                    }
                )
        }
        return result
    }

    override suspend fun canHandleUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false.also {
            println("Cannot handle url")
        }
        println("Handle url: $nsUrl")
        println("canOpenURL: ${UIApplication.sharedApplication.canOpenURL(nsUrl)}")
        return UIApplication.sharedApplication.canOpenURL(nsUrl)
    }
}

/**
 * iOS implementation of getUrlOpener.
 */
actual fun getUrlOpener(): UrlOpener = IosUrlOpener()