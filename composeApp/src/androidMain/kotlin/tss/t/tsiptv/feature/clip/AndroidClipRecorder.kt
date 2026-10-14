package tss.t.tsiptv.feature.clip

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.network.ClipTap
import tss.t.tsiptv.player.network.playerHttpDataSourceFactory
import tss.t.tsiptv.player.service.MediaPlayerService
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.TimeSource
import androidx.media3.common.MediaItem as Media3Item

/**
 * Android clips, rewritten as MP4 by Media3 Transformer and saved to `Movies/TS IPTV`:
 * - HLS: the segments from the one under the playhead ([HlsClipDownloader]), then no re-encoding;
 * - a plain live stream: what the player reads is copied ([ClipTap]), no second connection;
 * - a file on a server (VOD): Transformer reads the piece itself. Its start is rarely a keyframe,
 *   so the first part is re-encoded.
 */
@OptIn(UnstableApi::class)
internal class AndroidClipRecorder(private val context: Context) : ClipRecorder {

    private val http = AndroidClipHttp()

    private enum class Kind { HLS, DASH, PROGRESSIVE }

    private class PlayerView(val kind: Kind?, val hlsStart: HlsStart?)

    override suspend fun record(item: MediaItem, live: Boolean, fromMs: Long, session: ClipSession): ClipResult {
        Clips.check(item)?.let { return ClipResult.Failed(it) }
        val view = withContext(Dispatchers.Main) { playerView(item) }
        val kind = view.kind ?: when {
            looksLikeHls(item.uri, item.mimeType) -> Kind.HLS
            looksLikeDash(item.uri, item.mimeType) -> Kind.DASH
            else -> Kind.PROGRESSIVE
        }
        if (kind == Kind.DASH) return ClipResult.Failed(ClipFailure.UNSUPPORTED)
        // One clip at a time (PlayerViewModel): whatever is left here is from a clip that died.
        val work = withContext(Dispatchers.IO) { File(context.cacheDir, "clips").apply { deleteRecursively(); mkdirs() } }
        return try {
            val output = File(work, "clip.mp4")
            val clipMs = when {
                kind == Kind.HLS -> {
                    val recorded = File(work, "recorded")
                    val recording = writeTo(recorded) { sink ->
                        HlsClipDownloader(http, JvmAes128Cbc, item.headers)
                            .record(item.uri, live, fromMs, session, sink, view.hlsStart)
                    }
                    convertRecording(recorded, recording.container, recording.recordedMs, session, output)
                }
                live -> {
                    val recorded = File(work, "recorded")
                    val recordedMs = tap(item.uri, recorded, session)
                    convertRecording(recorded, ClipContainer.OTHER, recordedMs, session, output)
                }
                else -> {
                    session.finishing()
                    val piece = Media3Item.Builder()
                        .setUri(item.uri)
                        .apply { item.mimeType?.let(::setMimeType) }
                        .setClippingConfiguration(
                            Media3Item.ClippingConfiguration.Builder()
                                .setStartPositionMs(fromMs)
                                .setEndPositionMs(fromMs + session.lengthMs)
                                .build()
                        )
                        .build()
                    val ok = export(piece, output, remoteHeaders = item.headers) { percent ->
                        session.report(session.lengthMs * percent / 100)
                    }
                    if (!ok) return ClipResult.Failed(ClipFailure.CONVERT)
                    session.lengthMs
                }
            }
            if (output.length() < MIN_BYTES) return ClipResult.Failed(ClipFailure.CONVERT)
            withContext(Dispatchers.IO) { save(output, item.title, clipMs) }
        } catch (e: ClipException) {
            ClipResult.Failed(e.failure)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    /**
     * What the player knows of [item]: its manifest type and, for HLS, the playlist it plays and
     * the segment under the playhead. Nothing when it plays something else. Main thread.
     */
    private fun playerView(item: MediaItem): PlayerView {
        val player = MediaPlayerService.getExoPlayer() ?: return PlayerView(null, null)
        // Not loaded yet (or another item): no manifest does not mean a plain stream then.
        if (player.currentMediaItem?.mediaId != item.id || player.currentTimeline.isEmpty) return PlayerView(null, null)
        return when (val manifest = player.currentManifest) {
            is HlsManifest -> {
                val playlist = manifest.mediaPlaylist
                val positionUs = player.currentPosition * 1000
                val index = playlist.segments.indexOfLast { it.relativeStartTimeUs <= positionUs }
                PlayerView(
                    Kind.HLS,
                    HlsStart(
                        variantUrl = playlist.baseUri,
                        sequence = index.takeIf { it >= 0 }?.let { playlist.mediaSequence + it },
                    ),
                )
            }
            is DashManifest -> PlayerView(Kind.DASH, null)
            null -> PlayerView(Kind.PROGRESSIVE, null)
            else -> PlayerView(null, null)
        }
    }

    private suspend fun <T> writeTo(file: File, block: suspend (ClipSink) -> T): T {
        val out = withContext(Dispatchers.IO) { BufferedOutputStream(file.outputStream()) }
        try {
            return block(ClipSink { bytes, length ->
                withContext(Dispatchers.IO) {
                    try {
                        out.write(bytes, 0, length)
                    } catch (e: IOException) {
                        throw ClipException(ClipFailure.STORAGE, e::class.simpleName)
                    }
                }
            })
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { out.close() } }
        }
    }

    /**
     * Copies what the player reads of [uri] for [ClipSession.lengthMs], counted from the first
     * byte (a stalled stream does not use up the clip).
     */
    private suspend fun tap(uri: String, file: File, session: ClipSession): Long {
        val out = withContext(Dispatchers.IO) { BufferedOutputStream(file.outputStream()) }
        // The data spec carries the URI as the player parsed it.
        ClipTap.start(Uri.parse(uri).toString(), out)
        val armed = TimeSource.Monotonic.markNow()
        var first: TimeSource.Monotonic.ValueTimeMark? = null
        var recordedMs = 0L
        var bytes = 0L
        try {
            while (recordedMs < session.lengthMs && !session.stopRequested.value) {
                if (first == null && ClipTap.recordedBytes() > 0) first = TimeSource.Monotonic.markNow()
                recordedMs = first?.elapsedNow()?.inWholeMilliseconds ?: 0L
                session.report(recordedMs)
                if (first == null && armed.elapsedNow().inWholeMilliseconds > TAP_START_TIMEOUT_MS) break
                withTimeoutOrNull(TAP_POLL_MS) { session.stopRequested.first { it } }
            }
        } finally {
            bytes = ClipTap.stop()
            withContext(NonCancellable + Dispatchers.IO) { runCatching { out.close() } }
        }
        if (bytes < 0) throw ClipException(ClipFailure.STORAGE)
        if (bytes == 0L) throw ClipException(ClipFailure.NETWORK, "the player read nothing")
        return recordedMs.coerceAtMost(session.lengthMs)
    }

    /** The recording as MP4, cut at the clip's length (an end cut needs no re-encoding). */
    private suspend fun convertRecording(
        recorded: File,
        container: ClipContainer,
        recordedMs: Long,
        session: ClipSession,
        output: File,
    ): Long {
        if (recordedMs < Clips.MIN_MS) throw ClipException(ClipFailure.TOO_SHORT)
        session.finishing()
        // The extension tells the extractor what to try first.
        val input = File(recorded.parentFile, "recorded.${container.extension}")
        withContext(Dispatchers.IO) { recorded.renameTo(input) }
        val clipMs = recordedMs.coerceAtMost(session.lengthMs)
        val item = Media3Item.Builder()
            .setUri(Uri.fromFile(input))
            .setClippingConfiguration(Media3Item.ClippingConfiguration.Builder().setEndPositionMs(clipMs).build())
            .build()
        if (!export(item, output, remoteHeaders = null, onProgress = null)) throw ClipException(ClipFailure.CONVERT)
        return clipMs
    }

    /**
     * Runs Transformer on the main looper. [remoteHeaders] non-null reads [item] from the server
     * through the player's HTTP stack with those headers.
     */
    private suspend fun export(
        item: Media3Item,
        output: File,
        remoteHeaders: Map<String, String>?,
        onProgress: ((Int) -> Unit)?,
    ): Boolean = withContext(Dispatchers.Main) {
        val handler = Handler(Looper.getMainLooper())
        suspendCancellableCoroutine { cont ->
            lateinit var poll: Runnable
            val builder = Transformer.Builder(context)
                // Media3's own MP4 writer: the framework MediaMuxer wrote H.264 from TS without its
                // SPS/PPS (an unplayable file) on the API 37 emulator.
                .setMuxerFactory(InAppMp4Muxer.Factory())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(false)
                    }
                })
            if (remoteHeaders != null) {
                val sources = DefaultMediaSourceFactory(context).setDataSourceFactory(playerHttpDataSourceFactory(remoteHeaders))
                builder.setAssetLoaderFactory(
                    DefaultAssetLoaderFactory(
                        context,
                        DefaultDecoderFactory.Builder(context).build(),
                        Clock.DEFAULT,
                        sources,
                        DataSourceBitmapLoader(context),
                    )
                )
                // Re-encodes only up to the first keyframe after the start, when the file allows.
                builder.experimentalSetTrimOptimizationEnabled(true)
            }
            val transformer = builder.build()
            transformer.start(EditedMediaItem.Builder(item).build(), output.absolutePath)
            val progress = ProgressHolder()
            poll = Runnable {
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress?.invoke(progress.progress)
                handler.postDelayed(poll, PROGRESS_POLL_MS)
            }
            handler.post(poll)
            // Transformer is used on the thread that built it.
            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(poll)
                    transformer.cancel()
                }
            }
        }
    }

    private fun save(file: File, title: String, durationMs: Long): ClipResult {
        val name = Clips.fileName(title, System.currentTimeMillis())
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, MIME_MP4)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return ClipResult.Failed(ClipFailure.STORAGE)
                try {
                    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: throw IOException("no output stream")
                } catch (e: IOException) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                ClipResult.Saved(SavedClip(uri.toString(), name, "${Environment.DIRECTORY_MOVIES}/$ALBUM", durationMs))
            } else {
                // Before Android 10 the shared Movies folder needs a storage permission: this app's
                // own Movies folder instead, shared through the FileProvider.
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), ALBUM).apply { mkdirs() }
                val target = File(dir, name)
                file.copyTo(target, overwrite = true)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
                ClipResult.Saved(SavedClip(uri.toString(), name, "${Environment.DIRECTORY_MOVIES}/$ALBUM", durationMs))
            }
        } catch (_: Exception) {
            ClipResult.Failed(ClipFailure.STORAGE)
        }
    }

    override val canShare: Boolean get() = true

    override fun share(clip: SavedClip, text: String): ClipShareOutcome = try {
        val uri = Uri.parse(clip.uri)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME_MP4)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(clip.name, uri)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ClipShareOutcome.OPENED
    } catch (_: Exception) {
        ClipShareOutcome.FAILED
    }

    override fun open(clip: SavedClip): Boolean = try {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(clip.uri), MIME_MP4)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val ALBUM = "TS IPTV"
        const val MIME_MP4 = "video/mp4"
        const val MIN_BYTES = 16 * 1024L
        const val TAP_POLL_MS = 250L

        /** The player reads a live stream continuously; nothing for this long means it stopped. */
        const val TAP_START_TIMEOUT_MS = 10_000L
        const val PROGRESS_POLL_MS = 500L
    }
}
