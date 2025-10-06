package tss.t.tsiptv.utils

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS implementation of UrlOpener.
 */
class IosUrlOpener : UrlOpener {
    
    override fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        return UIApplication.sharedApplication.openURL(nsUrl)
    }
    
    override fun canHandleUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        return UIApplication.sharedApplication.canOpenURL(nsUrl)
    }
}

/**
 * iOS implementation of getUrlOpener.
 */
actual fun getUrlOpener(): UrlOpener = IosUrlOpener()