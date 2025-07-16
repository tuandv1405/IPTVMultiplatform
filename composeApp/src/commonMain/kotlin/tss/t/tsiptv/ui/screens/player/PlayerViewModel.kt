package tss.t.tsiptv.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.player.models.toMediaItem

class PlayerViewModel(
    private val _mediaPlayer: MediaPlayer,
    private val _iptvDatabase: IPTVDatabase,
) : ViewModel() {
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

                }

                is PlayerEvent.Play -> _mediaPlayer.play()
                is PlayerEvent.Pause -> _mediaPlayer.pause()
                else -> {}
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

sealed class PlayerEvent {
    data class PlayMedia(val mediaItem: MediaItem) : PlayerEvent()
    data class PlayIptv(val iptvChannel: Channel) : PlayerEvent()

    data object OnPlayBackground : PlayerEvent()
    data object OnPictureInPicture : PlayerEvent()
    data object TogglePlayPause : PlayerEvent()

    data object Pause : PlayerEvent()
    data object Stop : PlayerEvent()
    data object Play : PlayerEvent()
    data object OnVerticalPlayerBack : PlayerEvent()

    data object OnSettings : PlayerEvent()
    data object OnEnterFullScreen : PlayerEvent()
}
