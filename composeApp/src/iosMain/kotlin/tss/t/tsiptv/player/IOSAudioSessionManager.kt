package tss.t.tsiptv.player

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSLog

/**
 * The app's audio session: the playback category, so channels are heard with the silent switch
 * on and keep playing in the background (`UIBackgroundModes` has `audio`), as on Android.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSAudioSessionManager {
    companion object {
        fun configureAudioSessionForBackgroundPlayback() {
            val session = AVAudioSession.sharedInstance()
            val categorySet = session.setCategory(
                AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeMoviePlayback,
                options = 0u,
                error = null,
            )
            val active = session.setActive(true, error = null)
            if (!categorySet || !active) {
                NSLog("Audio session not configured (category=$categorySet, active=$active)")
            }
        }

        /** Lets other apps' audio resume once the player is released. */
        fun deactivateAudioSession() {
            AVAudioSession.sharedInstance().setActive(false, error = null)
        }
    }
}
