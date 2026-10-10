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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.firebase.analystics.AnalyticsConstants
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.rating.AppRatingController
import tss.t.tsiptv.core.stremio.MediaPlaybackContext
import tss.t.tsiptv.core.stremio.MediaProgressTracker
import tss.t.tsiptv.core.stremio.StremioType
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
    private val appRating: AppRatingController,
    private val progressTracker: MediaProgressTracker,
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
                        appRating.onPlaybackStarted(_mediaPlayer.currentMedia.value?.id.orEmpty())
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

    /** The channel asked for last, before the player has taken it; stops a second start. */
    private var requestedChannelId: String? = null

    /** F3: the streams of the channel playing, when it has several (the player's stream menu). */
    private val _channelStreams = MutableStateFlow<ChannelStreamChoice?>(null)
    val channelStreams: StateFlow<ChannelStreamChoice?> = _channelStreams

    /** F3: plays another stream of the current channel (picked in the player's menu). */
    fun playChannelStream(index: Int) {
        val choice = _channelStreams.value ?: return
        val variant = tss.t.tsiptv.core.tsiptv.TsiptvChannelStreams.variants(choice.channel).getOrNull(index) ?: return
        _channelStreams.value = choice.copy(current = index)
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _mediaPlayer.prepare(variant.toMediaItem())
                _mediaPlayer.play()
            }
        }
    }

    fun playMedia(mediaItem: MediaItem) {
        verifyPlayingMediaItem(mediaItem.id)
    }

    @OptIn(ExperimentalTime::class)
    fun playIptv(iptvChannel: Channel) {
        requestedChannelId = iptvChannel.id
        viewModelScope.launch {
            // History, "continue watching" and the mini player hand over partial rows
            // (ChannelWithHistory); headers and DRM live only on the full channel.
            val channel = withContext(Dispatchers.IO) { _iptvDatabase.getChannelById(iptvChannel.id) }
                ?.takeIf { it.playlistId == iptvChannel.playlistId }
                ?: iptvChannel
            // F3: a source channel may have several streams; play the first this device supports.
            val variants = tss.t.tsiptv.core.tsiptv.TsiptvChannelStreams.variants(channel)
            val index = tss.t.tsiptv.core.tsiptv.TsiptvChannelStreams.firstPlayable(variants) {
                tss.t.tsiptv.player.models.PlaybackPreflight.check(it.toMediaItem(), _mediaPlayer.supportsDrm) == null
            }
            _channelStreams.value = if (variants.size > 1) ChannelStreamChoice(channel, index) else null
            val item = variants[index].toMediaItem()
            // Close the previous channel's history while the player still holds its position.
            historyTracker.onPlaybackStopped()
            withContext(Dispatchers.Main) {
                _mediaPlayer.prepare(item)
                _mediaPlayer.play()
            }
            // A channel refused before playback never played: no history row for it.
            if (_mediaPlayer.playbackError.value != null) return@launch

            // Hour only: channel names and stream links can identify a private source.
            Firebase.analytics.logEvent(
                AnalyticsConstants.EVENT_PLAY_IPTV_CHANNEL,
                mapOf(
                    AnalyticsConstants.PARAMS_IPTV_CHANNEL_PLAY_HOUR to Clock.System.now()
                        .toEpochMilliseconds()
                        .formatDynamic("HH"),
                )
            )

            // Track channel play history
            historyTracker.onChannelPlay(
                channel = channel,
                playlistId = channel.playlistId
            )
        }
    }

    /**
     * Plays a stream chosen in the addon stream picker (F2). The item is not in the channel
     * database, so it is prepared directly; progress goes to `media_history` instead of
     * `channel_history`. The stream URL is never logged or sent to analytics.
     */
    @OptIn(ExperimentalTime::class)
    fun playStream(item: MediaItem, playback: MediaPlaybackContext) {
        requestedChannelId = item.id
        _channelStreams.value = null
        viewModelScope.launch {
            historyTracker.onPlaybackStopped()
            progressTracker.save()
            withContext(Dispatchers.Main) {
                _mediaPlayer.prepare(item)
                _mediaPlayer.play()
            }
            if (_mediaPlayer.playbackError.value != null) return@launch
            progressTracker.start(item.id, playback)
            // Hour and a known content type only: titles and links can identify a private addon.
            Firebase.analytics.logEvent(
                AnalyticsConstants.EVENT_PLAY_ADDON_STREAM,
                mapOf(
                    AnalyticsConstants.PARAMS_IPTV_CHANNEL_PLAY_HOUR to Clock.System.now()
                        .toEpochMilliseconds()
                        .formatDynamic("HH"),
                    AnalyticsConstants.PARAMS_ADDON_CONTENT_TYPE to when (playback.itemType) {
                        StremioType.MOVIE, StremioType.SERIES, StremioType.TV, StremioType.CHANNEL -> playback.itemType
                        else -> "other"
                    },
                )
            )
        }
    }

    /**
     * TV: plays a stream cast from a paired phone (docs/prd-tv-cast-and-sync.md §2.6). Not a stored
     * channel: no history row; VOD starts at the phone's position.
     */
    fun playCast(stream: tss.t.tsiptv.feature.lan.CastStream): String {
        val prefix = if (stream.isLive) CAST_LIVE_MEDIA_ID_PREFIX else CAST_VOD_MEDIA_ID_PREFIX
        val item = MediaItem(
            id = prefix + stream.url.hashCode().toUInt().toString(16),
            uri = stream.url,
            title = stream.title,
            artworkUri = stream.logo,
            mimeType = stream.mimeType,
            headers = stream.headers,
            drm = stream.drm,
            subtitles = stream.subtitles,
        )
        requestedChannelId = item.id
        _channelStreams.value = null
        viewModelScope.launch {
            historyTracker.onPlaybackStopped()
            progressTracker.save()
            withContext(Dispatchers.Main) {
                _mediaPlayer.prepare(item)
                _mediaPlayer.play()
            }
            val start = stream.positionMs?.takeIf { !stream.isLive && it > 0 } ?: return@launch
            // prepare() swaps the source asynchronously: a seek now would be lost and the TV would
            // start at 0. Seek once this item is loaded (same rule as MediaProgressTracker's resume).
            val loaded = withTimeoutOrNull(CAST_SEEK_WAIT_MS) {
                kotlinx.coroutines.flow.combine(_mediaPlayer.currentMedia, _mediaPlayer.duration) { media, duration ->
                    (media?.id == item.id) to (duration > 0)
                }.first { (current, hasDuration) -> !current || hasDuration }
            }
            if (loaded != null && loaded.first && loaded.second && _mediaPlayer.currentMedia.value?.id == item.id) {
                val duration = _mediaPlayer.duration.value
                // Never past the end (a phone position of a longer cut, or a rounding at the end).
                val target = if (duration > CAST_END_MARGIN_MS) minOf(start, duration - CAST_END_MARGIN_MS) else start
                withContext(Dispatchers.Main) { _mediaPlayer.seekTo(target) }
            }
        }
        return item.id
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
                    progressTracker.save()
                }

                is PlayerEvent.SeekTo -> _mediaPlayer.seekTo(event.positionMs.coerceAtLeast(0))

                is PlayerEvent.SeekBy -> {
                    val duration = _mediaPlayer.duration.value
                    val target = (_mediaPlayer.currentPosition.value + event.deltaMs).coerceAtLeast(0)
                    _mediaPlayer.seekTo(if (duration > 0) target.coerceAtMost(duration - 1_000).coerceAtLeast(0) else target)
                }

                is PlayerEvent.Stop -> {
                    progressTracker.save()
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

    /**
     * Makes sure [channelId] is what plays. It used to look the channel up and call
     * [playMedia], which only came back here, so a resumed channel never actually started.
     */
    fun verifyPlayingMediaItem(channelId: String?) {
        if (channelId.isNullOrEmpty()) return
        // Addon streams are not channels; they are started by playStream().
        if (isAddonItemId(channelId)) return
        if (channelId == requestedChannelId || channelId == _mediaPlayer.currentMedia.value?.id) return
        viewModelScope.launch {
            _iptvDatabase.getChannelById(channelId)?.let { playIptv(it) }
        }
    }

    fun stopMedia() {
        viewModelScope.launch {
            progressTracker.save()
            _mediaPlayer.stop()
        }
    }

    fun resumeMediaItem(mediaItem: MediaItem) {
        _channelStreams.value = null
        viewModelScope.launch {
            val loaded = mediaItem.id == _mediaPlayer.currentMedia.value?.id &&
                    playbackState.value != PlaybackState.IDLE && playbackState.value != PlaybackState.ERROR
            if (loaded) {
                _mediaPlayer.play()
            } else if (isAddonItemId(mediaItem.id)) {
                // Not in the channel database: the item itself carries the stream and its headers.
                // Restart from the last known position, not from where the item was first started.
                val context = progressTracker.restartContext(mediaItem.id)
                withContext(Dispatchers.Main) {
                    _mediaPlayer.prepare(mediaItem)
                    _mediaPlayer.play()
                }
                context?.let { progressTracker.start(mediaItem.id, it) }
            } else {
                // Stopped, failed or never loaded: start it again from the stored channel.
                _iptvDatabase.getChannelById(mediaItem.id)?.let { playIptv(it) }
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

    /** VOD (addon movies/episodes): absolute and relative seeking. */
    data class SeekTo(val positionMs: Long) : PlayerEvent
    data class SeekBy(val deltaMs: Long) : PlayerEvent
}

/** Media item ids of addon streams (`stremio:{addonId}:{videoId}`), which are not channels. */
const val ADDON_MEDIA_ID_PREFIX = "stremio:"

/** F3: media item ids of TS IPTV Source movies and episodes (`tsvod:{playlistId}:{videoId}`). */
const val SOURCE_VOD_MEDIA_ID_PREFIX = "tsvod:"

/**
 * Items that are not channels (addon streams, TS IPTV Source movies/episodes): started by
 * [PlayerViewModel.playStream], tracked in `media_history`, VOD controls (seek), no zapping.
 */
fun isAddonItemId(id: String?): Boolean =
    id != null && (id.startsWith(ADDON_MEDIA_ID_PREFIX) || id.startsWith(SOURCE_VOD_MEDIA_ID_PREFIX) ||
        id.startsWith(CAST_VOD_MEDIA_ID_PREFIX))

/** Streams cast from a phone (TV side): VOD items get VOD controls, live ones play like a channel. */
const val CAST_VOD_MEDIA_ID_PREFIX = "castvod:"
const val CAST_LIVE_MEDIA_ID_PREFIX = "cast:"

/** How long a cast VOD may take to load before its start position is given up (it then plays from 0). */
private const val CAST_SEEK_WAIT_MS = 60_000L

/** A cast VOD never starts closer than this to its end. */
private const val CAST_END_MARGIN_MS = 5_000L

data class PlayerUIState(
    val isFullScreen: Boolean = false,
    val isFitWidth: Boolean = false,
    val isFillScreen169: Boolean = false,
    val isPlaying: Boolean = false,
)

/** The streams of a source channel and the one playing. */
data class ChannelStreamChoice(val channel: tss.t.tsiptv.core.model.Channel, val current: Int)
