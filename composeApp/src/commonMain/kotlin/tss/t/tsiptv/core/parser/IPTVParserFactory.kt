package tss.t.tsiptv.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okio.Buffer
import okio.GzipSource
import okio.buffer
import okio.use
import tss.t.tsiptv.core.parser.iptv.iptvorg.IptvOrgParser
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.iptv.m3u.PlainUrlListParser
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.strm.StrmParser

/**
 * Factory for creating IPTV parsers.
 */
object IPTVParserFactory {
    /**
     * Creates an IPTV parser for the specified format.
     *
     * @param format The format to create a parser for
     * @return The created parser
     * @throws IllegalArgumentException if the format is not a list of channels TS IPTV can read
     */
    fun createParser(format: IPTVFormat): IPTVParser {
        return when (format) {
            IPTVFormat.M3U -> M3UParser()
            IPTVFormat.XML -> XMLParser()
            IPTVFormat.JSON -> JSONParser()
            IPTVFormat.XSPF -> XSPFParser()
            IPTVFormat.JSON_IPTV_ORG -> IptvOrgParser()
            IPTVFormat.M3U_PLAIN -> PlainUrlListParser()
            IPTVFormat.STRM -> StrmParser("Stream")

            IPTVFormat.HLS_MANIFEST,
            IPTVFormat.TSIPTV_SOURCE,
            IPTVFormat.HTML,
            IPTVFormat.UNKNOWN -> throw IllegalArgumentException("No channel list parser for $format")
        }
    }

    /**
     * Detects the format of an IPTV playlist from its content.
     *
     * The order matters: an HLS manifest is also `#EXTM3U`, and a JSON array may be iptv-org's
     * `streams.json` or a plain channel array. Content that matches nothing is [IPTVFormat.UNKNOWN]
     * rather than a guess at M3U, which used to end in a confusing "missing #EXTM3U".
     *
     * @param content The playlist content as a string
     * @return The detected format
     */
    fun detectFormat(content: String): IPTVFormat {
        val trimmed = content.trimStart('﻿').trimStart()
        return when {
            trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
                    trimmed.startsWith("<html", ignoreCase = true) -> IPTVFormat.HTML

            // XHTML pages start with an XML declaration.
            trimmed.startsWith("<?xml") && XHTML_START.containsMatchIn(trimmed.take(2048)) -> IPTVFormat.HTML

            trimmed.startsWith("{") && TSIPTV_SOURCE_MARKER.containsMatchIn(
                trimmed.take(TSIPTV_SOURCE_SCAN_CHARS)
            ) -> IPTVFormat.TSIPTV_SOURCE

            // Kodi lists may open with #KODIPROP lines or a comment before #EXTM3U / #EXTINF.
            // HLS tags count only at the start of a line: a channel name may mention them.
            trimmed.startsWith("#") && M3U_DIRECTIVE.containsMatchIn(trimmed) ->
                if (HLS_TAG_LINE.containsMatchIn(trimmed)) IPTVFormat.HLS_MANIFEST else IPTVFormat.M3U

            // The XML declaration is optional; files often start straight at <playlist>.
            (trimmed.startsWith("<?xml") || trimmed.startsWith("<playlist")) && trimmed.contains("<playlist") &&
                    (trimmed.contains("xmlns=\"http://xspf.org/ns/0/\"") ||
                            trimmed.contains("xmlns:vlc=\"http://www.videolan.org/vlc/playlist/ns/0/\"")) -> IPTVFormat.XSPF

            trimmed.startsWith("<?xml") || trimmed.startsWith("<tv") -> IPTVFormat.XML

            trimmed.startsWith("[") && isIptvOrgStreams(trimmed) -> IPTVFormat.JSON_IPTV_ORG

            trimmed.startsWith("{") || trimmed.startsWith("[") -> IPTVFormat.JSON

            else -> detectUrlList(trimmed)
        }
    }

    /**
     * Creates an IPTV parser for the content.
     *
     * @param content The playlist content as a string
     * @return The created parser
     * @throws IllegalArgumentException if the format is not supported
     */
    fun createParserForContent(content: String): IPTVParser {
        val format = detectFormat(content)
        return createParser(format)
    }

    /**
     * Turns downloaded or picked bytes into text: gunzips when the bytes carry the gzip magic
     * number (servers often send `.m3u.gz` without `Content-Encoding`), and drops a UTF-8 BOM.
     */
    fun decode(bytes: ByteArray): String {
        val plain = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GzipSource(Buffer().write(bytes)).buffer().use { it.readByteArray() }
        } else {
            bytes
        }
        return plain.decodeToString().removePrefix("﻿")
    }

    private const val TSIPTV_SOURCE_SCAN_CHARS = 64 * 1024
    private val TSIPTV_SOURCE_MARKER = Regex("\"format\"\\s*:\\s*\"tsiptv-source\"")
    private val M3U_DIRECTIVE = Regex("^\\s*#EXT(M3U|INF)", RegexOption.MULTILINE)
    private val HLS_TAG_LINE =
        Regex("^\\s*#EXT-X-(TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE)", RegexOption.MULTILINE)
    private val XHTML_START = Regex("<(!DOCTYPE\\s+)?html", RegexOption.IGNORE_CASE)
    private val URL_LINE = Regex("^[A-Za-z][A-Za-z0-9+.-]*://\\S.*$")
    private val json = Json { isLenient = true }

    /** iptv-org's streams.json: objects with `channel`, `url` and `title`, and no `name`. */
    private fun isIptvOrgStreams(text: String): Boolean {
        val first = runCatching { (json.parseToJsonElement(text) as? JsonArray)?.firstOrNull() }
            .getOrNull() as? JsonObject ?: return false
        return "channel" in first && "url" in first && "title" in first && "name" !in first
    }

    /** `.strm` (one URL) or a bare URL list (two or more), both without `#EXTINF`. */
    private fun detectUrlList(text: String): IPTVFormat {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val urls = lines.filterNot { it.startsWith("#") }
        if (urls.isEmpty() || !urls.all { URL_LINE.matches(it) }) return IPTVFormat.UNKNOWN
        return if (urls.size == 1) IPTVFormat.STRM else IPTVFormat.M3U_PLAIN
    }
}
