package tss.t.tsiptv.feature.clip

/**
 * HTTP for clips, with the stream's own headers on every request (playlist, key, segment). The
 * platform client is the one its player trusts streams with. Never logs URLs or header values.
 */
internal interface ClipHttp {
    /** The whole body of [url] (or [range] of it); throws [ClipException] on an error status. */
    suspend fun fetch(url: String, headers: Map<String, String>, range: HlsByteRange? = null): ClipHttpBody

    /** Reads [url]'s body as it arrives; [onChunk] gets each read and returns false to stop. */
    suspend fun stream(url: String, headers: Map<String, String>, onChunk: suspend (ByteArray, Int) -> Boolean)
}

/** @param url where the body came from, after redirects: relative playlist URIs resolve against it */
internal class ClipHttpBody(val bytes: ByteArray, val url: String)

/** AES-128 CBC with PKCS#7 padding, as `#EXT-X-KEY:METHOD=AES-128` segments are encrypted. */
internal fun interface Aes128Cbc {
    fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray
}

/** Where the recorded stream goes, in order (a temporary file before it is turned into MP4). */
internal fun interface ClipSink {
    suspend fun write(bytes: ByteArray, length: Int)
}

internal class ClipException(val failure: ClipFailure, message: String? = null) : Exception(message ?: failure.name)

/** The container of what was recorded, for the file the converter reads. */
internal enum class ClipContainer(val extension: String) {
    TS("ts"),

    /** fMP4 segments after their init section. */
    FMP4("mp4"),

    /** Anything else (FLV, Matroska, packed audio): the converter finds out. */
    OTHER("bin");

    companion object {
        fun sniff(bytes: ByteArray, length: Int = bytes.size): ClipContainer = when {
            length > 0 && bytes[0] == TS_SYNC && (length <= TS_PACKET || bytes[TS_PACKET] == TS_SYNC) -> TS
            length >= 8 && bytes.decodeToString(4, 8) in MP4_BOXES -> FMP4
            else -> OTHER
        }

        private const val TS_SYNC: Byte = 0x47
        private const val TS_PACKET = 188
        private val MP4_BOXES = setOf("ftyp", "styp", "moof", "sidx", "moov")
    }
}
