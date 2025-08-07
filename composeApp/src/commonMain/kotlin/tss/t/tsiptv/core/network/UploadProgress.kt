package tss.t.tsiptv.core.network

/**
 * Data class representing the progress of an upload.
 *
 * @property bytesUploaded The number of bytes uploaded
 * @property contentLength The total size of the file in bytes
 * @property isCompleted Whether the upload is completed
 * @property response The response from the server, only available when isCompleted is true
 */
data class UploadProgress(
    val bytesUploaded: Long,
    val contentLength: Long,
    val isCompleted: Boolean,
    val response: String? = null
) {
    /**
     * Returns the upload progress as a percentage.
     */
    val progress: Float
        get() = bytesUploaded.toFloat() / contentLength.toFloat() * 100f
}