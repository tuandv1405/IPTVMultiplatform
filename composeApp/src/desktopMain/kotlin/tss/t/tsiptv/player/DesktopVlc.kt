package tss.t.tsiptv.player

import java.io.File

/**
 * Finds libVLC for vlcj. The Windows and macOS packages bundle VLC under the app resources
 * (`prepareDesktopVlc` / `prepareDesktopVlcMac` in composeApp/build.gradle.kts, docs/desktop-build.md);
 * Linux uses the system VLC, which vlcj's own discovery already finds.
 */
object DesktopVlc {

    /**
     * Puts the bundled libVLC first on `jna.library.path`. vlcj's discovery reads that property and also
     * sets the plugin path from it (`<dir>/plugins` on Windows, `<dir>/../plugins` on macOS). Must run
     * before the first vlcj call. No-op without a bundled copy.
     */
    fun useBundledLibVlc() {
        val vlc = System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") } ?: return
        val dir = listOf(vlc to "libvlc.dll", File(vlc, "lib") to "libvlc.dylib")
            .firstOrNull { (dir, library) -> File(dir, library).isFile }
            ?.first
            ?: return
        val existing = System.getProperty("jna.library.path")
        System.setProperty(
            "jna.library.path",
            listOfNotNull(dir.absolutePath, existing).joinToString(File.pathSeparator),
        )
    }
}
