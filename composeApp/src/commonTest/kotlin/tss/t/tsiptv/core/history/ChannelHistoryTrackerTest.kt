package tss.t.tsiptv.core.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.database.InMemoryIPTVDatabase
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** QC round 2, item 23: watch time only while the player really plays. */
class ChannelHistoryTrackerTest {

    private class FakePlayer : MediaPlayer {
        val playing = MutableStateFlow(false)
        override val playbackState: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState.IDLE)
        override val currentMedia = MutableStateFlow<MediaItem?>(null)
        override val currentPosition: StateFlow<Long> = MutableStateFlow(0L)
        override val duration: StateFlow<Long> = MutableStateFlow(0L)
        override val playbackSpeed: StateFlow<Float> = MutableStateFlow(1f)
        override val isBuffering: StateFlow<Boolean> = MutableStateFlow(false)
        override val isPlaying: StateFlow<Boolean> = playing
        override val volume: StateFlow<Float> = MutableStateFlow(1f)
        override val isMuted: StateFlow<Boolean> = MutableStateFlow(false)
        override val playbackError: StateFlow<PlaybackError?> = MutableStateFlow(null)
        override suspend fun prepare(mediaItem: MediaItem) {}
        override suspend fun play() {}
        override suspend fun pause() {}
        override suspend fun stop() {}
        override suspend fun seekTo(positionMs: Long) {}
        override suspend fun setPlaybackSpeed(speed: Float) {}
        override suspend fun setVolume(volume: Float) {}
        override suspend fun setMuted(muted: Boolean) {}
        override suspend fun release() {}
    }

    private val channel = Channel("c", "C", "https://cdn.example.com/c.m3u8", playlistId = "p")

    private suspend fun watchedMs(db: InMemoryIPTVDatabase) =
        db.getAllPlayedChannelsInPlaylist("p").first().single().totalPlayedTimeMs

    @Test
    fun channelThatNeverPlaysGetsNoWatchTime() = runBlocking {
        val db = InMemoryIPTVDatabase()
        val player = FakePlayer()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val tracker = ChannelHistoryTracker(db, scope, player)
        try {
            tracker.onChannelPlay(channel, "p") // e.g. answered 403: the error overlay shows
            delay(80)
            tracker.onPlaybackStopped()
            assertEquals(0L, watchedMs(db))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun timeCountsFromWhenThePlayerStartsPlaying() = runBlocking {
        val db = InMemoryIPTVDatabase()
        val player = FakePlayer()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val tracker = ChannelHistoryTracker(db, scope, player)
        try {
            tracker.onChannelPlay(channel, "p")
            player.playing.value = true
            delay(120)
            player.playing.value = false // pausing saves the session
            delay(50)
            assertTrue(watchedMs(db) > 0)
        } finally {
            scope.cancel()
        }
    }
}
