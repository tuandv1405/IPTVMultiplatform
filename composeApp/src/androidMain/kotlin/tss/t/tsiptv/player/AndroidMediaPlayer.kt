package tss.t.tsiptv.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.player.service.MediaPlayerService

/**
 * Android implementation of the MediaPlayer interface using Media3 ExoPlayer.
 * This implementation uses a foreground service for background playback.
 */
class AndroidMediaPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : MediaPlayer {

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    override val currentMedia: StateFlow<MediaItem?> = _currentMedia.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    // ExoPlayer instance for direct control when needed
    private var exoPlayer: ExoPlayer? = null

    // Player listener to update state flows
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                _playbackState.value = PlaybackState.PLAYING
            } else if (_playbackState.value == PlaybackState.PLAYING) {
                _playbackState.value = PlaybackState.PAUSED
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            _isBuffering.value = isLoading
        }
    }

    init {
        // Start position update job
        coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition
                    }
                }
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }

    override suspend fun prepare(mediaItem: MediaItem) {
        _currentMedia.value = mediaItem

        // Start the media service
        MediaPlayerService.startService(context, mediaItem)

        // Get the ExoPlayer instance from the service
        exoPlayer = MediaPlayerService.getExoPlayer()
        exoPlayer?.let { player ->
            // Add listener
            player.addListener(playerListener)

            // Update duration when available
            _duration.value = player.duration.takeIf { it > 0 }
                ?: 0L
        }

        _playbackState.value = PlaybackState.READY
    }

    override suspend fun play() {
        MediaPlayerService.play()
        _playbackState.value = PlaybackState.PLAYING
    }

    override suspend fun pause() {
        MediaPlayerService.pause()
        _playbackState.value = PlaybackState.PAUSED
    }

    override suspend fun stop() {
        MediaPlayerService.stop()
        _playbackState.value = PlaybackState.IDLE
    }

    override suspend fun seekTo(positionMs: Long) {
        MediaPlayerService.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        MediaPlayerService.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    override suspend fun release() {
        exoPlayer?.removeListener(playerListener)
        MediaPlayerService.stopService(context)
        exoPlayer = null
        _playbackState.value = PlaybackState.IDLE
    }

    private fun updatePlaybackState(playbackState: Int) {
        _playbackState.value = when (playbackState) {
            Player.STATE_IDLE -> PlaybackState.IDLE
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> {
                if (exoPlayer?.isPlaying == true) PlaybackState.PLAYING else PlaybackState.READY
            }
            Player.STATE_ENDED -> PlaybackState.ENDED
            else -> PlaybackState.ERROR
        }
    }
}
