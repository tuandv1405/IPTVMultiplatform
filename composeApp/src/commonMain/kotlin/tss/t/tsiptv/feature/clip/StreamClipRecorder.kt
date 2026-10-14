package tss.t.tsiptv.feature.clip

import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

internal sealed interface StreamRecording {
    data class Recorded(val recordedMs: Long, val container: ClipContainer) : StreamRecording

    /** The URL answers with an HLS playlist: record it with [HlsClipDownloader]. */
    data object IsPlaylist : StreamRecording
}

/**
 * Records a live stream sent as one long HTTP response (MPEG-TS from most IPTV panels): its bytes
 * for [ClipSession.lengthMs] of wall-clock time, which the server sends at playback speed.
 *
 * This is a second connection next to the player's; a panel that allows one connection per
 * account may refuse it (or drop the player).
 */
internal suspend fun recordLiveStream(
    http: ClipHttp,
    url: String,
    headers: Map<String, String>,
    session: ClipSession,
    sink: ClipSink,
): StreamRecording {
    var container: ClipContainer? = null
    var isPlaylist = false
    var started: TimeSource.Monotonic.ValueTimeMark? = null
    var recordedMs = 0L
    try {
        http.stream(url, headers) { chunk, length ->
            if (container == null) {
                if (chunk.decodeToString(0, minOf(length, 7)) == "#EXTM3U") {
                    isPlaylist = true
                    return@stream false
                }
                container = ClipContainer.sniff(chunk, length)
                started = TimeSource.Monotonic.markNow()
            }
            sink.write(chunk, length)
            recordedMs = started!!.elapsedNow().inWholeMilliseconds
            session.report(recordedMs)
            recordedMs < session.lengthMs && !session.stopRequested.value
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The server closed or failed mid-way: keep a clip that is long enough already.
        if (recordedMs < Clips.MIN_MS) throw (e as? ClipException) ?: ClipException(ClipFailure.NETWORK, e::class.simpleName)
    }
    if (isPlaylist) return StreamRecording.IsPlaylist
    return StreamRecording.Recorded(recordedMs.coerceAtMost(session.lengthMs), container ?: ClipContainer.OTHER)
}

/** Whether [uri] or its [mimeType] names an HLS playlist, without asking the server. */
internal fun looksLikeHls(uri: String, mimeType: String?): Boolean {
    val mime = mimeType?.lowercase()
    if (mime != null && ("mpegurl" in mime)) return true
    return uri.substringBefore('?').substringBefore('#').lowercase().endsWith(".m3u8")
}

/** Whether [uri] or its [mimeType] names a DASH manifest, which clips do not support. */
internal fun looksLikeDash(uri: String, mimeType: String?): Boolean {
    val mime = mimeType?.lowercase()
    if (mime != null && "dash" in mime) return true
    return uri.substringBefore('?').substringBefore('#').lowercase().endsWith(".mpd")
}
