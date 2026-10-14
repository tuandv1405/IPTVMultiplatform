package tss.t.tsiptv.player.network

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.OutputStream

/**
 * Player › Clip for a live stream sent as one long HTTP response (MPEG-TS from most IPTV panels):
 * the bytes the player reads are copied to the recording, so the clip needs no second connection,
 * which a panel allowing one connection per account would refuse.
 */
internal object ClipTap {

    private class Recording(val uri: String, val out: OutputStream) {
        var bytes = 0L
        var failed = false
    }

    @Volatile
    private var recording: Recording? = null

    /** Copies what the player reads of [uri] to [out] from now on, until [stop]. */
    fun start(uri: String, out: OutputStream) {
        recording = Recording(uri, out)
    }

    /** Ends the recording; the bytes copied, or -1 when writing failed. [out] is not closed. */
    fun stop(): Long {
        val current = recording ?: return 0L
        recording = null
        return synchronized(current) { if (current.failed) -1L else current.bytes }
    }

    /** The bytes copied so far. */
    fun recordedBytes(): Long = recording?.let { synchronized(it) { it.bytes } } ?: 0L

    fun onRead(uri: String, buffer: ByteArray, offset: Int, length: Int) {
        val current = recording ?: return
        if (current.uri != uri || length <= 0) return
        synchronized(current) {
            if (current.failed || recording !== current) return
            try {
                current.out.write(buffer, offset, length)
                current.bytes += length
            } catch (_: IOException) {
                // Storage full: the recording fails, playback must not.
                current.failed = true
            }
        }
    }
}

/** Wraps the player's data sources so [ClipTap] sees what they read. */
@OptIn(UnstableApi::class)
internal class ClipTapDataSourceFactory(private val upstream: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = Tapped(upstream.createDataSource())

    private class Tapped(private val source: DataSource) : DataSource {
        private var uri: String? = null

        override fun addTransferListener(transferListener: TransferListener) = source.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri.toString()
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = source.read(buffer, offset, length)
            uri?.let { if (read > 0) ClipTap.onRead(it, buffer, offset, read) }
            return read
        }

        override fun getUri(): Uri? = source.uri

        override fun getResponseHeaders(): Map<String, List<String>> = source.responseHeaders

        override fun close() {
            uri = null
            source.close()
        }
    }
}
