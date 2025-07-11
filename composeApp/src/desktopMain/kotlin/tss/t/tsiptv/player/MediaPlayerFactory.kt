package tss.t.tsiptv.player

import kotlinx.coroutines.CoroutineScope

/**
 * Desktop implementation of MediaPlayerFactory.
 */
actual object MediaPlayerFactory {
    /**
     * Create a new MediaPlayer instance for desktop.
     *
     * @param coroutineScope The coroutine scope to use for asynchronous operations.
     * @return A desktop-specific implementation of MediaPlayer using VLCj.
     */
    actual fun createPlayer(coroutineScope: CoroutineScope): MediaPlayer {
        return DesktopMediaPlayer(coroutineScope)
    }
    
    /**
     * Get the desktop player with access to the video surface component.
     * This is useful for integrating the video surface into a Compose UI.
     *
     * @param coroutineScope The coroutine scope to use for asynchronous operations.
     * @return The DesktopMediaPlayer instance.
     */
    fun createDesktopPlayer(coroutineScope: CoroutineScope): DesktopMediaPlayer {
        return DesktopMediaPlayer(coroutineScope)
    }
}