package tss.t.tsiptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackPreflight
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.player.service.MediaPlayerService

/**
 * Android implementation of the MediaPlayer interface using Media3 ExoPlayer.
 * This implementation uses a foreground service for background playback.
 */
@OptIn(UnstableApi::class)
class AndroidMediaPlayer(
    private val context: Context,
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

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    override val playbackError: StateFlow<PlaybackError?> = _playbackError.asStateFlow()

    // ExoPlayer instance for direct control when needed
    private var exoPlayer: ExoPlayer? = null

    /**
     * True from prepare() until the service has actually swapped in the new source (timeline or
     * item transition) or failed. Meanwhile the player still reports the previous source, which may
     * even carry the same media id (replaying the same title): position/duration are not taken
     * from it, so a resume seek cannot land on the old source.
     */
    @Volatile
    private var pendingSource = false
    /** The player's item object at prepare(); a different object means the new source is in. */
    private var itemAtPrepare: androidx.media3.common.MediaItem? = null
    private var observerPlayer: Job? = null

    init {
        observerPlayer = coroutineScope.launch(Dispatchers.Main) {
            MediaPlayerService.globalPlayer.collect {
                exoPlayer?.removeListener(playerListener)
                exoPlayer = it
                it?.addListener(playerListener)
            }
        }
        coroutineScope.launch(Dispatchers.Main) {
            MediaPlayerService.sourceFailures.collect { id ->
                if (id == _currentMedia.value?.id) {
                    pendingSource = false
                    _playbackError.value = PlaybackError.STREAM_FAILED
                    _playbackState.value = PlaybackState.ERROR
                }
            }
        }
    }

    // Player listener to update state flows
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                _playbackState.value = PlaybackState.PLAYING
            } else if (_playbackState.value == PlaybackState.PLAYING) {
                _playbackState.value = PlaybackState.PAUSED
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            _isBuffering.value = isLoading
        }

        override fun onPlayerError(error: PlaybackException) {
            pendingSource = false
            _playbackError.value = mapError(error)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) pendingSource = false
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            pendingSource = false
        }
    }

    private fun mapError(error: PlaybackException): PlaybackError = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> PlaybackError.DRM_NOT_SUPPORTED_DEVICE

        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> PlaybackError.DRM_LICENSE_FAILED

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            val hasOwnHeaders = _currentMedia.value?.headers?.isNotEmpty() == true
            if (hasOwnHeaders && (status == 401 || status == 403)) PlaybackError.FORBIDDEN_HEADERS
            else PlaybackError.STREAM_FAILED
        }

        else -> PlaybackError.STREAM_FAILED
    }

    init {
        // Start position update job
        coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                // Fallback for a missed listener event: a new MediaItem object means the swap happened.
                exoPlayer?.let { player ->
                    if (pendingSource && player.currentMediaItem != null && player.currentMediaItem !== itemAtPrepare) {
                        pendingSource = false
                    }
                }
                exoPlayer?.takeIf { !pendingSource }?.let { player ->
                    // Only the item this player was asked for: right after prepare() the service has
                    // not swapped the source yet, and the previous item's position and duration
                    // (e.g. a VOD before a live channel) must not leak into the new one.
                    val current = _currentMedia.value?.id
                    val loaded = player.currentMediaItem?.mediaId
                    if (current != null && loaded == current) {
                        if (player.isPlaying) {
                            _currentPosition.value = player.currentPosition
                        }
                        // prepare() runs before the duration is known. Live streams stay 0 (LIVE UI),
                        // even with a DVR window.
                        val d = player.duration
                        val knownDuration = if (player.isCurrentMediaItemLive || d <= 0) 0L else d
                        if (knownDuration != _duration.value) _duration.value = knownDuration
                    }
                }
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }

    /** ExoPlayer must only be touched on the main thread; callers may be on IO. */
    private suspend inline fun <T> onMain(crossinline block: () -> T): T = withContext(Dispatchers.Main.immediate) { block() }

    override suspend fun prepare(mediaItem: MediaItem) = onMain { prepareOnMain(mediaItem) }

    private fun prepareOnMain(mediaItem: MediaItem) {
        _currentMedia.value = mediaItem
        // A subtitle choice belongs to the previous item.
        exoPlayer?.let { player ->
            val text = androidx.media3.common.C.TRACK_TYPE_TEXT
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(text).setTrackTypeDisabled(text, false).build()
        }
        _playbackError.value = PlaybackPreflight.check(mediaItem, platformSupportsDrm = true)
        _currentPosition.value = 0L
        _duration.value = 0L
        if (_playbackError.value != null) {
            // Stop and unload the previous channel rather than leave it under the message, and make
            // any start intent still in flight (fast zapping) stale.
            pendingSource = false
            MediaPlayerService.invalidatePendingStarts()
            MediaPlayerService.stop()
            MediaPlayerService.getExoPlayer()?.clearMediaItems()
            _playbackState.value = PlaybackState.ERROR
            return
        }
        pendingSource = true
        itemAtPrepare = MediaPlayerService.getExoPlayer()?.currentMediaItem
        var started = false
        try {
            MediaPlayerService.startService(context, mediaItem)
            started = true
        } finally {
            // startService can throw (e.g. background start restrictions): never leave the flag set.
            if (!started) pendingSource = false
        }
        exoPlayer = MediaPlayerService.getExoPlayer()
        exoPlayer?.let { player ->
            player.playWhenReady = true
            player.addListener(playerListener)
        }

        _playbackState.value = PlaybackState.READY
    }

    override suspend fun play() = onMain {
        // A refused channel has nothing loaded; playing would resume the previous one.
        if (_playbackError.value != null) return@onMain
        MediaPlayerService.play()
        _playbackState.value = PlaybackState.PLAYING
    }

    override suspend fun pause() = onMain {
        if (_playbackError.value != null) return@onMain
        MediaPlayerService.pause()
        _playbackState.value = PlaybackState.PAUSED
    }

    override suspend fun stop() = onMain {
        // A start intent still in flight must not restart playback after the stop.
        MediaPlayerService.invalidatePendingStarts()
        pendingSource = false
        MediaPlayerService.stop()
        _playbackState.value = PlaybackState.IDLE
    }

    override suspend fun seekTo(positionMs: Long) = onMain {
        MediaPlayerService.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    override suspend fun setPlaybackSpeed(speed: Float) = onMain {
        MediaPlayerService.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    override val supportsDrm: Boolean get() = true

    /** Main thread only (the menu calls it from the UI). */
    override fun textTracks(): List<TextTrackOption> {
        val player = exoPlayer ?: return emptyList()
        val out = ArrayList<TextTrackOption>()
        player.currentTracks.groups.forEachIndexed { g, group ->
            if (group.type != androidx.media3.common.C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (t in 0 until group.length) {
                if (!group.isTrackSupported(t)) continue
                val format = group.getTrackFormat(t)
                out += TextTrackOption(
                    id = "$g:$t",
                    label = format.label ?: format.language ?: "${out.size + 1}",
                    language = format.language,
                    selected = group.isTrackSelected(t),
                )
            }
        }
        return out
    }

    override fun selectTextTrack(id: String?) {
        val player = exoPlayer ?: return
        val type = androidx.media3.common.C.TRACK_TYPE_TEXT
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (id == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            val (g, t) = id.split(':').map { it.toInt() }
            val group = player.currentTracks.groups.getOrNull(g) ?: return
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, t))
        }
        player.trackSelectionParameters = builder.build()
    }

    override suspend fun release() = onMain {
        exoPlayer?.removeListener(playerListener)
        MediaPlayerService.stopService(context)
        exoPlayer = null
        _playbackState.value = PlaybackState.IDLE
        observerPlayer = null
    }

    override suspend fun setVolume(volume: Float) = onMain {
        val clampedVolume = volume.coerceIn(0f, 1f)
        MediaPlayerService.setVolume(clampedVolume)
        _volume.value = clampedVolume
        _isMuted.value = clampedVolume == 0f
    }

    override suspend fun setMuted(muted: Boolean) = onMain {
        MediaPlayerService.setMuted(muted)
        _isMuted.value = muted
        _volume.value = if (muted) 0f else 1f
    }

    private fun updatePlaybackState(playbackState: Int) {
        // The stop that follows a failure or a refusal must not turn ERROR back into IDLE.
        if (_playbackError.value != null && playbackState == Player.STATE_IDLE) return
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
