package tss.t.tsiptv.feature.clip

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tss.t.tsiptv.player.models.MediaItem
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import uk.co.caprica.vlcj.factory.MediaPlayerFactory as VlcFactory

/**
 * Desktop clips: HLS and live streams are recorded by the app ([HlsClipDownloader],
 * [recordLiveStream]), then libVLC's stream output rewrites the recording as MP4 without
 * re-encoding, in a second, headless player of the same factory. A file on a server (VOD MP4 or
 * MKV) is cut by libVLC directly. Clips go to `Videos/TS IPTV` (`Movies/TS IPTV` on macOS).
 *
 * @param headerOptions the VLC options that send the item's headers, as playback does
 */
internal class VlcClipRecorder(
    private val factory: VlcFactory,
    private val headerOptions: (MediaItem) -> Array<String>,
    private val http: ClipHttp = DesktopClipHttp(),
    private val folder: File = defaultFolder(),
) : ClipRecorder {

    override suspend fun record(item: MediaItem, live: Boolean, fromMs: Long, session: ClipSession): ClipResult {
        Clips.check(item)?.let { return ClipResult.Failed(it) }
        if (looksLikeDash(item.uri, item.mimeType)) return ClipResult.Failed(ClipFailure.UNSUPPORTED)
        val work = withContext(Dispatchers.IO) { Files.createTempDirectory("tsiptv-clip").toFile() }
        return try {
            val output = File(work, "clip.mp4")
            val durationMs = if (!live && !looksLikeHls(item.uri, item.mimeType)) {
                // A file on a server: libVLC reads only the piece it needs.
                session.finishing()
                val ok = remux(
                    item.uri, output,
                    *headerOptions(item),
                    ":start-time=${seconds(fromMs)}",
                    ":stop-time=${seconds(fromMs + session.lengthMs)}",
                )
                if (!ok) return ClipResult.Failed(ClipFailure.CONVERT)
                session.lengthMs
            } else {
                val recorded = File(work, "recorded")
                val (recordedMs, container) = record(item, live, fromMs, session, recorded)
                if (recordedMs < Clips.MIN_MS) return ClipResult.Failed(ClipFailure.TOO_SHORT)
                session.finishing()
                val input = File(work, "recorded.${container.extension}").also { recorded.renameTo(it) }
                val clipMs = recordedMs.coerceAtMost(session.lengthMs)
                if (!remux(input.absolutePath, output, ":stop-time=${seconds(clipMs)}")) {
                    return ClipResult.Failed(ClipFailure.CONVERT)
                }
                clipMs
            }
            if (output.length() < MIN_BYTES) return ClipResult.Failed(ClipFailure.CONVERT)
            save(output, item.title, durationMs)
        } catch (e: ClipException) {
            ClipResult.Failed(e.failure)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    /** Records the stream into [file]: its HLS segments, or its bytes for a plain live stream. */
    private suspend fun record(item: MediaItem, live: Boolean, fromMs: Long, session: ClipSession, file: File): Pair<Long, ClipContainer> {
        val out = withContext(Dispatchers.IO) { BufferedOutputStream(file.outputStream()) }
        try {
            val sink = ClipSink { bytes, length -> withContext(Dispatchers.IO) { out.write(bytes, 0, length) } }
            val hls = HlsClipDownloader(http, JvmAes128Cbc, item.headers)
            if (!looksLikeHls(item.uri, item.mimeType)) {
                when (val stream = recordLiveStream(http, item.uri, item.headers, session, sink)) {
                    is StreamRecording.Recorded -> return stream.recordedMs to stream.container
                    // A playlist behind a URL that does not say so: nothing was written yet.
                    StreamRecording.IsPlaylist -> Unit
                }
            }
            val recording = hls.record(item.uri, live, fromMs, session, sink)
            return recording.recordedMs to recording.container
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { out.close() }
        }
    }

    /** Writes [input] (or the piece [options] select) to [output] as MP4, without re-encoding. */
    private suspend fun remux(input: String, output: File, vararg options: String): Boolean {
        val player = factory.mediaPlayers().newMediaPlayer()
        val done = CompletableDeferred<Boolean>()
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer) {
                done.complete(true)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                done.complete(true)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                done.complete(false)
            }
        })
        try {
            // Inside the quotes VLC reads a backslash as an escape: forward slashes, quotes escaped.
            val dst = output.absolutePath.replace('\\', '/').replace("'", "\\'")
            val started = player.media().play(
                input,
                ":sout=#std{access=file,mux=mp4,dst='$dst'}",
                // Subtitles and teletext have no place in the MP4 muxer.
                ":no-sout-spu",
                *options,
            )
            if (!started) return false
            return withTimeoutOrNull(REMUX_TIMEOUT_MS) { done.await() } ?: false
        } finally {
            // Never on libVLC's event thread, and also when the clip is cancelled.
            withContext(NonCancellable + Dispatchers.IO) {
                player.controls().stop()
                player.release()
            }
        }
    }

    private suspend fun save(file: File, title: String, durationMs: Long): ClipResult = withContext(Dispatchers.IO) {
        try {
            folder.mkdirs()
            val name = Clips.fileName(title, System.currentTimeMillis())
            val target = File(folder, name)
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            ClipResult.Saved(SavedClip(target.absolutePath, name, "${folder.parentFile.name}${File.separator}${folder.name}", durationMs))
        } catch (_: IOException) {
            ClipResult.Failed(ClipFailure.STORAGE)
        }
    }

    override val canShare: Boolean get() = true

    /** No share sheet on desktop: the video goes on the clipboard, to paste into a chat app. */
    override fun share(clip: SavedClip, text: String): ClipShareOutcome = try {
        val files = listOf(File(clip.uri))
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
                override fun getTransferData(flavor: DataFlavor): Any =
                    if (flavor == DataFlavor.javaFileListFlavor) files else throw UnsupportedFlavorException(flavor)
            },
            null,
        )
        ClipShareOutcome.COPIED
    } catch (_: Exception) {
        ClipShareOutcome.FAILED
    }

    override fun open(clip: SavedClip): Boolean = runCatching {
        Desktop.getDesktop().open(File(clip.uri))
    }.isSuccess

    private fun seconds(ms: Long) = String.format(Locale.ROOT, "%.3f", ms / 1000.0)

    companion object {
        /** A minute of video is remuxed in well under this; a stuck libVLC must not keep the clip open. */
        private const val REMUX_TIMEOUT_MS = 120_000L

        /** An MP4 with no samples in it (the input had nothing libVLC could mux). */
        private const val MIN_BYTES = 16 * 1024L

        fun defaultFolder(): File {
            val home = File(System.getProperty("user.home"))
            val mac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
            return File(File(home, if (mac) "Movies" else "Videos"), "TS IPTV")
        }
    }
}
