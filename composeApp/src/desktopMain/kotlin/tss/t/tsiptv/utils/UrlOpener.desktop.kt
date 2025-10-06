package tss.t.tsiptv.utils

import java.awt.Desktop
import java.net.URI

/**
 * Desktop implementation of UrlOpener.
 */
class DesktopUrlOpener : UrlOpener {
    
    override fun openUrl(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI(url))
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
    
    override fun canHandleUrl(url: String): Boolean {
        return try {
            URI(url) // Just check if URL is valid
            Desktop.isDesktopSupported() && 
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Desktop implementation of getUrlOpener.
 */
actual fun getUrlOpener(): UrlOpener = DesktopUrlOpener()