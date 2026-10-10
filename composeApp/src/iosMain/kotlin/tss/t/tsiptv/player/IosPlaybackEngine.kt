package tss.t.tsiptv.player

import platform.UIKit.UIView
import tss.t.tsiptv.player.models.PlaybackCapabilities

/**
 * A second video engine for iOS, written in Swift (`iosApp/iosApp/VLCPlaybackEngine.swift`, on
 * VLCKit) and registered at app start through [IosPlaybackEngines]. [IOSMediaPlayer] routes clear
 * MPEG-DASH to it (AVPlayer plays HLS only) and keeps AVPlayer for everything else.
 *
 * Exported to Swift as a protocol. Method names avoid Objective-C/NSObject names (`release`,
 * `description`, setters), which Kotlin/Native would rename. All calls happen on the main thread,
 * and the engine reports back on the main thread too.
 */
interface IosPlaybackEngine {
    /** The render surface. [IOSMediaPlayer.engineView] hands it to the Compose video surface. */
    val view: UIView

    /** Receives state, progress and errors; null detaches. */
    fun attachListener(listener: IosPlaybackEngineListener?)

    /**
     * Opens [url] (paused until [play]). [headers] are what the stream needs on its requests; the
     * engine sends those it can (VLC: User-Agent and Referer) and drops the rest. Header values
     * may be tokens: never log them. [startPositionMs] > 0 starts there (resume).
     */
    fun load(url: String, headers: Map<String, String>, startPositionMs: Long)

    fun play()
    fun pause()

    /** Seeks when the stream is seekable; an engine still opening applies it once it plays. */
    fun seekTo(positionMs: Long)

    /** Stops and unloads the current stream; the engine stays usable. */
    fun stop()

    /** Frees everything; the engine is not used again. */
    fun dispose()

    /** 0.0 … 1.0. */
    fun changeVolume(volume: Float)

    fun changeRate(rate: Float)

    /** Lock screen / Control Center "Now Playing" (title, artist, artwork link). */
    fun updateNowPlaying(title: String, artist: String?, artworkUrl: String?)
}

/** What an [IosPlaybackEngine] reports. Called on the main thread. */
interface IosPlaybackEngineListener {
    /** One of the [IosEngineState] constants. */
    fun onStateChanged(state: Int)

    /** [durationMs] ≤ 0 means live or unknown (the app treats it as live). */
    fun onProgress(positionMs: Long, durationMs: Long)

    /** One of the [IosEngineError] constants; [message] is for debug logs and must hold no URL. */
    fun onError(code: Int, message: String?)
}

/** Engine states (plain Ints: simplest to use from Swift). */
object IosEngineState {
    const val IDLE = 0
    const val BUFFERING = 1
    const val READY = 2
    const val PLAYING = 3
    const val PAUSED = 4
    const val ENDED = 5
}

/** Engine error codes, mapped to [tss.t.tsiptv.player.models.PlaybackError] by [IOSMediaPlayer]. */
object IosEngineError {
    /** The stream could not be opened or played (network, 404, unreadable). */
    const val STREAM_FAILED = 1

    /** The server answered 401/403 (if the engine can tell). */
    const val FORBIDDEN = 2

    /** The stream needs DRM the engine cannot play. */
    const val DRM_UNSUPPORTED = 3
}

/** Creates engines; Swift implements it (a Kotlin function type would be clumsier to export). */
interface IosPlaybackEngineFactory {
    fun create(): IosPlaybackEngine
}

/**
 * Engines the iOS app registers at start (`iOSApp.init()`):
 * `IosPlaybackEngines.shared.dashFactory = VLCPlaybackEngineFactory()`.
 */
object IosPlaybackEngines {
    /** Plays clear MPEG-DASH. Null: DASH is left to AVPlayer, which cannot play it. */
    var dashFactory: IosPlaybackEngineFactory? = null
        set(value) {
            field = value
            PlaybackCapabilities.iosDashEngineAvailable = value != null
        }
}
