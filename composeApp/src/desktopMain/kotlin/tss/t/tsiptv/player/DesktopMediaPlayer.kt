package tss.t.tsiptv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.Canvas
import java.awt.Component

/**
 * Desktop implementation of the MediaPlayer interface using VLCj.
 */
class DesktopMediaPlayer(
    private val coroutineScope: CoroutineScope
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
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
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
                _playbackState.value = PlaybackState.IDLE
            }
            
            override fun finished(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.ENDED
            }
            
            override fun error(mediaPlayer: MediaPlayer) {
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
        
        // Prepare the media
        vlcjMediaPlayer.media().play(mediaItem.uri)
        vlcjMediaPlayer.controls().pause()
        
        _playbackState.value = PlaybackState.READY
    }
    
    override suspend fun play() {
        vlcjMediaPlayer.controls().play()
    }
    
    override suspend fun pause() {
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
    
    override suspend fun release() {
        vlcjMediaPlayer.release()
        _playbackState.value = PlaybackState.IDLE
    }
}