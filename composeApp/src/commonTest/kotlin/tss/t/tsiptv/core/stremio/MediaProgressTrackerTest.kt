package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeMediaPlayer : MediaPlayer {
    override val playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val currentMedia = MutableStateFlow<MediaItem?>(null)
    override val currentPosition = MutableStateFlow(0L)
    override val duration = MutableStateFlow(0L)
    override val playbackSpeed = MutableStateFlow(1f)
    override val isBuffering = MutableStateFlow(false)
    override val isPlaying = MutableStateFlow(false)
    override val volume = MutableStateFlow(1f)
    override val isMuted = MutableStateFlow(false)
    override val playbackError = MutableStateFlow<PlaybackError?>(null)
    val seeks = mutableListOf<Long>()
    override suspend fun prepare(mediaItem: MediaItem) { currentMedia.value = mediaItem; currentPosition.value = 0; duration.value = 0 }
    override suspend fun play() { isPlaying.value = true; playbackState.value = PlaybackState.PLAYING }
    override suspend fun pause() { isPlaying.value = false; playbackState.value = PlaybackState.PAUSED }
    override suspend fun stop() { isPlaying.value = false; playbackState.value = PlaybackState.IDLE }
    override suspend fun seekTo(positionMs: Long) { seeks += positionMs; currentPosition.value = positionMs }
    override suspend fun setPlaybackSpeed(speed: Float) = Unit
    override suspend fun setVolume(volume: Float) = Unit
    override suspend fun setMuted(muted: Boolean) = Unit
    override suspend fun release() = Unit
}

class MediaProgressTrackerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stores = InMemoryStremioStores()
    private val history = MediaHistoryRepository(stores, nowMs = { 1_000 })
    private val player = FakeMediaPlayer()
    private val tracker = MediaProgressTracker(player, history, scope, intervalMs = 50)

    @AfterTest
    fun tearDown() = scope.cancel()

    private val context = MediaPlaybackContext(
        sourceAddonId = "a", itemType = "movie", itemId = "m", videoId = "m", title = "Movie", subtitle = null,
        posterUrl = null, season = null, episode = null, streamAddonId = "a", bingeGroup = "g", isLive = false,
        startPositionMs = 120_000,
    )

    private suspend fun awaitRecord(predicate: (MediaHistoryRecord) -> Boolean): MediaHistoryRecord = withTimeout(5_000) {
        stores.observeHistory().first { rows -> rows.any(predicate) }.first(predicate)
    }

    @Test
    fun resumesSavesPeriodicallyAndOnPause() = runBlocking {
        val item = MediaItem(id = "stremio:a:m", uri = "https://a/m.mp4")
        player.prepare(item)
        player.play()
        tracker.start(item.id, context)
        // QC r1 #1: READY/PLAYING alone is not enough (the source may not be swapped yet): no seek,
        // and no save that could overwrite the resume point, until this item's duration is known.
        delay(300)
        assertEquals(emptyList(), player.seeks)
        assertEquals(emptyList(), stores.observeHistory().first())

        player.duration.value = 5_400_000
        withTimeout(5_000) { while (player.seeks.isEmpty()) delay(10) }
        assertEquals(listOf(120_000L), player.seeks)

        player.currentPosition.value = 130_000
        awaitRecord { it.positionMs == 130_000L && it.durationMs == 5_400_000L }

        player.currentPosition.value = 140_000
        player.pause()
        awaitRecord { it.positionMs == 140_000L }

        // Switching to another item (e.g. a channel) stops tracking: the channel's position is never
        // written into this title's record.
        player.prepare(MediaItem(id = "channel-1", uri = "https://c/1.m3u8"))
        player.play()
        player.currentPosition.value = 999_999
        delay(300)
        val rows = stores.observeHistory().first()
        assertEquals(1, rows.size)
        assertEquals(140_000L, rows.single().positionMs)
        assertEquals("g", rows.single().lastBingeGroup)
    }

    /** QC r2 N5: the item changed before tracking started (early change): tracking ends at once. */
    @Test
    fun trackingEndsWhenAnotherItemIsAlreadyCurrent() = runBlocking {
        player.prepare(MediaItem(id = "channel-1", uri = "https://c/1.m3u8"))
        player.play()
        tracker.start("stremio:a:m", context.copy(startPositionMs = 0))
        withTimeout(5_000) { while (tracker.restartContext("stremio:a:m") != null || tracker.currentContext != null) delay(10) }
        player.duration.value = 5_400_000
        player.currentPosition.value = 777_000
        delay(300)
        assertEquals(emptyList(), stores.observeHistory().first())
    }

    @Test
    fun restartResumesFromTheLastPositionAndStopIsTiedToTheItem() = runBlocking {
        val item = MediaItem(id = "stremio:a:m", uri = "https://a/m.mp4")
        player.prepare(item)
        player.play()
        tracker.start(item.id, context.copy(startPositionMs = 0))
        player.duration.value = 5_400_000
        player.currentPosition.value = 300_000
        awaitRecord { it.positionMs == 300_000L }
        // Re-prepare after an error: from the last known position, not from the first start point (0).
        assertEquals(300_000L, tracker.restartContext(item.id)?.startPositionMs)
        assertEquals(null, tracker.restartContext("stremio:other"))
        // A stale stop for another id does not end tracking of this one.
        tracker.stop("stremio:other")
        delay(100)
        assertEquals(item.id.let { tracker.restartContext(it)?.itemId }, "m")
    }
}
