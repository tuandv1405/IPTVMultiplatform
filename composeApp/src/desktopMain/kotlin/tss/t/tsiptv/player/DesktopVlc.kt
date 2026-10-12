package tss.t.tsiptv.player

import java.io.File

/**
 * Finds libVLC for vlcj. The Windows installer bundles VLC under the app resources
 * (`prepareDesktopVlc` in composeApp/build.gradle.kts, docs/desktop-build.md); macOS and Linux use the
 * system VLC, which vlcj's own discovery already finds.
 */
object DesktopVlc {

    /**
     * Puts the bundled libVLC first on `jna.library.path`. vlcj's discovery reads that property and also
     * sets the plugin path from it. Must run before the first vlcj call. No-op without a bundled copy.
     */
    fun useBundledLibVlc() {
        val dir = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?.takeIf { File(it, "libvlc.dll").isFile }
            ?: return
        val existing = System.getProperty("jna.library.path")
        System.setProperty(
            "jna.library.path",
            listOfNotNull(dir.absolutePath, existing).joinToString(File.pathSeparator),
        )
    }
}
