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
}