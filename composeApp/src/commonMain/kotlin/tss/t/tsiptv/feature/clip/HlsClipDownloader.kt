package tss.t.tsiptv.feature.clip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * Where the player is in an HLS stream, when the platform can tell (Android: the playlist it plays
 * and the segment under the playhead), so the clip starts at what is on screen.
 */
internal data class HlsStart(val variantUrl: String?, val sequence: Long?)

internal data class HlsRecording(val recordedMs: Long, val container: ClipContainer)

/**
 * Records an HLS stream by downloading its segments one after the other, decrypted, into one file:
 * the TS (or fMP4) a converter then turns into MP4 without re-encoding. Segments are the only cut
 * points, so a clip starts at a segment boundary (a keyframe).
 *
 * Live: from the segment under the playhead ([HlsStart]) or the newest one, then each new segment
 * as the playlist gets it, until [ClipSession.lengthMs] is reached or the viewer stops. VOD: from
 * the segment holding the position.
 */
internal class HlsClipDownloader(
    private val http: ClipHttp,
    private val aes: Aes128Cbc?,
    private val headers: Map<String, String>,
) {
    private val keys = HashMap<String, ByteArray>()

    suspend fun record(
        url: String,
        live: Boolean,
        fromMs: Long,
        session: ClipSession,
        sink: ClipSink,
        start: HlsStart? = null,
    ): HlsRecording {
        val (mediaUrl, firstPlaylist) = mediaPlaylist(url, start?.variantUrl)
        var playlist = firstPlaylist
        var next = when {
            live && !playlist.ended -> start?.sequence ?: playlist.segments.lastOrNull()?.sequence ?: playlist.mediaSequence
            else -> sequenceAt(playlist, fromMs)
        }
        var recordedMs = 0L
        var container: ClipContainer? = null
        var initWritten: HlsInit? = null
        var lastNew = TimeSource.Monotonic.markNow()

        fun done() = HlsRecording(recordedMs, container ?: ClipContainer.OTHER)

        while (true) {
            // Fell behind the live window (a long stall): carry on from its oldest segment.
            playlist.segments.firstOrNull()?.let { if (next < it.sequence) next = it.sequence }
            val pending = playlist.segments.filter { it.sequence >= next }
            if (pending.isNotEmpty()) lastNew = TimeSource.Monotonic.markNow()
            for (segment in pending) {
                // New timestamps or a new format (an ad break): the clip ends before it.
                if (segment.discontinuity && recordedMs > 0) return done()
                val key = segment.key
                if (key != null && (key.method != "AES-128" || key.url == null || aes == null)) {
                    if (recordedMs >= Clips.MIN_MS) return done()
                    throw ClipException(ClipFailure.UNSUPPORTED, "segment encryption ${key.method}")
                }
                val bytes = try {
                    val init = segment.init
                    val written = initWritten
                    val initBytes = if (init != null && (written == null || written.url != init.url || written.range != init.range)) {
                        // A second init section is a format change, like a discontinuity.
                        if (written != null) return done()
                        decrypt(init.key, segment.sequence, http.fetch(init.url, headers, init.range).bytes)
                    } else null
                    val body = decrypt(key, segment.sequence, http.fetch(segment.url, headers, segment.range).bytes)
                    if (initBytes != null) {
                        container = container ?: ClipContainer.sniff(initBytes)
                        sink.write(initBytes, initBytes.size)
                        initWritten = init
                    }
                    body
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep a clip that is long enough already; otherwise the stream failed.
                    if (recordedMs >= Clips.MIN_MS) return done()
                    throw (e as? ClipException) ?: ClipException(ClipFailure.NETWORK, e::class.simpleName)
                }
                container = container ?: ClipContainer.sniff(bytes)
                sink.write(bytes, bytes.size)
                recordedMs += segment.durationMs
                next = segment.sequence + 1
                session.report(recordedMs)
                if (recordedMs >= session.lengthMs || session.stopRequested.value) return done()
            }
            if (!live || playlist.ended) return done()

            // Live: wait for the playlist to grow, or for Stop.
            val wait = (playlist.targetDurationMs / 2).coerceIn(MIN_POLL_MS, MAX_POLL_MS)
            if (withTimeoutOrNull(wait) { session.stopRequested.first { it } } != null) return done()
            if (lastNew.elapsedNow().inWholeMilliseconds > maxOf(STALL_MS, playlist.targetDurationMs * 3)) {
                if (recordedMs >= Clips.MIN_MS) return done()
                throw ClipException(ClipFailure.NETWORK, "the live playlist stopped growing")
            }
            playlist = try {
                fetchMedia(mediaUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed refresh is retried on the next turn, until the stall limit.
                playlist
            }
        }
    }

    /** The media playlist to record: [url] itself, or the variant of it that suits a clip. */
    private suspend fun mediaPlaylist(url: String, preferredVariant: String?): Pair<String, HlsMediaPlaylist> {
        val body = fetchText(url)
        return when (val parsed = HlsParser.parse(body.text, body.url)) {
            is HlsMediaPlaylist -> body.url to parsed
            is HlsMultivariant -> {
                val variant = chooseVariant(parsed, preferredVariant)
                    ?: throw ClipException(ClipFailure.UNSUPPORTED, "audio only in separate playlists")
                variant.url to fetchMedia(variant.url)
            }
            null -> throw ClipException(ClipFailure.UNSUPPORTED, "not an HLS playlist")
        }
    }

    private suspend fun fetchMedia(url: String): HlsMediaPlaylist {
        val body = fetchText(url)
        return HlsParser.parse(body.text, body.url) as? HlsMediaPlaylist
            ?: throw ClipException(ClipFailure.UNSUPPORTED, "not a media playlist")
    }

    private class Text(val text: String, val url: String)

    private suspend fun fetchText(url: String): Text = try {
        http.fetch(url, headers).let { Text(it.bytes.decodeToString(), it.url) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClipException) {
        throw e
    } catch (e: Exception) {
        throw ClipException(ClipFailure.NETWORK, e::class.simpleName)
    }

    /** [data] decrypted with [key]; without an IV attribute the IV is the media [sequence] (RFC 8216 §5.2). */
    private suspend fun decrypt(key: HlsKey?, sequence: Long, data: ByteArray): ByteArray {
        if (key == null || key.method != "AES-128") return data
        val cipher = aes ?: return data
        val keyUrl = key.url ?: return data
        val keyBytes = keys[keyUrl] ?: http.fetch(keyUrl, headers).bytes.also { keys[keyUrl] = it }
        return cipher.decrypt(keyBytes, key.iv ?: sequenceIv(sequence), data)
    }

    companion object {
        private const val MIN_POLL_MS = 1_000L
        private const val MAX_POLL_MS = 5_000L
        private const val STALL_MS = 20_000L

        /** The segment holding [positionMs] (the last one past the end). */
        fun sequenceAt(playlist: HlsMediaPlaylist, positionMs: Long): Long {
            var start = 0L
            for (segment in playlist.segments) {
                if (positionMs < start + segment.durationMs) return segment.sequence
                start += segment.durationMs
            }
            return playlist.segments.lastOrNull()?.sequence ?: playlist.mediaSequence
        }

        /**
         * The variant a clip records: [preferred] (the one the player shows) when possible, else the
         * best up to 1080p. Only variants with their audio inside: a separate audio playlist would be
         * a second recording to merge.
         */
        fun chooseVariant(playlist: HlsMultivariant, preferred: String?): HlsVariant? {
            val muxed = playlist.variants.filter { variant ->
                variant.audioGroup == null ||
                    playlist.audios.none { it.groupId == variant.audioGroup && it.url != null }
            }
            muxed.firstOrNull { it.url == preferred }?.let { return it }
            val upTo1080 = muxed.filter { (it.height ?: 0) <= 1080 }
            return upTo1080.maxByOrNull { it.bandwidth } ?: muxed.minByOrNull { it.bandwidth }
        }

        fun sequenceIv(sequence: Long): ByteArray = ByteArray(16).also { iv ->
            for (i in 0 until 8) iv[15 - i] = (sequence ushr (8 * i)).toByte()
        }
    }
}
