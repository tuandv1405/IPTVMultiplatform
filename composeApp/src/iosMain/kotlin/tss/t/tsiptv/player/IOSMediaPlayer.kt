package tss.t.tsiptv.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.audiovisualBackgroundPlaybackPolicy
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
import platform.AVFoundation.setVolume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIImage
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackState
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

    // Cache for artwork images
    private var cachedArtworkImage: UIImage? = null
    private var lastLoadedArtworkUri: String? = null

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private var avPlayer: AVPlayer? = AVPlayer()
    override val isPlaying: StateFlow<Boolean>
        get() = _playbackState.map {
            when (it) {
                PlaybackState.PLAYING -> true
                else -> false
            }
        }.stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // AVPlayer instance

    // Time observer token for position updates
    private var timeObserverToken: Any? = null

    // Notification observer for playback ended
    private var itemObserver: Any? = null

    // Notification observers for app lifecycle events
    private var backgroundObserver: Any? = null
    private var foregroundObserver: Any? = null

    // Flag to track if app is in background
    private var isInBackground = false

    init {
        // Start position update job
        startPositionUpdateJob()

        // Request notification permissions
        requestNotificationPermissions()

        // Configure audio session for background playback
        IOSAudioSessionManager.configureAudioSessionForBackgroundPlayback()

        // Set up app lifecycle observers
        setupAppLifecycleObservers()
    }

    /**
     * Sets up observers for app lifecycle events to handle background/foreground transitions
     */
    private fun setupAppLifecycleObservers() {
        val notificationCenter = NSNotificationCenter.defaultCenter

        // Observer for when app enters background
        backgroundObserver = notificationCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification,
            null,
            null
        ) { _ ->
            println("App entered background")
            isInBackground = true
            handleAppBackgrounded()
        }

        // Observer for when app enters foreground
        foregroundObserver = notificationCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification,
            null,
            null
        ) { _ ->
            println("App will enter foreground")
            isInBackground = false
            handleAppForegrounded()
        }
    }

    /**
     * Handles actions when app goes to background
     * Ensures audio continues to play while video display is hidden
     */
    private fun handleAppBackgrounded() {
        // Continue audio playback in background
        if (_playbackState.value == PlaybackState.PLAYING) {
            // Make sure audio continues to play
            avPlayer?.play()

            // Show notification for background playback
            showPlaybackNotification()
        }
    }

    /**
     * Handles actions when app comes to foreground
     * Restores video display
     */
    private fun handleAppForegrounded() {
        // Resume normal playback state
        if (_playbackState.value == PlaybackState.PLAYING) {
            avPlayer?.play()
        }
    }

    /**
     * Requests permission to display notifications
     */
    private fun requestNotificationPermissions() {
        try {
            val notificationCenter =
                platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()

            // Request authorization for notifications
            notificationCenter.requestAuthorizationWithOptions(
                platform.UserNotifications.UNAuthorizationOptionAlert.or(
                    platform.UserNotifications.UNAuthorizationOptionBadge.or(
                        platform.UserNotifications.UNAuthorizationOptionSound
                    )
                )
            ) { granted, error ->
                if (granted) {
                    println("Notification permission granted")
                } else {
                    println("Notification permission denied: ${error?.localizedDescription}")
                }
            }
        } catch (e: Exception) {
            println("Error requesting notification permissions: ${e.message}")
        }
    }

    private fun startPositionUpdateJob() {
        coroutineScope.launch(Dispatchers.Main) {
            updateCurrentPosition()
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

        // Show notification when playing
        showPlaybackNotification()
    }

    /**
     * Shows a notification for the currently playing media
     */
    private fun showPlaybackNotification() {
        try {
            // Get current media item
            val mediaItem = _currentMedia.value ?: return

            // Format title and description for better display
            val formattedTitle = if (mediaItem.title.isNotEmpty()) {
                "Now Playing: ${mediaItem.title}"
            } else {
                "Now Playing"
            }

            // Create a more detailed description
            val description = buildString {
                if (mediaItem.artist?.isNotEmpty() == true) {
                    append(mediaItem.artist)
                }

                // Add stream type info if available
                if (mediaItem.uri.contains(".m3u8") || mediaItem.uri.contains("live")) {
                    if (isNotEmpty()) append(" • ")
                    append("Live Stream")
                } else if (mediaItem.mimeType?.isNotEmpty() == true) {
                    if (isNotEmpty()) append(" • ")
                    append(mediaItem.mimeType)
                }

                // Add a default if nothing else is available
                if (isEmpty()) {
                    append("Streaming media content")
                }
            }

            // Create notification content with improved formatting
            val content = platform.UserNotifications.UNMutableNotificationContent().apply {
                setTitle(formattedTitle)
                setBody(description)
                setSound(null) // No sound for media notifications

                // Set category for media playback
                setThreadIdentifier("media_playback")
                setCategoryIdentifier("media_playback")

                // Add user info for handling notification actions
                val userInfo = mapOf<Any?, Any?>(
                    "mediaUri" to mediaItem.uri,
                    "mediaId" to mediaItem.id,
                    "mediaTitle" to mediaItem.title,
                    "mediaArtist" to mediaItem.artist,
                    "mediaArtworkUri" to (mediaItem.artworkUri ?: "")
                )
                setUserInfo(userInfo)
            }

            // Create a unique identifier for this notification
            val requestIdentifier =
                "media_playback_${platform.Foundation.NSUUID.UUID().UUIDString()}"

            // Create the notification request
            val request = platform.UserNotifications.UNNotificationRequest.requestWithIdentifier(
                requestIdentifier,
                content,
                null // No trigger, show immediately
            )

            // Add the notification request
            platform.UserNotifications.UNUserNotificationCenter
                .currentNotificationCenter()
                .addNotificationRequest(request, null)

            println("Enhanced media playback notification shown")
        } catch (e: Exception) {
            println("Error showing notification: ${e.message}")
        }
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

        // Remove notification observers
        val notificationCenter = NSNotificationCenter.defaultCenter

        itemObserver?.let { observer ->
            notificationCenter.removeObserver(observer)
            itemObserver = null
        }

        backgroundObserver?.let { observer ->
            notificationCenter.removeObserver(observer)
            backgroundObserver = null
        }

        foregroundObserver?.let { observer ->
            notificationCenter.removeObserver(observer)
            foregroundObserver = null
        }

        // Stop and release player
        avPlayer?.pause()
        avPlayer = null

        _playbackState.value = PlaybackState.IDLE
    }

    override suspend fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        avPlayer?.setVolume(clampedVolume)
        _volume.value = clampedVolume
        _isMuted.value = clampedVolume == 0f
    }

    override suspend fun setMuted(muted: Boolean) {
        avPlayer?.let { player ->
            if (muted) {
                player.setVolume(0f)
            } else {
                player.setVolume(_volume.value.takeIf { it > 0f } ?: 1f)
            }
        }
        _isMuted.value = muted
        if (muted) {
            _volume.value = 0f
        } else if (_volume.value == 0f) {
            _volume.value = 1f
        }
    }

    /**
     * Returns the AVPlayer instance for use in the UI layer.
     * This is needed for the MediaPlayerContent composable to display the video.
     */
    fun getAVPlayer(): AVPlayer? = avPlayer
}
