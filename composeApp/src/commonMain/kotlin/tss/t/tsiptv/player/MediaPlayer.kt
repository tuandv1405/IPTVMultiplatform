package tss.t.tsiptv.player

import kotlinx.coroutines.flow.StateFlow
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackState

/**
 * Common interface for media player implementations across all platforms.
 * This interface defines the basic functionality that all platform-specific
 * implementations must provide.
 */
interface MediaPlayer {
    /**
     * Current playback state
     */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Current media item being played
     */
    val currentMedia: StateFlow<MediaItem?>

    /**
     * Current playback position in milliseconds
     */
    val currentPosition: StateFlow<Long>

    /**
     * Total duration of the current media in milliseconds
     */
    val duration: StateFlow<Long>

    /**
     * Current playback speed
     */
    val playbackSpeed: StateFlow<Float>

    /**
     * Whether the player is buffering
     */
    val isBuffering: StateFlow<Boolean>

    /**
     * Whether the player is playing
     */
    val isPlaying: StateFlow<Boolean>

    /**
     * Current volume level (0.0 to 1.0)
     */
    val volume: StateFlow<Float>

    /**
     * Whether the player is muted
     */
    val isMuted: StateFlow<Boolean>

    /**
     * Why the current item cannot play, when that has a specific message; null otherwise.
     * Cleared by the next [prepare].
     */
    val playbackError: StateFlow<PlaybackError?>

    /**
     * Prepare the player with the given media item
     */
    suspend fun prepare(mediaItem: MediaItem)

    /**
     * Start or resume playback
     */
    suspend fun play()

    /**
     * Pause playback
     */
    suspend fun pause()

    /**
     * Stop playback and release resources
     */
    suspend fun stop()

    /**
     * Seek to a specific position in milliseconds
     */
    suspend fun seekTo(positionMs: Long)

    /**
     * Set playback speed
     */
    suspend fun setPlaybackSpeed(speed: Float)

    /**
     * Set volume level (0.0 to 1.0)
     */
    suspend fun setVolume(volume: Float)

    /**
     * Mute or unmute the player
     */
    suspend fun setMuted(muted: Boolean)

    /**
     * Release all resources held by the player
     */
    suspend fun release()

    /**
     * F3: the text (subtitle) tracks of the current item, for the player's track menu. Empty when
     * the platform has none or cannot list them (iOS in this release).
     */
    fun textTracks(): List<TextTrackOption> = emptyList()

    /** Whether Widevine / PlayReady / ClearKey can play here (Android only). */
    val supportsDrm: Boolean get() = false

    /** F3: shows the text track [id] (from [textTracks]), or turns subtitles off when null. */
    fun selectTextTrack(id: String?) {}
}

/** One entry of the player's subtitle menu. */
data class TextTrackOption(val id: String, val label: String, val language: String?, val selected: Boolean)
