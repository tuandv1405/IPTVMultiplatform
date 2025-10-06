package tss.t.tsiptv.utils

/**
 * Interface for opening URLs and deeplinks across different platforms.
 */
interface UrlOpener {
    /**
     * Opens the given URL in the default browser or appropriate app.
     * @param url The URL to open
     * @return true if the URL was successfully opened, false otherwise
     */
    fun openUrl(url: String): Boolean
    
    /**
     * Checks if the given URL can be handled by an installed app.
     * @param url The URL to check
     * @return true if an app can handle the URL, false otherwise
     */
    fun canHandleUrl(url: String): Boolean
}

/**
 * Gets the platform-specific implementation of UrlOpener.
 */
expect fun getUrlOpener(): UrlOpener