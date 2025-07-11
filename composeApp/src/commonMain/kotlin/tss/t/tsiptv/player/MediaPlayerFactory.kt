package tss.t.tsiptv.player

import kotlinx.coroutines.CoroutineScope

/**
 * Factory for creating platform-specific MediaPlayer implementations.
 */
expect object MediaPlayerFactory {

    /**
     * Create a new MediaPlayer instance for the current platform.
     *
     * @param coroutineScope The coroutine scope to use for asynchronous operations.
     * @return A platform-specific implementation of MediaPlayer.
     */
    fun createPlayer(coroutineScope: CoroutineScope): MediaPlayer
}