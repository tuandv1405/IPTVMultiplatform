package tss.t.tsiptv.player

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

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
     * Release all resources held by the player
     */
    suspend fun release()
}

/**
 * Represents the current state of media playback
 */
enum class PlaybackState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

/**
 * Represents a media item that can be played
 */
@Serializable
data class MediaItem(
    val id: String,
    val uri: String,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val mimeType: String? = null
)