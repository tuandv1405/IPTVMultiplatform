package tss.t.tsiptv.player

import kotlinx.coroutines.CoroutineScope

/**
 * iOS implementation of MediaPlayerFactory.
 */
actual object MediaPlayerFactory {
    /**
     * Create a new MediaPlayer instance for iOS.
     *
     * @param coroutineScope The coroutine scope to use for asynchronous operations.
     * @return An iOS-specific implementation of MediaPlayer using AVPlayer.
     */
    actual fun createPlayer(coroutineScope: CoroutineScope): MediaPlayer {
        return IOSMediaPlayer(coroutineScope)
    }
}