package tss.t.tsiptv.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/** One decoded video frame. [aspect] is the display aspect ratio (pixel aspect applied). */
class VideoFrame(val image: ImageBitmap, val aspect: Float)

/**
 * Receives libVLC's decoded frames in memory and publishes them as Compose images, so the video is
 * drawn by Compose like any other content. The heavyweight AWT canvas used before sat on top of the
 * window: it hid the player controls and swallowed every click, so playback could not be paused or
 * left.
 *
 * libVLC calls [display] on its own thread; the copy into [bytes] happens there, the Skia upload on
 * the UI thread. When the UI thread falls behind, frames are dropped rather than queued.
 */
internal class VideoFrameSink : BufferFormatCallback, RenderCallback {

    private val lock = Any()
    private var info: ImageInfo? = null
    private var bytes = ByteArray(0)
    private var dirty = false
    private val publishQueued = AtomicBoolean(false)

    @Volatile
    private var aspect = 0f

    // UI thread only. Its pixels are replaced in place for each frame.
    private val bitmap = Bitmap()

    private val _frame = MutableStateFlow<VideoFrame?>(null)
    val frame: StateFlow<VideoFrame?> = _frame.asStateFlow()

    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
        synchronized(lock) {
            // RV32 is B, G, R, X in memory: Skia's BGRA_8888.
            info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            bytes = ByteArray(sourceWidth * sourceHeight * 4)
            dirty = false
        }
        aspect = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight else 0f
        return RV32BufferFormat(sourceWidth, sourceHeight)
    }

    override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {
        if (displayWidth > 0 && displayHeight > 0) aspect = displayWidth.toFloat() / displayHeight
    }

    override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit

    override fun lock(mediaPlayer: MediaPlayer) = Unit

    override fun unlock(mediaPlayer: MediaPlayer) = Unit

    override fun display(
        mediaPlayer: MediaPlayer,
        nativeBuffers: Array<out ByteBuffer>,
        bufferFormat: BufferFormat,
        displayWidth: Int,
        displayHeight: Int,
    ) {
        val source = nativeBuffers.firstOrNull()?.duplicate() ?: return
        synchronized(lock) {
            if (info == null) return
            source.rewind()
            source.get(bytes, 0, minOf(bytes.size, source.remaining()))
            dirty = true
        }
        if (displayWidth > 0 && displayHeight > 0) aspect = displayWidth.toFloat() / displayHeight
        if (publishQueued.compareAndSet(false, true)) SwingUtilities.invokeLater(::publish)
    }

    private fun publish() {
        // Cleared first: a frame arriving during this upload queues the next one.
        publishQueued.set(false)
        synchronized(lock) {
            val info = info ?: return
            if (!dirty) return
            bitmap.installPixels(info, bytes, info.minRowBytes)
            dirty = false
            // Under the lock, so a clear() cannot land between the check and this.
            _frame.value = VideoFrame(bitmap.asComposeImageBitmap(), aspect)
        }
    }

    /**
     * A copy of the picture on screen (share card), or null when none is shown. The published
     * [frame] cannot be kept: its pixels are replaced in place by the next frame.
     */
    fun snapshot(): VideoSnapshot? {
        synchronized(lock) {
            val info = info ?: return null
            if (_frame.value == null) return null
            val copy = Bitmap()
            if (!copy.installPixels(info, bytes.copyOf(), info.minRowBytes)) return null
            copy.setImmutable()
            return VideoSnapshot(copy.asComposeImageBitmap(), aspect)
        }
    }

    /**
     * Drops the current picture, so a new channel does not open on the last frame of the old one.
     * Call it once libVLC has stopped the old stream (after `media().prepare()` or `stop()`), or a
     * late frame of that stream shows again.
     */
    fun clear() {
        synchronized(lock) {
            dirty = false
            _frame.value = null
        }
    }
}
