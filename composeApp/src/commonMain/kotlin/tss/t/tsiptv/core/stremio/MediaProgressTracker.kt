package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tss.t.tsiptv.player.MediaPlayer

/**
 * Saves addon playback progress to `media_history` every 10 s and on pause/stop (PRD §9), and
 * resumes from the saved position.
 *
 * - The resume seek waits until **this** item is loaded (current media id matches and its duration
 *   is known): players report READY before the new source replaced the old one, and a seek then is
 *   lost when the new source starts at 0.
 * - Nothing is saved before the resume seek was applied, so an early save cannot overwrite the
 *   resume point with 0.
 * - The position is sampled continuously while this item plays, so a hand-over or Back saves the
 *   latest position, not the one from the last 10 s tick.
 */
class MediaProgressTracker(
    private val player: MediaPlayer,
    private val history: MediaHistoryRepository,
    private val scope: CoroutineScope,
    private val intervalMs: Long = SAVE_INTERVAL_MS,
) {
    private val lock = Mutex()
    private var context: MediaPlaybackContext? = null
    private var mediaId: String? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    /** False while a resume seek is pending: saving then would store the pre-seek position. */
    private var resumeSettled = true
    private var job: Job? = null

    val currentContext: MediaPlaybackContext? get() = context

    /**
     * The context to re-prepare the current item with (after an error or from the mini player):
     * it resumes from the last known position, not from where the item was first started.
     */
    fun restartContext(mediaItemId: String): MediaPlaybackContext? {
        val c = context ?: return null
        if (mediaId != mediaItemId) return null
        val position = if (resumeSettled) lastPositionMs else c.startPositionMs
        return c.copy(startPositionMs = if (c.isLive) 0 else position)
    }

    /** Call right after `prepare()`/`play()` of the addon item [mediaItemId]. */
    fun start(mediaItemId: String, playback: MediaPlaybackContext) {
        scope.launch {
            lock.withLock {
                saveLocked()
                job?.cancel()
                context = playback
                mediaId = mediaItemId
                lastPositionMs = playback.startPositionMs
                lastDurationMs = 0
                resumeSettled = playback.isLive || playback.startPositionMs <= 0
                job = scope.launch { track(mediaItemId, playback) }
            }
        }
    }

    private suspend fun track(mediaItemId: String, playback: MediaPlaybackContext) = coroutineScope {
        if (!resumeSettled) {
            launch {
                // No timeout: a slow stream (> 20 s to start) must still resume. Wait while this item is
                // current; settle only once the seek was applied or the item stopped being current.
                // Until then nothing is saved, so the resume point cannot be overwritten with ~0.
                val (current, loaded) = combine(player.currentMedia, player.duration) { media, duration ->
                    (media?.id == mediaItemId) to (duration > 0)
                }.first { (current, loaded) -> !current || loaded }
                // Player controls belong on the main thread (ExoPlayer, AVPlayer); this scope is IO.
                if (current && loaded && isCurrent(mediaItemId)) {
                    withContext(Dispatchers.Main) { player.seekTo(playback.startPositionMs) }
                }
                lock.withLock { if (mediaId == mediaItemId) resumeSettled = true }
            }
        }
        launch {
            // Continuous sampling of this item's position (players reset it to 0 on a new prepare).
            combine(player.currentMedia, player.currentPosition, player.duration) { media, position, duration ->
                Triple(media?.id, position, duration)
            }.collect { (id, position, duration) ->
                if (id == mediaItemId && resumeSettled) {
                    if (position > 0) lastPositionMs = position
                    if (duration > 0) lastDurationMs = duration
                }
            }
        }
        launch {
            player.isPlaying.drop(1).collect { playing -> if (!playing) save() }
        }
        launch {
            // Another item took over (a channel, another title): close this record. No drop(1): the
            // current value is compared right after subscribing, so a change that happened before
            // tracking started is not missed.
            player.currentMedia.collect { media -> if (media?.id != mediaItemId) stop(mediaItemId) }
        }
        while (true) {
            delay(intervalMs)
            if (!isCurrent(mediaItemId)) break
            if (player.isPlaying.value) save()
        }
    }

    private fun isCurrent(mediaItemId: String) = player.currentMedia.value?.id == mediaItemId

    /** Saves the current position now (pause, stop, leaving the player). */
    fun save() {
        scope.launch { lock.withLock { saveLocked() } }
    }

    private suspend fun saveLocked() {
        val playback = context ?: return
        val id = mediaId ?: return
        if (!resumeSettled) return
        if (player.currentMedia.value?.id == id) {
            val position = player.currentPosition.value
            val duration = player.duration.value
            if (position > 0) lastPositionMs = position
            if (duration > 0) lastDurationMs = duration
        }
        history.saveProgress(playback, lastPositionMs, lastDurationMs)
    }

    /** Final save and stop tracking [mediaItemId] (ignored if another item is tracked by then). */
    fun stop(mediaItemId: String) {
        scope.launch {
            lock.withLock {
                if (mediaId != mediaItemId) return@withLock
                saveLocked()
                job?.cancel()
                job = null
                context = null
                mediaId = null
            }
        }
    }

    companion object {
        const val SAVE_INTERVAL_MS = 10_000L
    }
}
