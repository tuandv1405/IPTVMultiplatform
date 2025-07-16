package tss.t.tsiptv.ui.screens.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.player.models.toMediaItem
import tss.t.tsiptv.utils.getScreenOrientationUtils

class PlayerViewModel(
    private val _mediaPlayer: MediaPlayer,
    private val _iptvDatabase: IPTVDatabase,
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
    val player: MediaPlayer = _mediaPlayer

    fun playMedia(mediaItem: MediaItem) {
        verifyPlayingMediaItem(mediaItem.id)
    }

    fun playIptv(iptvChannel: Channel) {
        viewModelScope.launch {
            val item = iptvChannel.toMediaItem()
            withContext(Dispatchers.Main) {
                _mediaPlayer.prepare(item)
                _mediaPlayer.play()
            }
            launch {
                loadPrograms(iptvChannel)
            }
        }
    }

    fun loadPrograms(iptvChannel: Channel) {
        viewModelScope.launch(Dispatchers.IO) {

        }
    }

    fun onHandleEvent(event: PlayerEvent) {
        viewModelScope.launch {
            when (event) {
                is PlayerEvent.PlayMedia -> playMedia(event.mediaItem)
                is PlayerEvent.PlayIptv -> playIptv(event.iptvChannel)
                is PlayerEvent.TogglePlayPause -> togglePlayPause()
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

                is PlayerEvent.Play -> _mediaPlayer.play()
                is PlayerEvent.Pause -> _mediaPlayer.pause()
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
        } else {
            _mediaPlayer.play()
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
}

@Immutable
sealed interface PlayerEvent {
    data class PlayMedia(val mediaItem: MediaItem) : PlayerEvent
    data class PlayIptv(val iptvChannel: Channel) : PlayerEvent

    data object OnPlayBackground : PlayerEvent
    data object OnPictureInPicture : PlayerEvent
    data object TogglePlayPause : PlayerEvent

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
}

data class PlayerUIState(
    val isFullScreen: Boolean = false,
    val isFitWidth: Boolean = false,
    val isFillScreen169: Boolean = false,
)