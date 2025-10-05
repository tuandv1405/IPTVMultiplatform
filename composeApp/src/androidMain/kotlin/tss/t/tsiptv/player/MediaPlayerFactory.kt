package tss.t.tsiptv.player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import tss.t.tsiptv.TSAndroidApplication

/**
 * Android implementation of MediaPlayerFactory.
 */
actual object MediaPlayerFactory {
    /**
     * Create a new MediaPlayer instance for Android.
     *
     * @param coroutineScope The coroutine scope to use for asynchronous operations.
     * @return An Android-specific implementation of MediaPlayer using Media3.
     */
    actual fun createPlayer(coroutineScope: CoroutineScope): MediaPlayer {
        throw IllegalStateException("Use createPlayer(context, coroutineScope) instead")
    }
    
    /**
     * Create a new MediaPlayer instance for Android with provided context.
     *
     * @param context The Android context.
     * @param coroutineScope The coroutine scope to use for asynchronous operations.
     * @return An Android-specific implementation of MediaPlayer using Media3.
     */
    fun createPlayer(context: Context, coroutineScope: CoroutineScope): MediaPlayer {
        return AndroidMediaPlayer(context, coroutineScope)
    }
}