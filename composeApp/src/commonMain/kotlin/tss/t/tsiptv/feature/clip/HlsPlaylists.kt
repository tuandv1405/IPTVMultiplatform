package tss.t.tsiptv.feature.clip

/**
 * The parts of an HLS playlist (RFC 8216) a clip needs: the variants of a multivariant playlist,
 * and the segments of a media playlist with their keys, init sections and byte ranges.
 */
internal sealed interface HlsPlaylist

internal data class HlsMultivariant(val variants: List<HlsVariant>, val audios: List<HlsRendition>) : HlsPlaylist

/** @param height from `RESOLUTION`, null when absent (audio-only or not declared) */
internal data class HlsVariant(val url: String, val bandwidth: Long, val height: Int?, val audioGroup: String?)

/** An `EXT-X-MEDIA` entry; [url] null means its audio is inside the variant streams. */
internal data class HlsRendition(val type: String, val groupId: String, val url: String?)

internal data class HlsMediaPlaylist(
    val targetDurationMs: Long,
    val mediaSequence: Long,
    val segments: List<HlsSegment>,
    /** `EXT-X-ENDLIST`: a VOD, or a live event that is over. */
    val ended: Boolean,
) : HlsPlaylist

internal data class HlsSegment(
    val url: String,
    val durationMs: Long,
    val sequence: Long,
    val range: HlsByteRange?,
    val key: HlsKey?,
    val init: HlsInit?,
    /** `EXT-X-DISCONTINUITY` before this segment: timestamps or format change here. */
    val discontinuity: Boolean,
)

internal data class HlsByteRange(val offset: Long, val length: Long)

/** `EXT-X-KEY`; [method] is `AES-128` or `SAMPLE-AES` (and its variants). */
internal data class HlsKey(val method: String, val url: String?, val iv: ByteArray?) {
    override fun equals(other: Any?): Boolean =
        other is HlsKey && method == other.method && url == other.url && iv.contentEqualsNullable(other.iv)

    override fun hashCode(): Int = (method.hashCode() * 31 + url.hashCode()) * 31 + (iv?.contentHashCode() ?: 0)
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && contentEquals(other)

/**
 * `EXT-X-MAP`: the init section of fMP4 segments.
 *
 * @param key the `EXT-X-KEY` in effect where the map is declared (it encrypts the section too)
 */
internal data class HlsInit(val url: String, val range: HlsByteRange?, val key: HlsKey?)

internal object HlsParser {

    /** The playlist in [text], with URLs resolved against [baseUrl]; null when it is not one. */
    fun parse(text: String, baseUrl: String): HlsPlaylist? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull()?.removePrefix("﻿") != "#EXTM3U") return null
        return if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) parseMultivariant(lines, baseUrl)
        else parseMedia(lines, baseUrl)
    }

    private fun parseMultivariant(lines: List<String>, baseUrl: String): HlsMultivariant {
        val variants = ArrayList<HlsVariant>()
        val audios = ArrayList<HlsRendition>()
        var pending: Map<String, String>? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> pending = attributes(line.substringAfter(':'))
                line.startsWith("#EXT-X-MEDIA:") -> {
                    val a = attributes(line.substringAfter(':'))
                    val type = a["TYPE"] ?: continue
                    audios += HlsRendition(type, a["GROUP-ID"].orEmpty(), a["URI"]?.let { resolveUrl(baseUrl, it) })
                }
                line.startsWith("#") -> Unit
                else -> pending?.let { a ->
                    variants += HlsVariant(
                        url = resolveUrl(baseUrl, line),
                        bandwidth = a["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                        height = a["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull(),
                        audioGroup = a["AUDIO"],
                    )
                    pending = null
                }
            }
        }
        return HlsMultivariant(variants, audios.filter { it.type == "AUDIO" })
    }

    private fun parseMedia(lines: List<String>, baseUrl: String): HlsMediaPlaylist {
        var targetMs = 0L
        var mediaSequence = 0L
        var ended = false
        val segments = ArrayList<HlsSegment>()
        var duration: Long? = null
        var range: HlsByteRange? = null
        var key: HlsKey? = null
        var init: HlsInit? = null
        var discontinuity = false
        // A byte range without an offset starts where the previous one of the same file ended.
        var lastRangeEnd = 0L
        var lastRangeUrl: String? = null
        var rangeSpec: String? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-TARGETDURATION:") ->
                    targetMs = ((line.substringAfter(':').toDoubleOrNull() ?: 0.0) * 1000).toLong()
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    mediaSequence = line.substringAfter(':').toLongOrNull() ?: 0L
                line.startsWith("#EXTINF:") ->
                    duration = ((line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0) * 1000).toLong()
                line.startsWith("#EXT-X-BYTERANGE:") -> rangeSpec = line.substringAfter(':')
                line.startsWith("#EXT-X-KEY:") -> {
                    val a = attributes(line.substringAfter(':'))
                    val method = a["METHOD"] ?: "NONE"
                    key = if (method == "NONE") null else HlsKey(
                        method = method,
                        url = a["URI"]?.let { resolveUrl(baseUrl, it) },
                        iv = a["IV"]?.let(::hexToBytes),
                    )
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val a = attributes(line.substringAfter(':'))
                    init = a["URI"]?.let { uri ->
                        HlsInit(resolveUrl(baseUrl, uri), a["BYTERANGE"]?.let { parseRange(it, 0L) }, key)
                    }
                }
                line == "#EXT-X-DISCONTINUITY" -> discontinuity = true
                line == "#EXT-X-ENDLIST" -> ended = true
                line.startsWith("#") -> Unit
                else -> {
                    val url = resolveUrl(baseUrl, line)
                    rangeSpec?.let { spec ->
                        val start = if (url == lastRangeUrl) lastRangeEnd else 0L
                        range = parseRange(spec, start)
                    }
                    range?.let { lastRangeEnd = it.offset + it.length; lastRangeUrl = url }
                    segments += HlsSegment(
                        url = url,
                        durationMs = duration ?: 0L,
                        sequence = mediaSequence + segments.size,
                        range = range,
                        key = key,
                        init = init,
                        discontinuity = discontinuity,
                    )
                    duration = null
                    range = null
                    rangeSpec = null
                    discontinuity = false
                }
            }
        }
        return HlsMediaPlaylist(targetMs, mediaSequence, segments, ended)
    }

    /** `<length>[@<offset>]`; without an offset the range starts at [defaultStart]. */
    private fun parseRange(spec: String, defaultStart: Long): HlsByteRange? {
        val length = spec.substringBefore('@').trim().toLongOrNull() ?: return null
        val offset = if ('@' in spec) spec.substringAfter('@').trim().toLongOrNull() ?: return null else defaultStart
        return HlsByteRange(offset, length)
    }

    /** `KEY=VALUE,KEY="quoted, value"` attribute lists. */
    internal fun attributes(list: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < list.length) {
            val eq = list.indexOf('=', i)
            if (eq < 0) break
            val name = list.substring(i, eq).trim().trimStart(',').trim()
            var j = eq + 1
            val value: String
            if (j < list.length && list[j] == '"') {
                val close = list.indexOf('"', j + 1).let { if (it < 0) list.length else it }
                value = list.substring(j + 1, close)
                j = close + 1
            } else {
                val comma = list.indexOf(',', j).let { if (it < 0) list.length else it }
                value = list.substring(j, comma).trim()
                j = comma
            }
            out[name] = value
            i = if (j < list.length && list[j] == ',') j + 1 else j
        }
        return out
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val digits = hex.removePrefix("0x").removePrefix("0X")
        if (digits.isEmpty() || digits.length > 32 || digits.any { it.digitToIntOrNull(16) == null }) return null
        // Left-padded to 16 bytes: "0x1" is IV 00…01.
        val padded = digits.padStart(32, '0')
        return ByteArray(16) { padded.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

/** [ref] resolved against [base] (RFC 3986 §5.2, enough for playlist URIs). */
internal fun resolveUrl(base: String, ref: String): String {
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(ref)) return ref
    val scheme = base.substringBefore("://", "")
    val afterScheme = base.substringAfter("://")
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val origin = "$scheme://$authority"
    if (ref.startsWith("//")) return "$scheme:$ref"
    val basePath = afterScheme.removePrefix(authority).substringBefore('#')
    val path = when {
        ref.startsWith("/") -> ref
        ref.startsWith("?") -> basePath.substringBefore('?') + ref
        else -> basePath.substringBefore('?').substringBeforeLast('/', "") + "/" + ref
    }
    return origin + removeDotSegments(path)
}

private fun removeDotSegments(path: String): String {
    val query = path.indexOf('?').let { if (it < 0) "" else path.substring(it) }
    val segments = path.removeSuffix(query).split('/')
    val out = ArrayList<String>()
    segments.forEachIndexed { index, segment ->
        when (segment) {
            "." -> if (index == segments.lastIndex) out += ""
            ".." -> {
                if (out.size > 1) out.removeAt(out.lastIndex)
                if (index == segments.lastIndex) out += ""
            }
            else -> out += segment
        }
    }
    return out.joinToString("/").let { if (it.startsWith("/")) it else "/$it" } + query
}
