package tss.t.tsiptv.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tss.t.tsiptv.core.database.Channel
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.PlaybackState
import tss.t.tsiptv.player.models.toMediaItem

class PlayerViewModel(
    private val _mediaPlayer: MediaPlayer,
) : ViewModel() {
    private val _mediaItem by lazy {
        MutableStateFlow(
            MediaItem.EMPTY
        )
    }
    val mediaItemState: StateFlow<MediaItem> = _mediaItem
    val playbackState: StateFlow<PlaybackState> = _mediaPlayer.playbackState
    val player: MediaPlayer = _mediaPlayer

    fun playMedia(mediaItem: MediaItem) {
        _mediaItem.value = mediaItem
    }

    fun playIptv(iptvChannel: Channel) {
        viewModelScope.launch {
            val item = iptvChannel.toMediaItem()
            _mediaItem.value = item
            launch {
                loadPrograms(iptvChannel)
            }
            withContext(Dispatchers.Main) {
                _mediaPlayer.prepare(item)
                _mediaPlayer.play()
            }
        }
    }

    fun loadPrograms(iptvChannel: Channel) {
        viewModelScope.launch(Dispatchers.IO) {

        }
    }

    fun onHandleEvent(event: PlayerEvent) {

    }
}

sealed class PlayerEvent {
    data class PlayMedia(val mediaItem: MediaItem) : PlayerEvent()
    data class PlayIptv(val iptvChannel: Channel) : PlayerEvent()

    data object OnPlayBackground : PlayerEvent()
    data object OnPictureInPicture : PlayerEvent()
}
