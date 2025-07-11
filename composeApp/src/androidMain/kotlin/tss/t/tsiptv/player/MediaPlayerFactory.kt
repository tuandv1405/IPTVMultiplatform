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
        val context: Context = TSAndroidApplication.instance
        return AndroidMediaPlayer(context, coroutineScope)
    }
}