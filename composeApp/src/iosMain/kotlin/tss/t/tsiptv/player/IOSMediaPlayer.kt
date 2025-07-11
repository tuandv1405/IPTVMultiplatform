package tss.t.tsiptv.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.playbackBufferEmpty
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import kotlin.math.roundToLong

/**
 * iOS implementation of the MediaPlayer interface using AVPlayer.
 */
class IOSMediaPlayer(
    private val coroutineScope: CoroutineScope,
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

    // AVPlayer instance
    private var avPlayer: AVPlayer? = AVPlayer()

    // Time observer token for position updates
    private var timeObserverToken: Any? = null

    // Notification observer for playback ended
    private var itemObserver: Any? = null

    init {
        // Start position update job
        startPositionUpdateJob()
    }

    private fun startPositionUpdateJob() {
        coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                updateCurrentPosition()
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun updateCurrentPosition() {
        avPlayer?.let { player ->
            val time = player.currentTime()
            val seconds = CMTimeGetSeconds(time)
            if (!seconds.isNaN()) {
                _currentPosition.value = (seconds * 1000).roundToLong()
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun prepare(mediaItem: MediaItem) {
        _currentMedia.value = mediaItem
        _isBuffering.value = true

        // Create URL from the media item URI
        val url = NSURL.URLWithString(mediaItem.uri)

        // Create AVPlayerItem
        val playerItem = url?.let { AVPlayerItem.playerItemWithURL(it) }
        // Create or reuse AVPlayer
        if (avPlayer == null && playerItem != null) {
            avPlayer = AVPlayer.playerWithPlayerItem(playerItem)
        } else if (playerItem != null) {
            avPlayer?.replaceCurrentItemWithPlayerItem(playerItem)
        }

        // Add observers
        setupObservers()

        // Update duration when available
        updateDuration()

        _playbackState.value = PlaybackState.READY
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun setupObservers() {
        avPlayer?.let { player ->
            // Add time observer for position updates
            val interval = CMTimeMake(1, 2) // 0.5 seconds
            timeObserverToken = player.addPeriodicTimeObserverForInterval(interval, null) { _ ->
                updateCurrentPosition()

                // Also check player status and update state
                player.currentItem?.let { item ->
                    // Update duration
                    updateDuration()


                    // Check if playback is ready
                    if (item.status == AVPlayerItemStatusReadyToPlay) { // AVPlayerItemStatusReadyToPlay
                        _isBuffering.value = false
                        if (_playbackState.value == PlaybackState.PLAYING) {
                            player.play()
                        }
                    } else if (item.status == AVPlayerItemStatusFailed) { //
                        _playbackState.value = PlaybackState.ERROR
                    }

                    // Check buffering state
                    if (item.playbackBufferEmpty) {
                        _isBuffering.value = true
                    }
                }
            }

            // Playback ended observer
            player.currentItem?.let { item ->
                val notificationCenter = NSNotificationCenter.defaultCenter
                itemObserver = notificationCenter.addObserverForName(
                    AVPlayerItemDidPlayToEndTimeNotification,
                    item,
                    null
                ) { _ ->
                    _playbackState.value = PlaybackState.ENDED
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun updateDuration() {
        avPlayer?.currentItem?.let { item ->
            val duration = item.duration
            val seconds = CMTimeGetSeconds(duration)
            if (!seconds.isNaN() && seconds > 0) {
                _duration.value = (seconds * 1000).roundToLong()
            }
        }
    }

    override suspend fun play() {
        avPlayer?.play()
        _playbackState.value = PlaybackState.PLAYING
    }

    override suspend fun pause() {
        avPlayer?.pause()
        _playbackState.value = PlaybackState.PAUSED
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun stop() {
        avPlayer?.pause()
        avPlayer?.seekToTime(CMTimeMake(0, 1))
        _currentPosition.value = 0
        _playbackState.value = PlaybackState.IDLE
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000.0
        val time = CMTimeMake((seconds * 1000).toLong(), 1000)
        avPlayer?.seekToTime(time)
        _currentPosition.value = positionMs
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        avPlayer?.rate = speed
        _playbackSpeed.value = speed
    }

    override suspend fun release() {
        // Remove observers
        timeObserverToken?.let { token ->
            avPlayer?.removeTimeObserver(token)
            timeObserverToken = null
        }

        itemObserver?.let { observer ->
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            itemObserver = null
        }

        // Stop and release player
        avPlayer?.pause()
        avPlayer = null

        _playbackState.value = PlaybackState.IDLE
    }

    /**
     * Returns the AVPlayer instance for use in the UI layer.
     * This is needed for the MediaPlayerContent composable to display the video.
     */
    fun getAVPlayer(): AVPlayer? = avPlayer
}
