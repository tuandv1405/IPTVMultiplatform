package tss.t.tsiptv.feature.clip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.utils.formatDynamic

/**
 * Player › Clip: a short piece of what plays, saved as an MP4 on this device only (no upload).
 * Short on purpose: a clip is a moment to keep or send, not a recording of the channel.
 */
object Clips {
    /** The lengths offered; the longest is also the cap. */
    val LENGTHS_MS = listOf(15_000L, 30_000L, 60_000L)
    const val MAX_MS = 60_000L

    /** Less than this is not worth a file (stopped at once, or the stream ended). */
    const val MIN_MS = 2_000L

    /** "VTV1 HD 2026-10-14 23.15.02.mp4": the title, without characters file systems refuse. */
    fun fileName(title: String, nowMs: Long): String {
        val safe = title.map { if (it < ' ' || it in "\\/:*?\"<>|") ' ' else it }.joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
            .take(60).trimEnd('.', ' ')
            .ifEmpty { "TS IPTV" }
        return "$safe ${nowMs.formatDynamic("yyyy-MM-dd HH.mm.ss")}.mp4"
    }

    /** Why [item] cannot be clipped on any platform, or null when it may be. */
    fun check(item: MediaItem): ClipFailure? = when {
        // Not possible (the picture is encrypted) and not allowed.
        item.drm != null -> ClipFailure.PROTECTED
        item.isRadio -> ClipFailure.UNSUPPORTED
        else -> null
    }
}

/**
 * One clip being made: the recorder reports how much it has here and reads the viewer's Stop.
 * Cancelling the coroutine that records throws the clip away; [stop] keeps what is recorded.
 */
class ClipSession(val lengthMs: Long) {
    private val _recordedMs = MutableStateFlow(0L)

    /** Content recorded so far (not wall-clock time: a live playlist can give some at once). */
    val recordedMs: StateFlow<Long> = _recordedMs.asStateFlow()

    private val _finishing = MutableStateFlow(false)

    /** True once recording is over and the file is being written as MP4. */
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()

    private val _stopRequested = MutableStateFlow(false)
    val stopRequested: StateFlow<Boolean> = _stopRequested.asStateFlow()

    /** Ends the clip early, keeping what is recorded. */
    fun stop() {
        _stopRequested.value = true
    }

    fun report(recordedMs: Long) {
        _recordedMs.value = recordedMs.coerceIn(0L, lengthMs)
    }

    fun finishing() {
        _finishing.value = true
    }
}

sealed interface ClipResult {
    data class Saved(val clip: SavedClip) : ClipResult
    data class Failed(val reason: ClipFailure) : ClipResult
}

enum class ClipFailure {
    /** DRM: never recorded. */
    PROTECTED,

    /** A kind of stream this platform cannot cut (DASH, radio, separate audio playlists). */
    UNSUPPORTED,

    /** The stream could not be read. */
    NETWORK,

    /** Recorded, but could not be turned into a video file. */
    CONVERT,

    /** The video could not be saved (storage full, no permission). */
    STORAGE,

    /** Less than [Clips.MIN_MS] was recorded. */
    TOO_SHORT,
}

/**
 * A saved clip.
 *
 * @param uri what the platform opens or shares: a `content://` URI on Android, a file path on desktop
 * @param where the folder, as the viewer finds it ("Movies/TS IPTV")
 */
data class SavedClip(val uri: String, val name: String, val where: String, val durationMs: Long)

/**
 * The platform side of Player › Clip, from [tss.t.tsiptv.player.MediaPlayer.clipRecorder]. The stream
 * URL and its headers never leave the recorder (no logs, no analytics).
 */
interface ClipRecorder {
    /**
     * Records [item] for up to [ClipSession.lengthMs] and saves it.
     *
     * @param live a live channel: from what plays now on; otherwise from [fromMs]
     */
    suspend fun record(item: MediaItem, live: Boolean, fromMs: Long, session: ClipSession): ClipResult

    /** Whether [share] can send a clip here. */
    val canShare: Boolean get() = false

    /** Sends [clip] with [text]: the system share sheet, or the clipboard where there is none. */
    fun share(clip: SavedClip, text: String): ClipShareOutcome = ClipShareOutcome.FAILED

    /** Opens [clip] in the system's video player, or shows it in its folder. */
    fun open(clip: SavedClip): Boolean
}

enum class ClipShareOutcome {
    /** The share sheet is in front. */
    OPENED,

    /** The video file is on the clipboard, to paste into a chat app (desktop). */
    COPIED,
    FAILED,
}
