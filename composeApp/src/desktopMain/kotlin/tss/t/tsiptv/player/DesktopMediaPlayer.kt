package tss.t.tsiptv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackPreflight
import tss.t.tsiptv.player.models.PlaybackState
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.Canvas
import java.awt.Component

/**
 * Desktop implementation of the MediaPlayer interface using VLCj.
 */
class DesktopMediaPlayer(
    private val coroutineScope: CoroutineScope,
) : tss.t.tsiptv.player.MediaPlayer {

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

    private val _isPlaying = MutableStateFlow(false)
    private val _volume = MutableStateFlow(1f)
    private val _isMuted = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    override val isPlaying: StateFlow<Boolean>
        get() = _isPlaying
    override val volume: StateFlow<Float>
        get() = _volume
    override val isMuted: StateFlow<Boolean>
        get() = _isMuted

    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    override val playbackError: StateFlow<PlaybackError?> = _playbackError.asStateFlow()

    // VLCj player component
    private val mediaPlayerComponent = EmbeddedMediaPlayerComponent()

    // Get the VLCj MediaPlayer instance
    private val vlcjMediaPlayer: MediaPlayer = mediaPlayerComponent.mediaPlayer()

    // Canvas for rendering video
    private val videoSurface: Canvas = mediaPlayerComponent.videoSurfaceComponent() as Canvas

    init {
        // Add event listener
        vlcjMediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.PLAYING
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.PAUSED
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                // The stop after a refusal must not turn ERROR back into IDLE.
                if (_playbackError.value == null) _playbackState.value = PlaybackState.IDLE
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.ENDED
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _playbackError.value = PlaybackError.STREAM_FAILED
                _playbackState.value = PlaybackState.ERROR
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _isBuffering.value = newCache < 100.0f
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _duration.value = newLength
            }
        })

        // Start position update job
        coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                if (_playbackState.value == PlaybackState.PLAYING) {
                    _currentPosition.value = vlcjMediaPlayer.status().time()
                }
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }

    /**
     * Get the video surface component for rendering
     */
    fun getVideoSurface(): Component = videoSurface

    override suspend fun prepare(mediaItem: MediaItem) {
        _currentMedia.value = mediaItem
        _currentPosition.value = 0L
        _duration.value = 0L

        // VLC has no Widevine / PlayReady CDM: refuse before the stream is requested.
        _playbackError.value = PlaybackPreflight.check(mediaItem, platformSupportsDrm = false)
        if (_playbackError.value != null) {
            vlcjMediaPlayer.controls().stop()
            _playbackState.value = PlaybackState.ERROR
            return
        }

        // Prepare the media
        vlcjMediaPlayer.media().play(mediaItem.uri, *vlcHeaderOptions(mediaItem.headers))
        addSubtitles(mediaItem)
        vlcjMediaPlayer.controls().pause()

        _playbackState.value = PlaybackState.READY
    }

    /**
     * F3: side-loaded subtitles (WebVTT / SubRip) as VLC slaves. The track in the system
     * language is selected; the others stay available. URLs are never logged.
     */
    private fun addSubtitles(mediaItem: MediaItem) {
        if (mediaItem.subtitles.isEmpty()) return
        // Not preselected (spec §8.7 has no default): the viewer picks one in the track menu.
        mediaItem.subtitles.forEach { track ->
            runCatching {
                vlcjMediaPlayer.media().addSlave(uk.co.caprica.vlcj.media.MediaSlaveType.SUBTITLE, track.url, false)
            }
        }
    }

    override fun textTracks(): List<tss.t.tsiptv.player.TextTrackOption> = runCatching {
        val list = vlcjMediaPlayer.tracks().textTracks()
        try {
            list.tracks().mapIndexed { i, track ->
                tss.t.tsiptv.player.TextTrackOption(
                    id = track.trackId(),
                    label = track.name()?.takeIf { it.isNotBlank() } ?: track.description()?.takeIf { it.isNotBlank() }
                        ?: track.language() ?: "${i + 1}",
                    language = track.language(),
                    selected = track.selected(),
                )
            }
        } finally {
            list.release()
        }
    }.getOrDefault(emptyList())

    override fun selectTextTrack(id: String?) {
        runCatching {
            if (id == null) vlcjMediaPlayer.tracks().deselect(uk.co.caprica.vlcj.media.TrackType.TEXT)
            else vlcjMediaPlayer.tracks().select(uk.co.caprica.vlcj.media.TrackType.TEXT, id)
        }
    }

    /**
     * VLC takes per-media options for User-Agent and Referer only (the same keys as
     * `#EXTVLCOPT`); any other header cannot be sent. Only the names are logged: values are
     * often tokens.
     */
    private fun vlcHeaderOptions(headers: Map<String, String>): Array<String> {
        val options = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        headers.forEach { (name, value) ->
            when (name.lowercase()) {
                "user-agent" -> options += ":http-user-agent=$value"
                "referer" -> options += ":http-referrer=$value"
                else -> dropped += name
            }
        }
        if (dropped.isNotEmpty()) println("VLC cannot send headers: ${dropped.joinToString()}")
        return options.toTypedArray()
    }

    // After a refusal VLC still holds the previous channel; play() would restart it.
    override suspend fun play() {
        if (_playbackError.value != null) return
        vlcjMediaPlayer.controls().play()
    }

    override suspend fun pause() {
        if (_playbackError.value != null) return
        vlcjMediaPlayer.controls().pause()
    }

    override suspend fun stop() {
        vlcjMediaPlayer.controls().stop()
    }

    override suspend fun seekTo(positionMs: Long) {
        vlcjMediaPlayer.controls().setTime(positionMs)
        _currentPosition.value = positionMs
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        vlcjMediaPlayer.controls().setRate(speed)
        _playbackSpeed.value = speed
    }

    override suspend fun setVolume(volume: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun setMuted(muted: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun release() {
        vlcjMediaPlayer.release()
        _playbackState.value = PlaybackState.IDLE
    }
}