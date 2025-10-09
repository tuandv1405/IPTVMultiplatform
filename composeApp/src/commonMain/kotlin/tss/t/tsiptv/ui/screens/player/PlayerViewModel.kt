package tss.t.tsiptv.ui.screens.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.firebase.analystics.AnalyticsConstants
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.player.models.toMediaItem
import tss.t.tsiptv.utils.formatDynamic
import tss.t.tsiptv.utils.getScreenOrientationUtils
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class PlayerViewModel(
    private val _mediaPlayer: MediaPlayer,
    private val _iptvDatabase: IPTVDatabase,
    private val historyTracker: ChannelHistoryTracker,
) : ViewModel() {

    // Flag to track if auto full-screen is enabled
    private var autoFullScreenEnabled = false

    // Flag to track if we're currently in full-screen mode
    private val _playerControlsUIState by lazy {
        MutableStateFlow(PlayerUIState())
    }

    val playerUIState: StateFlow<PlayerUIState>
        get() = _playerControlsUIState

    init {
        viewModelScope.launch {
            _mediaPlayer.isPlaying.collect { isPlaying ->
                if (isPlaying && autoFullScreenEnabled) {
                    handleFullScreenMode(true)
                }
                if (_playerControlsUIState.value.isPlaying != isPlaying) {
                    _playerControlsUIState.update {
                        it.copy(
                            isPlaying = isPlaying
                        )
                    }
                }
            }
        }

        // Observe playback state changes to track when media stops
        viewModelScope.launch {
            _mediaPlayer.playbackState.collect { state ->
                when (state) {
                    PlaybackState.ENDED, PlaybackState.IDLE -> {
                        // Media has stopped, save history
                        historyTracker.onPlaybackStopped()
                    }

                    PlaybackState.PLAYING -> {
                        // Media is playing, resume tracking if needed
                        historyTracker.onPlaybackResumed()
                    }

                    PlaybackState.PAUSED -> {
                        // Media is paused, pause tracking
                        historyTracker.onPlaybackPaused()
                    }

                    else -> {
                        // Other states (BUFFERING, READY, ERROR) - no action needed
                    }
                }
            }
        }
    }

    val mediaItemState: StateFlow<MediaItem> = _mediaPlayer.currentMedia
        .map {
            it ?: MediaItem.EMPTY
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(5000),
            initialValue = MediaItem.EMPTY
        )

    val playbackState: StateFlow<PlaybackState> = _mediaPlayer.playbackState
    val isPlaying: StateFlow<Boolean> = _mediaPlayer.isPlaying
    val volume: StateFlow<Float> = _mediaPlayer.volume
    val isMuted: StateFlow<Boolean> = _mediaPlayer.isMuted
    val player: MediaPlayer = _mediaPlayer

    @OptIn(ExperimentalTime::class)
    fun playMedia(mediaItem: MediaItem) {
        verifyPlayingMediaItem(mediaItem.id)

        Firebase.analytics.logEvent(
            AnalyticsConstants.EVENT_PLAY_IPTV_CHANNEL,
            mapOf(
                AnalyticsConstants.PARAMS_IPTV_NAME to mediaItem.title,
                AnalyticsConstants.PARAMS_IPTV_URL to mediaItem.uri,
                AnalyticsConstants.PARAMS_IPTV_CHANNEL_PLAY_HOUR to Clock.System.now()
                    .toEpochMilliseconds()
                    .formatDynamic("HH"),
            )
        )
    }

    fun playIptv(iptvChannel: Channel) {
        viewModelScope.launch {
            val item = iptvChannel.toMediaItem()
            withContext(Dispatchers.Main) {
                _mediaPlayer.prepare(item)
                _mediaPlayer.play()
            }

            // Track channel play history
            historyTracker.onChannelPlay(
                channel = iptvChannel,
                playlistId = iptvChannel.playlistId
            )
        }
    }

    fun onHandleEvent(event: PlayerEvent) {
        viewModelScope.launch {
            when (event) {
                is PlayerEvent.PlayMedia -> playMedia(event.mediaItem)
                is PlayerEvent.PlayIptv -> playIptv(event.iptvChannel)

                is PlayerEvent.OnPlayBackground -> {
                    // Handle background playback
                }

                is PlayerEvent.OnEnterFullScreen -> {
                    // Handle entering full-screen mode
                    handleFullScreenMode(true)
                }

                is PlayerEvent.OnExitFullScreen, is PlayerEvent.OnHorizontalPlayerBack -> {
                    handleFullScreenMode(false)
                }

                is PlayerEvent.OnPlayerViewFitWidth -> {
                    _playerControlsUIState.update {
                        it.copy(
                            isFitWidth = true,
                            isFillScreen169 = false
                        )
                    }
                }

                is PlayerEvent.OnPlayerViewFillScreen169 -> {
                    _playerControlsUIState.update {
                        it.copy(
                            isFillScreen169 = true,
                            isFitWidth = false
                        )
                    }
                }

                is PlayerEvent.OnPlayerViewExitFitWidth -> {
                    _playerControlsUIState.update {
                        it.copy(
                            isFitWidth = false
                        )
                    }
                }

                is PlayerEvent.OnPlayerViewExitFillScreen169 -> {
                    _playerControlsUIState.update {
                        it.copy(
                            isFillScreen169 = false
                        )
                    }
                }

                is PlayerEvent.Play -> {
                    _mediaPlayer.play()
                    historyTracker.onPlaybackResumed()
                }

                is PlayerEvent.Pause -> {
                    _mediaPlayer.pause()
                    historyTracker.onPlaybackPaused()
                }

                is PlayerEvent.Stop -> {
                    _mediaPlayer.stop()
                    historyTracker.onPlaybackStopped()
                }

                is PlayerEvent.ToggleMute -> toggleMute()
                is PlayerEvent.SetVolume -> setVolume(event.volume)
                else -> {}
            }
        }
    }

    /**
     * Handle full-screen mode.
     *
     * @param enterFullScreen True to enter full-screen mode, false to exit.
     */
    private fun handleFullScreenMode(enterFullScreen: Boolean) {
        val screenOrientationUtils = getScreenOrientationUtils()
        val isInFullScreenMode = _playerControlsUIState.value.isFullScreen
        if (enterFullScreen) {
            if (!isInFullScreenMode) {
                screenOrientationUtils.enterFullScreen()
                _playerControlsUIState.update {
                    it.copy(
                        isFullScreen = true
                    )
                }
            }
        } else {
            if (isInFullScreenMode) {
                screenOrientationUtils.exitFullScreen()
                _playerControlsUIState.update {
                    it.copy(
                        isFullScreen = false,
                        isFitWidth = false,
                        isFillScreen169 = false,
                    )
                }
            }
        }
    }

    private suspend fun togglePlayPause() {
        if (_mediaPlayer.isPlaying.value) {
            _mediaPlayer.pause()
            historyTracker.onPlaybackPaused()
        } else {
            _mediaPlayer.play()
            historyTracker.onPlaybackResumed()
        }
    }

    fun verifyPlayingMediaItem(channelId: String?) {
        if (channelId.isNullOrEmpty()) return
        viewModelScope.launch {
            val mediaItem = _mediaPlayer.currentMedia.value?.id
            if (mediaItem != channelId) {
                _iptvDatabase.getChannelById(channelId)?.toMediaItem()?.let {
                    playMedia(it)
                }
            }
        }
    }

    fun stopMedia() {
        viewModelScope.launch {
            _mediaPlayer.stop()
        }
    }

    fun resumeMediaItem(mediaItem: MediaItem) {
        viewModelScope.launch {
            if (isPlaying.value && mediaItem.id == _mediaPlayer.currentMedia.value?.id) {
                _mediaPlayer.play()
            } else {
                verifyPlayingMediaItem(mediaItem.id)
            }
        }
    }

    /**
     * Toggle mute/unmute
     */
    private fun toggleMute() {
        viewModelScope.launch {
            _mediaPlayer.setMuted(!_mediaPlayer.isMuted.value)
        }
    }

    /**
     * Set volume level (0.0 to 1.0)
     */
    private fun setVolume(volume: Float) {
        viewModelScope.launch {
            _mediaPlayer.setVolume(volume)
        }
    }
}

@Immutable
sealed interface PlayerEvent {
    data class PlayMedia(val mediaItem: MediaItem) : PlayerEvent
    data class PlayIptv(val iptvChannel: Channel) : PlayerEvent

    data object OnPlayBackground : PlayerEvent
    data object OnPictureInPicture : PlayerEvent

    data object Pause : PlayerEvent
    data object Stop : PlayerEvent
    data object Play : PlayerEvent
    data object OnVerticalPlayerBack : PlayerEvent

    data object OnSettings : PlayerEvent
    data object OnEnterFullScreen : PlayerEvent
    data object OnExitFullScreen : PlayerEvent

    data object OnPlayerViewFitWidth : PlayerEvent
    data object OnPlayerViewFillScreen169 : PlayerEvent
    data object OnPlayerViewExitFitWidth : PlayerEvent
    data object OnPlayerViewExitFillScreen169 : PlayerEvent

    data object OnHorizontalPlayerBack : PlayerEvent

    // Volume control events
    data object ToggleMute : PlayerEvent
    data class SetVolume(val volume: Float) : PlayerEvent
}

data class PlayerUIState(
    val isFullScreen: Boolean = false,
    val isFitWidth: Boolean = false,
    val isFillScreen169: Boolean = false,
    val isPlaying: Boolean = false,
)
