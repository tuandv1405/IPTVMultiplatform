package tss.t.tsiptv.player.network

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * F3 (spec §12, QC #19): side-loaded subtitle files are fetched **without** the stream's headers,
 * which may carry tokens and must not reach another host. Every other request (manifest, segments,
 * keys) goes to [withHeaders] as before.
 */
@OptIn(UnstableApi::class)
internal class SubtitleAwareDataSourceFactory(
    private val withHeaders: DataSource.Factory,
    private val plain: DataSource.Factory,
    private val subtitleUris: Set<String>,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = Routing()

    private inner class Routing : DataSource {
        private val listeners = ArrayList<TransferListener>()
        private var current: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = (if (dataSpec.uri.toString() in subtitleUris) plain else withHeaders).createDataSource()
            listeners.forEach(source::addTransferListener)
            current = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            current?.read(buffer, offset, length) ?: error("not opened")

        override fun getUri(): Uri? = current?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                current?.close()
            } finally {
                current = null
            }
        }
    }
}
