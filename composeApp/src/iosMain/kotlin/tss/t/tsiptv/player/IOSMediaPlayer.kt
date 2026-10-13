package tss.t.tsiptv.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerAudiovisualBackgroundPlaybackPolicyContinuesIfPossible
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemErrorLogEvent
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.audiovisualBackgroundPlaybackPolicy
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.error
import platform.AVFoundation.errorLog
import platform.AVFoundation.timeControlStatus
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
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIImage
import platform.AVFoundation.AVURLAsset
import platform.UIKit.UIView
import tss.t.tsiptv.player.models.DashRouting
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackPreflight
import tss.t.tsiptv.player.models.PlaybackState
import kotlin.math.roundToLong

/**
 * iOS implementation of the MediaPlayer interface using AVPlayer.
 *
 * Clear MPEG-DASH (MIME hint or `.mpd`, no DRM) goes to the registered DASH engine instead
 * ([IosPlaybackEngines.dashFactory], VLCKit in the app), because AVPlayer plays HLS only. Only one
 * engine holds a stream at a time: switching releases the other one's item.
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

    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    override val playbackError: StateFlow<PlaybackError?> = _playbackError.asStateFlow()

    /** The DASH engine, created on first use and kept for the next DASH item. */
    private var engine: IosPlaybackEngine? = null

    /** True while the current item plays on [engine] (not AVPlayer). */
    private var engineActive = false

    private val _engineView = MutableStateFlow<UIView?>(null)

    /** The engine's render surface while it plays the current item; null when AVPlayer does. */
    val engineView: StateFlow<UIView?> = _engineView.asStateFlow()

    private val engineListener = object : IosPlaybackEngineListener {
        override fun onStateChanged(state: Int) {
            if (!engineActive) return
            when (state) {
                IosEngineState.BUFFERING -> _isBuffering.value = true
                IosEngineState.READY -> _isBuffering.value = false
                IosEngineState.PLAYING -> {
                    _isBuffering.value = false
                    if (_playbackError.value == null) _playbackState.value = PlaybackState.PLAYING
                }
                IosEngineState.PAUSED -> {
                    _isBuffering.value = false
                    if (_playbackError.value == null && _playbackState.value == PlaybackState.PLAYING) {
                        _playbackState.value = PlaybackState.PAUSED
                    }
                }
                IosEngineState.ENDED -> {
                    _isBuffering.value = false
                    _playbackState.value = PlaybackState.ENDED
                }
                IosEngineState.IDLE -> _isBuffering.value = false
            }
        }

        override fun onProgress(positionMs: Long, durationMs: Long) {
            if (!engineActive) return
            _currentPosition.value = positionMs.coerceAtLeast(0L)
            // Live (or unknown length) stays 0, as with AVPlayer: the UI shows LIVE.
            val duration = if (durationMs > 0) durationMs else 0L
            if (_duration.value != duration) _duration.value = duration
        }

        override fun onError(code: Int, message: String?) {
            if (!engineActive) return
            _isBuffering.value = false
            val hasOwnHeaders = _currentMedia.value?.headers?.isNotEmpty() == true
            _playbackError.value = when (code) {
                IosEngineError.FORBIDDEN -> if (hasOwnHeaders) PlaybackError.FORBIDDEN_HEADERS else PlaybackError.STREAM_FAILED
                IosEngineError.DRM_UNSUPPORTED -> PlaybackError.DRM_NOT_SUPPORTED_DEVICE
                else -> PlaybackError.STREAM_FAILED
            }
            _playbackState.value = PlaybackState.ERROR
            // The engine never puts the URL in the message.
            message?.let { println("DASH engine error $code: $it") }
        }
    }

    private var avPlayer: AVPlayer? = AVPlayer()

    // One shared flow (a getter built a new stateIn on every read).
    override val isPlaying: StateFlow<Boolean> = _playbackState
        .map { it == PlaybackState.PLAYING }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    /** Watches the AVPlayer item's status, so a stream that fails before it starts shows the error. */
    private var itemStatusJob: Job? = null

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

        // Configure audio session for background playback
        IOSAudioSessionManager.configureAudioSessionForBackgroundPlayback()

        // Set initial volume on the AVPlayer
        avPlayer?.let { player ->
            // Audio keeps playing in the background while the video surface stays attached.
            player.audiovisualBackgroundPlaybackPolicy =
                AVPlayerAudiovisualBackgroundPlaybackPolicyContinuesIfPossible
            player.setVolume(_volume.value)
            println("Initial AVPlayer volume set to: ${_volume.value}")
        }

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
            // Make sure audio continues to play (the DASH engine keeps playing on its own).
            if (!engineActive) avPlayer?.play()
        }
    }

    /**
     * Handles actions when app comes to foreground
     * Restores video display
     */
    private fun handleAppForegrounded() {
        // Resume normal playback state
//        if (_playbackState.value == PlaybackState.PLAYING) {
//            avPlayer?.play()
//        }
    }

    private fun startPositionUpdateJob() {
        coroutineScope.launch(Dispatchers.Main) {
            updateCurrentPosition()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun updateCurrentPosition() {
        if (engineActive) return // the engine reports its own position
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
        if (backgroundObserver == null) {
            IOSAudioSessionManager.configureAudioSessionForBackgroundPlayback()
            setupAppLifecycleObservers()
        }
        _currentMedia.value = mediaItem
        _currentPosition.value = 0L
        _duration.value = 0L

        // AVPlayer offers FairPlay only: refuse DRM channels before the stream is requested,
        // and stop the previous channel so it does not keep playing under the message.
        _playbackError.value = PlaybackPreflight.check(mediaItem, platformSupportsDrm = false)
        if (_playbackError.value != null) {
            itemStatusJob?.cancel()
            avPlayer?.pause()
            avPlayer?.replaceCurrentItemWithPlayerItem(null)
            deactivateEngine()
            _isBuffering.value = false
            _playbackState.value = PlaybackState.ERROR
            return
        }
        _isBuffering.value = true

        // Clear MPEG-DASH: the registered engine (AVPlayer cannot play DASH).
        val dashFactory = IosPlaybackEngines.dashFactory
        if (dashFactory != null &&
            DashRouting.shouldUseDashEngine(mediaItem.uri, mediaItem.mimeType, mediaItem.drm, engineAvailable = true)
        ) {
            prepareOnEngine(mediaItem, dashFactory)
            return
        }
        // AVPlayer item: the engine must not keep playing underneath.
        deactivateEngine()

        // Create URL from the media item URI
        val url = NSURL.URLWithString(mediaItem.uri)

        // Headers through AVURLAsset's undocumented options key: widely used, best effort.
        val playerItem = url?.let {
            if (mediaItem.headers.isEmpty()) {
                AVPlayerItem.playerItemWithURL(it)
            } else {
                val asset = AVURLAsset.URLAssetWithURL(
                    it,
                    mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to mediaItem.headers)
                )
                AVPlayerItem.playerItemWithAsset(asset)
            }
        }
        // Create or reuse AVPlayer
        if (avPlayer == null && playerItem != null) {
            avPlayer = AVPlayer.playerWithPlayerItem(playerItem)
        } else if (playerItem != null) {
            avPlayer?.replaceCurrentItemWithPlayerItem(playerItem)
        }

        // Ensure the player has the correct volume settings
        avPlayer?.let { player ->
            // Set volume based on current state
            val currentVolume = if (_isMuted.value) 0f else _volume.value
            player.setVolume(currentVolume)
            println("AVPlayer volume set to: $currentVolume (muted: ${_isMuted.value})")
        }

        // Add observers
        setupObservers()
        watchItemStatus()

        // Update duration when available
        updateDuration()

        _playbackState.value = PlaybackState.READY
    }

    /** Stops AVPlayer's item and plays [mediaItem] on the DASH engine. */
    private fun prepareOnEngine(mediaItem: MediaItem, factory: IosPlaybackEngineFactory) {
        removeAvObservers()
        itemStatusJob?.cancel()
        avPlayer?.pause()
        avPlayer?.replaceCurrentItemWithPlayerItem(null)

        val dash = engine ?: factory.create().also { engine = it }
        engineActive = true
        dash.attachListener(engineListener)
        dash.changeVolume(if (_isMuted.value) 0f else _volume.value)
        dash.changeRate(_playbackSpeed.value)
        dash.load(mediaItem.uri, mediaItem.headers, 0L)
        dash.updateNowPlaying(mediaItem.title, mediaItem.artist.takeIf { it.isNotBlank() }, mediaItem.artworkUri)
        _engineView.value = dash.view
        _playbackState.value = PlaybackState.READY
    }

    /** Back to AVPlayer: the engine stops its stream (it is kept for the next DASH item). */
    private fun deactivateEngine() {
        if (!engineActive) return
        engineActive = false
        engine?.attachListener(null)
        engine?.stop()
        _engineView.value = null
    }

    /** The periodic time observer and the end observer of the previous AVPlayer item. */
    private fun removeAvObservers() {
        timeObserverToken?.let { token ->
            avPlayer?.removeTimeObserver(token)
            timeObserverToken = null
        }
        itemObserver?.let { observer ->
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            itemObserver = null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun setupObservers() {
        // One periodic observer at a time (each prepare used to add another).
        removeAvObservers()
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
                    } else if (item.status == AVPlayerItemStatusFailed) {
                        reportItemFailed(item)
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

    /**
     * The periodic observer only ticks once time moves, so a stream that fails or stalls before
     * it starts would spin forever: poll the item until it is ready or failed, then keep the
     * buffering flag in step with it.
     */
    private fun watchItemStatus() {
        itemStatusJob?.cancel()
        val item = avPlayer?.currentItem ?: return
        itemStatusJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive && avPlayer?.currentItem == item && !engineActive) {
                when (item.status) {
                    AVPlayerItemStatusFailed -> {
                        reportItemFailed(item)
                        return@launch
                    }
                    AVPlayerItemStatusReadyToPlay -> {
                        updateDuration()
                        // The rate stays 1 while AVPlayer waits for data; this status does not.
                        val waiting = avPlayer?.timeControlStatus ==
                            AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
                        _isBuffering.value = waiting && _playbackState.value == PlaybackState.PLAYING
                    }
                }
                delay(STATUS_POLL_MS)
            }
        }
    }

    /** Same mapping as Android: 401/403 on a channel with its own headers means they expired. */
    private fun reportItemFailed(item: AVPlayerItem) {
        if (_playbackState.value == PlaybackState.ERROR) return
        val status = (item.errorLog()?.events?.lastOrNull() as? AVPlayerItemErrorLogEvent)
            ?.errorStatusCode
        val hasOwnHeaders = _currentMedia.value?.headers?.isNotEmpty() == true
        _isBuffering.value = false
        _playbackError.value = if ((status == 401L || status == 403L) && hasOwnHeaders) {
            PlaybackError.FORBIDDEN_HEADERS
        } else {
            PlaybackError.STREAM_FAILED
        }
        _playbackState.value = PlaybackState.ERROR
        println("AVPlayer item failed (HTTP status ${status ?: "none"}): ${item.error?.localizedDescription}")
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
        // A refused channel has no item loaded; nothing to play.
        if (_playbackError.value != null) return
        if (engineActive) {
            engine?.changeVolume(if (_isMuted.value) 0f else _volume.value)
            engine?.play()
            _playbackState.value = PlaybackState.PLAYING
            return
        }
        avPlayer?.let { player ->
            // Ensure volume is set correctly before playing
            val currentVolume = if (_isMuted.value) 0f else _volume.value
            player.setVolume(currentVolume)
            println("Play: AVPlayer volume set to: $currentVolume (muted: ${_isMuted.value})")

            player.play()
        }
        _playbackState.value = PlaybackState.PLAYING
    }

    override suspend fun pause() {
        if (engineActive) engine?.pause() else avPlayer?.pause()
        if (_playbackError.value == null) _playbackState.value = PlaybackState.PAUSED
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun stop() {
        if (engineActive) {
            // As with AVPlayer: paused at the start, the item stays loaded (play() resumes it).
            engine?.pause()
            engine?.seekTo(0L)
        } else {
            avPlayer?.pause()
            avPlayer?.seekToTime(CMTimeMake(0, 1))
        }
        _currentPosition.value = 0
        _playbackState.value = PlaybackState.IDLE
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun seekTo(positionMs: Long) {
        if (engineActive) {
            engine?.seekTo(positionMs)
            _currentPosition.value = positionMs
            return
        }
        val seconds = positionMs / 1000.0
        val time = CMTimeMake((seconds * 1000).toLong(), 1000)
        avPlayer?.seekToTime(time)
        _currentPosition.value = positionMs
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        if (engineActive) engine?.changeRate(speed) else avPlayer?.rate = speed
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
        itemStatusJob?.cancel()

        // Deactivate audio session
        IOSAudioSessionManager.deactivateAudioSession()

        // Stop the player. The instance stays: the video surface holds it, and a later
        // prepare() reuses it.
        avPlayer?.pause()
        avPlayer?.replaceCurrentItemWithPlayerItem(null)

        // And the DASH engine.
        deactivateEngine()
        engine?.dispose()
        engine = null

        _playbackState.value = PlaybackState.IDLE
    }

    override suspend fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        avPlayer?.setVolume(clampedVolume)
        engine?.changeVolume(clampedVolume)
        _volume.value = clampedVolume
        _isMuted.value = clampedVolume == 0f
    }

    override suspend fun setMuted(muted: Boolean) {
        engine?.changeVolume(if (muted) 0f else (_volume.value.takeIf { it > 0f } ?: 1f))
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

    private companion object {
        const val STATUS_POLL_MS = 250L
    }
}
