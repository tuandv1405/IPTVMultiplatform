package tss.t.tsiptv.core.network

/**
 * Data class representing the progress of a download.
 *
 * @property bytesDownloaded The number of bytes downloaded
 * @property contentLength The total size of the file in bytes, or -1 if unknown
 * @property isCompleted Whether the download is completed
 * @property data The downloaded bytes, only available when isCompleted is true
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val contentLength: Long,
    val isCompleted: Boolean,
    val data: ByteArray? = null
) {
    /**
     * Returns the download progress as a percentage.
     * Returns -1 if the content length is unknown.
     */
    val progress: Float
        get() = if (contentLength > 0) {
            bytesDownloaded.toFloat() / contentLength.toFloat() * 100f
        } else {
            -1f
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DownloadProgress

        if (bytesDownloaded != other.bytesDownloaded) return false
        if (contentLength != other.contentLength) return false
        if (isCompleted != other.isCompleted) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytesDownloaded.hashCode()
        result = 31 * result + contentLength.hashCode()
        result = 31 * result + isCompleted.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}
