package tss.t.tsiptv.core.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

actual fun createMediaPlayer(): IMediaPlayer {
   return VlcjIMediaPlayer()
}


class VlcjIMediaPlayer : IMediaPlayer {
    private val mediaPlayerComponent = EmbeddedMediaPlayerComponent()
    private val vlcPlayer = mediaPlayerComponent.mediaPlayer()

    private val _playerState = MutableStateFlow(PlayerState.IDLE)
    override val playerState: StateFlow<PlayerState> = _playerState

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration

    private val _bufferedPosition = MutableStateFlow(0L)
    override val bufferedPosition: StateFlow<Long> = _bufferedPosition

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    override val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem

    init {
        vlcPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _playerState.value = PlayerState.PLAYING
                _isPlaying.value = true
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _playerState.value = PlayerState.PAUSED
                _isPlaying.value = false
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _playerState.value = PlayerState.IDLE
                _isPlaying.value = false
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _currentPosition.value = newTime
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _duration.value = newLength
            }
        })
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        _currentMediaItem.value = mediaItem
        vlcPlayer.media().play(mediaItem.uri)
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int
    ) {
    }

    override fun prepare() {
        _playerState.value = PlayerState.READY
    }

    override fun play() {
        vlcPlayer.controls().play()
    }

    override fun pause() {
        vlcPlayer.controls().pause()
    }

    override fun stop() {
        vlcPlayer.controls().stop()
    }

    override fun seekTo(positionMs: Long) {
        vlcPlayer.controls().setTime(positionMs)
    }

    override fun seekToNext() {
    }

    override fun seekToPrevious() {
    }

    override fun setPlaybackSpeed(speed: Float) {
        vlcPlayer.controls().setRate(speed)
        _playbackSpeed.value = speed
    }

    override fun setVolume(volume: Float) {
        vlcPlayer.audio().setVolume((volume * 100).toInt())
        _volume.value = volume
    }

    override fun setMuted(muted: Boolean) {
        vlcPlayer.audio().setMute(muted)
        _isMuted.value = muted
    }

    override fun release() {
        vlcPlayer.release()
        mediaPlayerComponent.release()
    }

    @Composable
    override fun PlayerView(modifier: Modifier) {
        Box(modifier = modifier) {
            SwingPanel(
                factory = { mediaPlayerComponent },
                modifier = modifier
            )
        }

        DisposableEffect(Unit) {
            onDispose {
                release()
            }
        }
    }

    // Implement other required methods from the interface...
}
