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
import uk.co.caprica.vlcj.factory.DialogHandlerAdapter
import uk.co.caprica.vlcj.factory.DialogId
import uk.co.caprica.vlcj.factory.DialogQuestionType
import uk.co.caprica.vlcj.factory.DialogType
import uk.co.caprica.vlcj.factory.Dialogs
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import kotlin.math.roundToInt
import uk.co.caprica.vlcj.factory.MediaPlayerFactory as VlcFactory

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

    // Frames are drawn by Compose (MediaPlayerContent), not by a native window: see VideoFrameSink.
    private val frameSink = VideoFrameSink()

    /** The picture to draw; null before the first frame and for audio-only streams. */
    val videoFrame: StateFlow<VideoFrame?> = frameSink.frame

    // Null when libVLC cannot be loaded (macOS / Linux without VLC installed). The app still runs;
    // every channel then fails with the generic stream error instead of the app crashing at start.
    private val vlcFactory: VlcFactory? = runCatching { VlcFactory("--quiet") }
        .onFailure { println("libVLC unavailable, desktop playback is off: $it") }
        .getOrNull()

    // Held so the native callbacks stay reachable for the factory's lifetime.
    private val vlcDialogs: Dialogs? = vlcFactory?.let(::acceptInsecureSites)

    private val vlcjMediaPlayer: EmbeddedMediaPlayer? = vlcFactory?.let { factory ->
        factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
            videoSurface().set(factory.videoSurfaces().newVideoSurface(frameSink, frameSink, true))
        }
    }

    init {
        vlcjMediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun opening(mediaPlayer: MediaPlayer) {
                _isBuffering.value = true
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.PLAYING
                _isPlaying.value = true
                _isBuffering.value = false
                // libVLC creates the audio output per stream, so a volume set before it is lost.
                // Never call back into libVLC on its event thread: submit() runs it elsewhere.
                mediaPlayer.submit { applyAudio(mediaPlayer) }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.PAUSED
                _isPlaying.value = false
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                _isBuffering.value = false
                // The stop after a refusal must not turn ERROR back into IDLE.
                if (_playbackError.value == null) _playbackState.value = PlaybackState.IDLE
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.ENDED
                _isPlaying.value = false
                _isBuffering.value = false
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _playbackError.value = PlaybackError.STREAM_FAILED
                _playbackState.value = PlaybackState.ERROR
                _isPlaying.value = false
                _isBuffering.value = false
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
                    vlcjMediaPlayer?.let { _currentPosition.value = it.status().time() }
                }
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }

    /**
     * Stream hosts often present a certificate for another name (CDN wildcards, e.g. a Stremio
     * addon's `pull1.example.com` served with `*.liveplay.myqcloud.com`). Android's player trusts
     * every certificate (PlayerHttpDataSource); libVLC instead asks "Insecure site" twice ("View
     * certificate", then "Accept 24 hours") and, with nobody to answer, fails the stream. Both are
     * answered with action 1, so the key is kept for 24 hours like VLC's own player does. Any other
     * question is dismissed, which is libVLC's behaviour without a handler.
     *
     * The title is matched in English: the bundled VLC ships no translations (prepareDesktopVlc).
     */
    private fun acceptInsecureSites(factory: VlcFactory): Dialogs? = runCatching {
        factory.dialogs().newDialogs(DialogType.QUESTION).apply {
            addDialogHandler(object : DialogHandlerAdapter() {
                override fun displayQuestion(
                    userData: Long?, id: DialogId, title: String?, text: String?,
                    type: DialogQuestionType?, cancel: String?, action1: String?, action2: String?,
                ) {
                    val accept = title == "Insecure site" && action1 != null
                    // Never call back into libVLC from its own callback thread.
                    coroutineScope.launch(Dispatchers.IO) {
                        if (accept) factory.dialogs().postAction(id, 1) else factory.dialogs().dismiss(id)
                    }
                }
            })
            factory.dialogs().enable(this)
        }
    }.onFailure { println("libVLC dialogs unavailable: $it") }.getOrNull()

    override suspend fun prepare(mediaItem: MediaItem) {
        _currentMedia.value = mediaItem
        _currentPosition.value = 0L
        _duration.value = 0L
        _isPlaying.value = false

        // VLC has no Widevine / PlayReady CDM: refuse before the stream is requested.
        _playbackError.value = PlaybackPreflight.check(mediaItem, platformSupportsDrm = false)
            ?: PlaybackError.STREAM_FAILED.takeIf { vlcjMediaPlayer == null }
        val player = vlcjMediaPlayer
        if (_playbackError.value != null || player == null) {
            player?.controls()?.stop()
            frameSink.clear()
            _playbackState.value = PlaybackState.ERROR
            return
        }

        // Loaded, not started: play() starts it. This used to start playback and pause straight
        // after, which raced with libVLC's asynchronous start.
        // prepare() stops the old stream before it returns, so its frames are all in by now.
        player.media().prepare(mediaItem.uri, *vlcHeaderOptions(mediaItem.headers))
        frameSink.clear()
        addSubtitles(player, mediaItem)

        _playbackState.value = PlaybackState.READY
    }

    /**
     * F3: side-loaded subtitles (WebVTT / SubRip) as VLC slaves. The track in the system
     * language is selected; the others stay available. URLs are never logged.
     */
    private fun addSubtitles(player: MediaPlayer, mediaItem: MediaItem) {
        if (mediaItem.subtitles.isEmpty()) return
        // Not preselected (spec §8.7 has no default): the viewer picks one in the track menu.
        mediaItem.subtitles.forEach { track ->
            runCatching {
                player.media().addSlave(uk.co.caprica.vlcj.media.MediaSlaveType.SUBTITLE, track.url, false)
            }
        }
    }

    // vlcj 4 (libVLC 3) lists subtitle tracks as descriptions; id -1 is VLC's "Disable" entry.
    override fun textTracks(): List<tss.t.tsiptv.player.TextTrackOption> = runCatching {
        val player = vlcjMediaPlayer ?: return emptyList()
        val current = player.subpictures().track()
        player.subpictures().trackDescriptions()
            .filter { it.id() >= 0 }
            .mapIndexed { i, track ->
                tss.t.tsiptv.player.TextTrackOption(
                    id = track.id().toString(),
                    label = track.description()?.takeIf { it.isNotBlank() } ?: "${i + 1}",
                    language = null,
                    selected = track.id() == current,
                )
            }
    }.getOrDefault(emptyList())

    override fun selectTextTrack(id: String?) {
        runCatching {
            val player = vlcjMediaPlayer ?: return
            if (id == null) player.subpictures().setTrack(-1)
            else id.toIntOrNull()?.let { player.subpictures().setTrack(it) }
        }
    }

    override suspend fun captureFrame(): VideoSnapshot? = frameSink.snapshot()

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
        vlcjMediaPlayer?.controls()?.play()
    }

    override suspend fun pause() {
        if (_playbackError.value != null) return
        // setPause(true), not pause(): libVLC's pause toggles, so a second Pause would resume.
        vlcjMediaPlayer?.controls()?.setPause(true)
    }

    override suspend fun stop() {
        vlcjMediaPlayer?.controls()?.stop()
        frameSink.clear()
    }

    override suspend fun seekTo(positionMs: Long) {
        vlcjMediaPlayer?.controls()?.setTime(positionMs)
        _currentPosition.value = positionMs
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        vlcjMediaPlayer?.controls()?.setRate(speed)
        _playbackSpeed.value = speed
    }

    override suspend fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        // The slider shows 0 while muted, so moving it up unmutes.
        if (_volume.value > 0f) _isMuted.value = false
        vlcjMediaPlayer?.let(::applyAudio)
    }

    override suspend fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        vlcjMediaPlayer?.let(::applyAudio)
    }

    // libVLC's volume runs 0..200 with 100 as unchanged; the app's runs 0..1.
    private fun applyAudio(player: MediaPlayer) {
        player.audio().setVolume((_volume.value * 100).roundToInt())
        player.audio().setMute(_isMuted.value)
    }

    override suspend fun release() {
        vlcjMediaPlayer?.release()
        if (vlcDialogs != null) vlcFactory?.dialogs()?.disable()
        vlcFactory?.release()
        _isPlaying.value = false
        _playbackState.value = PlaybackState.IDLE
    }
}
