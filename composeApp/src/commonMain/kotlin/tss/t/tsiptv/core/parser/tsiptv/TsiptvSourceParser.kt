package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okio.Buffer
import okio.GzipSource
import okio.buffer
import okio.use
import tss.t.tsiptv.core.parser.model.playback.CatchupMode
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.ClearKey
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import kotlin.math.floor

/** Outcome of [TsiptvSourceParser.parse]. */
sealed interface TsiptvParseResult {
    val report: TsiptvValidationReport

    /** The document is usable; [report] lists item errors and warnings (never document errors). */
    data class Success(
        val document: TsiptvSourceDocument,
        override val report: TsiptvValidationReport,
    ) : TsiptvParseResult

    /**
     * A document error (spec §10): nothing may be stored. [report] has at least one document error.
     *
     * @property cause Why: for an included document, [TsiptvFailureCause.isFetchFailure] causes are
     *   fetch failures (§9.3: keep the last good copy), the others a rejection (drop the include)
     */
    data class Failure(
        override val report: TsiptvValidationReport,
        val cause: TsiptvFailureCause = TsiptvFailureCause.INVALID_DOCUMENT,
    ) : TsiptvParseResult

    val documentOrNull: TsiptvSourceDocument? get() = (this as? Success)?.document
}

/** Why a document was rejected. */
enum class TsiptvFailureCause(val isFetchFailure: Boolean) {
    /** The content is not a valid TS IPTV Source (any document code except the two below). */
    INVALID_DOCUMENT(false),

    /** Over the size cap after decompression (`E_TOO_LARGE`). */
    TOO_LARGE(true),

    /** A gzip body that cannot be decompressed (reported as `E_NOT_JSON`, the closest §10 code). */
    CORRUPT_COMPRESSION(true),
}

/**
 * Reads and validates one TS IPTV Source document (docs/tsiptv-source-format.md, version 1).
 *
 * Validation is hand-written per spec §10 (the JSON Schema is stricter and not used at runtime):
 * document errors reject the file, item errors drop the offending item, stream, field or include,
 * warnings only report. Unknown members are never read (§11.3), so they are ignored at every level.
 *
 * The parser does no network access. Includes are validated as declarations only; fetching and
 * the tree rules across documents belong to [TsiptvIncludeGuard] / step 2.
 */
object TsiptvSourceParser {
    const val FORMAT = "tsiptv-source"

    /** The highest `version` this reader supports (§11.1). */
    const val SUPPORTED_VERSION = 1

    /** RFC 8259 only: no comments, no trailing commas, no unquoted keys. */
    private val json = Json

    private const val DETECTION_SCAN_CHARS = 64 * 1024
    private val DETECTION_MARKER = Regex("\"format\"\\s*:\\s*\"tsiptv-source\"")

    /**
     * Content sniffing of §3: an object whose first 64 KiB contain `"format": "tsiptv-source"`.
     * (`IPTVParserFactory.detectFormat` already applies the same rule before its JSON branch.)
     */
    fun looksLikeSource(content: String): Boolean {
        val trimmed = content.removePrefix("﻿").trimStart()
        return trimmed.startsWith("{") && DETECTION_MARKER.containsMatchIn(trimmed.take(DETECTION_SCAN_CHARS))
    }

    /**
     * Parses downloaded or picked bytes: gunzips a body that starts with `1F 8B` (§2), enforces
     * [maxBytes] after decompression (reading stops as soon as it is exceeded, so a gzip bomb is
     * never inflated fully), strips a UTF-8 BOM.
     *
     * @param documentUrl Where the document was fetched from, if known; an include pointing back
     *   to it is reported as `E_INCLUDE_CYCLE`
     */
    fun parse(
        bytes: ByteArray,
        documentUrl: String? = null,
        maxBytes: Long = TsiptvLimits.MAX_DOCUMENT_BYTES,
    ): TsiptvParseResult {
        val plain = try {
            decodeBounded(bytes, maxBytes)
        } catch (e: Exception) {
            return failure(TsiptvIssueCode.E_NOT_JSON, "", "The gzip data is corrupt.", TsiptvFailureCause.CORRUPT_COMPRESSION)
        } ?: return tooLarge(maxBytes)
        return parseText(plain.decodeToString(), documentUrl)
    }

    /** Parses text that is already decoded. The 5 MiB limit is applied to its UTF-8 size. */
    fun parse(
        text: String,
        documentUrl: String? = null,
        maxBytes: Long = TsiptvLimits.MAX_DOCUMENT_BYTES,
    ): TsiptvParseResult {
        if (utf8Size(text, maxBytes) > maxBytes) return tooLarge(maxBytes)
        return parseText(text, documentUrl)
    }

    private fun parseText(text: String, documentUrl: String?): TsiptvParseResult {
        val root = try {
            json.parseToJsonElement(text.removePrefix("﻿"))
        } catch (e: Exception) {
            return failure(TsiptvIssueCode.E_NOT_JSON, "", "The file is not valid JSON.")
        }
        return parseElement(root, documentUrl)
    }

    /** Validates an already parsed JSON tree (size limits are the caller's business here). */
    fun parseElement(root: JsonElement, documentUrl: String? = null): TsiptvParseResult =
        DocumentReader(documentUrl).read(root)

    private fun tooLarge(maxBytes: Long) = failure(
        TsiptvIssueCode.E_TOO_LARGE, "",
        "The document is larger than ${maxBytes / (1024 * 1024)} MiB after decompression.",
        TsiptvFailureCause.TOO_LARGE,
    )

    private fun failure(
        code: TsiptvIssueCode,
        path: String,
        message: String,
        cause: TsiptvFailureCause = TsiptvFailureCause.INVALID_DOCUMENT,
    ) = TsiptvParseResult.Failure(TsiptvValidationReport(listOf(TsiptvIssue(code, path, message))), cause)

    /** null when the decompressed size exceeds [maxBytes]. */
    internal fun decodeBounded(bytes: ByteArray, maxBytes: Long): ByteArray? {
        val gzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        if (!gzip) return if (bytes.size > maxBytes) null else bytes
        val sink = Buffer()
        GzipSource(Buffer().write(bytes)).buffer().use { source ->
            while (true) {
                if (source.read(sink, 64L * 1024) == -1L) break
                if (sink.size > maxBytes) return null
            }
        }
        return sink.readByteArray()
    }

    /** UTF-8 length of [text], counting stops once it exceeds [stopAfter]. */
    internal fun utf8Size(text: String, stopAfter: Long = Long.MAX_VALUE): Long {
        var size = 0L
        var i = 0
        while (i < text.length) {
            val c = text[i]
            size += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> { i++; 4 }
                else -> 3
            }
            if (size > stopAfter) return size
            i++
        }
        return size
    }
}

// --- JSON access helpers ----------------------------------------------------------------------

/** Spec §4 trimming: ECMA-262 whitespace, matching the schema's \S (not Kotlin's isWhitespace). */
private fun String.trimJs(): String = TsiptvRules.trimEcma(this)

private fun JsonElement?.isAbsent(): Boolean = this == null || this is JsonNull

private fun JsonElement?.asStr(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

private fun JsonElement?.asNum(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p is JsonNull) return null
    return p.doubleOrNull?.takeIf { it.isFinite() }
}

/** A JSON integer; `3.0` counts (as in JSON Schema), `3.5`, `"3"` and `true` do not. */
private fun JsonElement?.asInt(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p is JsonNull) return null
    p.longOrNull?.let { return it }
    val d = p.doubleOrNull ?: return null
    return if (d.isFinite() && d == floor(d) && kotlin.math.abs(d) < 1e15) d.toLong() else null
}

private fun JsonElement?.asBool(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p is JsonNull) return null
    return p.booleanOrNull
}

// `x-` members (§4) need no code: the reader only ever looks up the members it knows.

/** Result of reading a `drm` object. */
private sealed interface DrmRead {
    data object Absent : DrmRead
    data object Invalid : DrmRead
    data class Valid(val spec: DrmSpec) : DrmRead
}

/**
 * One validation run over one document. Not thread-safe; one instance per document.
 */
private class DocumentReader(private val documentUrl: String?) {
    private val issues = ArrayList<TsiptvIssue>()

    /** `meta.language`: language of plain strings and fallback of localized objects. */
    private var language = LocalizedTextResolver.FALLBACK_LANGUAGE

    /** Every id already taken by a channel, movie, series, episode or include (§5.4). */
    private val ids = HashSet<String>()

    private var episodeCount = 0
    private var episodesOverLimit = 0
    private var seriesDroppedByEpisodeLimit = 0

    private fun add(code: TsiptvIssueCode, path: String, message: String, dropped: Int = 0) {
        issues += TsiptvIssue(code, path, message, dropped)
    }

    private fun report() = TsiptvValidationReport(issues.toList())

    fun read(root: JsonElement): TsiptvParseResult {
        val obj = root.asObj() ?: return fail(TsiptvIssueCode.E_NOT_JSON, "", "The root of the document is not a JSON object.")

        if (obj["format"].asStr() != TsiptvSourceParser.FORMAT) {
            return fail(TsiptvIssueCode.E_NOT_SOURCE, "format", "\"format\" must be \"tsiptv-source\".")
        }
        val version = obj["version"].asInt()
        if (version == null || version < 1 || version > TsiptvSourceParser.SUPPORTED_VERSION) {
            // Integers too large for a Long (1e20) are still integers: a newer major version.
            val raw = (obj["version"] as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.content
            val hugeInteger = version == null && raw != null && HUGE_INTEGER.matches(raw)
            val message = when {
                obj["version"].isAbsent() -> "\"version\" is missing."
                hugeInteger && !raw!!.startsWith("-") ->
                    "This version needs a newer app (this one reads version ${TsiptvSourceParser.SUPPORTED_VERSION})."
                hugeInteger -> "This version does not exist."
                version == null -> "\"version\" must be an integer."
                version > TsiptvSourceParser.SUPPORTED_VERSION ->
                    "Version $version needs a newer app (this one reads version ${TsiptvSourceParser.SUPPORTED_VERSION})."
                else -> "Version $version does not exist."
            }
            return fail(TsiptvIssueCode.E_VERSION, "version", message)
        }

        val id = obj["id"].asStr()
        if (id == null || !TsiptvRules.isId(id)) {
            add(
                TsiptvIssueCode.E_ID, "id",
                if (obj["id"].isAbsent()) "The document id is missing." else "The document id is invalid (letters, digits, '.', '_', '-'; 1–128 characters)."
            )
        }
        val meta = meta(obj["meta"])
        if (issues.any { it.level == TsiptvIssueLevel.DOCUMENT }) return TsiptvParseResult.Failure(report())

        val revision = obj["revision"].let { el ->
            if (el.isAbsent()) 0 else el.asInt()?.takeIf { it in 0..Int.MAX_VALUE }?.toInt() ?: run {
                add(TsiptvIssueCode.W_FIELD, "revision", "\"revision\" must be an integer ≥ 0; 0 is used.")
                0
            }
        }

        val channels = collection(obj["channels"], "channels", TsiptvLimits.MAX_CHANNELS, ::channel)
        val movies = collection(obj["movies"], "movies", TsiptvLimits.MAX_MOVIES, ::movie)
        val series = collection(obj["series"], "series", TsiptvLimits.MAX_SERIES, ::series)
        if (episodesOverLimit > 0) {
            add(
                TsiptvIssueCode.W_LIMIT, "series",
                "More than ${TsiptvLimits.MAX_EPISODES_PER_DOCUMENT} episodes; $episodesOverLimit episodes were dropped.",
                episodesOverLimit + seriesDroppedByEpisodeLimit,
            )
        }
        val includes = collection(obj["includes"], "includes", TsiptvLimits.MAX_INCLUDES_PER_DOCUMENT, ::include)
        val epg = epgLinks(obj["epg"])

        val appearance = appearance(obj["appearance"])
        if (appearance != null) issues += TsiptvAppearanceResolver.resolve(appearance).second

        val includeTypes = LinkedHashMap<String, TsiptvIncludeType>()
        epg.forEach { includeTypes["epg-${it.index}"] = TsiptvIncludeType.XMLTV }
        includes.forEach { includeTypes[it.id] = it.type }
        val layout = layout(obj["layout"], includeTypes)

        if (channels.isEmpty() && movies.isEmpty() && series.isEmpty() && includes.isEmpty()) {
            val declared = listOf("channels", "movies", "series", "includes").any { (obj[it].asArr()?.size ?: 0) > 0 }
            add(
                TsiptvIssueCode.E_EMPTY, "",
                if (declared) "Every item and include was dropped; nothing is left to show."
                else "The document has no channels, movies, series or includes."
            )
            return TsiptvParseResult.Failure(report())
        }

        val document = TsiptvSourceDocument(
            id = id!!,
            version = version.toInt(),
            revision = revision,
            meta = meta!!,
            appearance = appearance,
            layout = layout,
            channels = channels,
            movies = movies,
            series = series,
            epg = epg,
            includes = includes,
        )
        return TsiptvParseResult.Success(document, report())
    }

    private fun fail(code: TsiptvIssueCode, path: String, message: String): TsiptvParseResult {
        add(code, path, message)
        return TsiptvParseResult.Failure(report())
    }

    /**
     * Reads a top-level array, keeping at most [max] accepted items; the entries after the
     * limit are not read and are reported as one `W_LIMIT`.
     */
    private fun <T : Any> collection(
        el: JsonElement?,
        path: String,
        max: Int,
        readItem: (JsonElement, String) -> T?,
    ): List<T> {
        if (el.isAbsent()) return emptyList()
        val array = el.asArr() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "\"$path\" must be an array; it is ignored.")
            return emptyList()
        }
        val out = ArrayList<T>(minOf(array.size, max))
        for ((index, item) in array.withIndex()) {
            if (out.size >= max) {
                val rest = array.size - index
                add(TsiptvIssueCode.W_LIMIT, path, "More than $max entries; the last $rest were dropped.", rest)
                break
            }
            readItem(item, "$path[$index]")?.let { out += it }
        }
        return out
    }

    // --- §5.1 Meta ------------------------------------------------------------------------------

    private fun meta(el: JsonElement?): TsiptvMeta? {
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.E_META, "meta", if (el.isAbsent()) "\"meta\" is missing." else "\"meta\" must be an object.")
            return null
        }
        val langEl = obj["language"]
        if (!langEl.isAbsent()) {
            val lang = langEl.asStr()
            if (lang != null && TsiptvRules.isLanguageTag(lang)) {
                language = lang
            } else {
                add(TsiptvIssueCode.W_FIELD, "meta.language", "Not a language tag; \"en\" is used.")
            }
        }
        val name = text(obj["name"], "meta.name")
        if (name == null) {
            add(
                TsiptvIssueCode.E_META, "meta.name",
                if (obj["name"].isAbsent()) "\"meta.name\" is missing." else "\"meta.name\" is not a valid text."
            )
            return null
        }
        val author = obj["author"].let { a ->
            if (a.isAbsent()) return@let null
            val aObj = a.asObj()
            val authorName = shortText(aObj?.get("name").asStr(), "meta.author.name")?.takeIf { TsiptvRules.lengthIn(it, 1, 100) }
            if (aObj == null || authorName == null) {
                add(TsiptvIssueCode.W_FIELD, "meta.author", "\"author\" needs a name of 1–100 characters; it is ignored.")
                return@let null
            }
            TsiptvAuthor(
                name = authorName,
                url = optUrl(aObj, "url", "meta.author"),
                email = optString(aObj, "email", "meta.author", maxLength = 254)?.takeIf {
                    if ('@' in it && it.none(Char::isWhitespace)) true else {
                        add(TsiptvIssueCode.W_FIELD, "meta.author.email", "Not an e-mail address; it is ignored.")
                        false
                    }
                },
            )
        }
        val updatedAt = optString(obj, "updatedAt", "meta", maxLength = 64)?.takeIf {
            if (TsiptvRules.isDateTime(it)) true else {
                add(TsiptvIssueCode.W_FIELD, "meta.updatedAt", "Not an RFC 3339 date-time; it is ignored.")
                false
            }
        }
        // Safety flag: a value that is present but not a boolean is read as "adult" so the
        // confirmation is asked rather than silently skipped.
        val adult = obj["adult"].let { a ->
            when {
                a.isAbsent() -> false
                a.asBool() != null -> a.asBool()!!
                else -> {
                    add(TsiptvIssueCode.W_FIELD, "meta.adult", "\"adult\" must be true or false; it is treated as true.")
                    true
                }
            }
        }
        return TsiptvMeta(
            name = name,
            description = optText(obj, "description", "meta", long = true),
            author = author,
            logo = optUrl(obj, "logo", "meta"),
            homepage = optUrl(obj, "homepage", "meta"),
            language = language,
            languages = stringList(obj, "languages", "meta", maxCount = 20, maxLength = 35, check = TsiptvRules::isLanguageTag),
            updatedAt = updatedAt,
            adult = adult,
            license = optString(obj, "license", "meta", maxLength = 200),
        )
    }

    // --- §5.2 Text ------------------------------------------------------------------------------

    /**
     * A Text / LongText value, or null when it is unusable (wrong type, empty, no valid entry);
     * the caller reports that with the code that fits. Partial repairs are reported here (`W_TEXT`).
     */
    private fun text(el: JsonElement?, path: String, long: Boolean = false): LocalizedText? {
        val max = if (long) TsiptvLimits.MAX_LONG_TEXT_CHARS else TsiptvLimits.MAX_TEXT_CHARS
        el.asStr()?.let { plain ->
            val value = cleanText(plain, path, max, long) ?: return null
            return LocalizedText(mapOf(language to value), language)
        }
        val obj = el.asObj() ?: return null
        val values = LinkedHashMap<String, String>()
        for ((index, entry) in obj.entries.withIndex()) {
            val (key, value) = entry
            // Keys of a localized object are data, not members: `x-…` is just an invalid tag (§4).
            // A key that is not a language tag may be anything (a URL, a token): it is never
            // echoed; the path names the entry by its position in the object instead.
            if (!TsiptvRules.isLanguageTag(key)) {
                add(TsiptvIssueCode.W_TEXT, "$path[$index]", "Entry ${index + 1} is not keyed by a language tag; it is ignored.")
                continue
            }
            val entryPath = "$path.$key"
            if (values.size >= TsiptvLimits.MAX_LOCALIZED_ENTRIES) {
                add(TsiptvIssueCode.W_TEXT, entryPath, "More than ${TsiptvLimits.MAX_LOCALIZED_ENTRIES} languages; the entry is ignored.")
                continue
            }
            val str = value.asStr()
            val cleaned = if (str == null) null else cleanText(str, entryPath, max, long)
            if (cleaned == null) {
                add(TsiptvIssueCode.W_TEXT, entryPath, "Not a non-empty string; the entry is ignored.")
                continue
            }
            values[key] = cleaned
        }
        return if (values.isEmpty()) null else LocalizedText(values, language)
    }

    private fun cleanText(raw: String, path: String, max: Int, long: Boolean): String? {
        var value = raw.trimJs()
        if (value.isEmpty()) return null
        if (!long && (value.contains('\n') || value.contains('\r'))) {
            value = value.replace(LINE_BREAKS, " ")
            add(TsiptvIssueCode.W_TEXT, path, "Line breaks are allowed in descriptions only; they were replaced by spaces.")
        }
        // LongText line breaks are normalised to LF (CRLF and a lone CR), silently.
        if (long && '\r' in value) value = value.replace("\r\n", "\n").replace('\r', '\n')
        // Every other C0 control (tab included) is not text: replaced by spaces, reported.
        val isStrayControl = { c: Char -> TsiptvRules.isC0(c) && !(long && c == '\n') }
        if (value.any(isStrayControl)) {
            val source = value
            value = TsiptvRules.trimEcma(CharArray(source.length) { i -> if (isStrayControl(source[i])) ' ' else source[i] }.concatToString())
            add(TsiptvIssueCode.W_TEXT, path, "Control characters are not allowed in text; they were replaced by spaces.")
            if (value.isEmpty()) return null
        }
        truncateCodePoints(value, max)?.let {
            add(TsiptvIssueCode.W_TEXT, path, "Longer than $max characters; the text was truncated.")
            value = it
        }
        return value
    }

    private fun optText(obj: JsonObject, key: String, parent: String, long: Boolean = false): LocalizedText? {
        val el = obj[key]
        if (el.isAbsent()) return null
        return text(el, "$parent.$key", long) ?: run {
            add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Not a valid text; the field is ignored.")
            null
        }
    }

    /** Required `name` of a content item. */
    private fun requiredName(obj: JsonObject, path: String): LocalizedText? {
        val el = obj["name"]
        return text(el, "$path.name") ?: run {
            add(
                TsiptvIssueCode.E_ITEM_NAME, "$path.name",
                if (el.isAbsent()) "The name is missing; the item is skipped." else "The name is not a valid text; the item is skipped.",
                1,
            )
            null
        }
    }

    // --- Scalars and lists ----------------------------------------------------------------------

    /** An image or link URL: an invalid one drops the field (`E_URL`, §10). */
    private fun optUrl(obj: JsonObject, key: String, parent: String): String? {
        val el = obj[key]
        if (el.isAbsent()) return null
        val url = el.asStr()?.trimJs()
        if (url != null && TsiptvRules.isHttpUrl(url)) return url
        add(TsiptvIssueCode.E_URL, "$parent.$key", URL_MESSAGE + " The field is ignored.")
        return null
    }

    /**
     * A short free-text string (group, genre, tag, name, epgId, quality, catalog field, …): C0
     * controls (tab, CR, LF included) are replaced by spaces and reported (`W_FIELD` at [path]),
     * then the value is trimmed — the same tolerance as Text.
     */
    private fun shortText(raw: String?, path: String): String? {
        if (raw == null) return null
        if (raw.none(TsiptvRules::isC0)) return raw.trimJs()
        add(TsiptvIssueCode.W_FIELD, path, "Control characters are not allowed here; they were replaced by spaces.")
        return CharArray(raw.length) { i -> if (TsiptvRules.isC0(raw[i])) ' ' else raw[i] }.concatToString().trimJs()
    }

    private fun optString(obj: JsonObject, key: String, parent: String, maxLength: Int, minLength: Int = 1): String? {
        val el = obj[key]
        if (el.isAbsent()) return null
        val value = shortText(el.asStr(), "$parent.$key")
        if (value != null && TsiptvRules.lengthIn(value, minLength, maxLength)) return value
        add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Must be a string of $minLength–$maxLength characters; the field is ignored.")
        return null
    }

    private fun optInt(obj: JsonObject, key: String, parent: String, range: IntRange): Int? {
        val el = obj[key]
        if (el.isAbsent()) return null
        val value = el.asInt()
        if (value != null && value in range.first.toLong()..range.last.toLong()) return value.toInt()
        add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Must be an integer from ${range.first} to ${range.last}; the field is ignored.")
        return null
    }

    private fun optNumber(obj: JsonObject, key: String, parent: String, min: Double, max: Double): Double? {
        val el = obj[key]
        if (el.isAbsent()) return null
        val value = el.asNum()
        if (value != null && value >= min && value <= max) return value
        add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Must be a number from ${fmt(min)} to ${fmt(max)}; the field is ignored.")
        return null
    }

    private fun optBool(obj: JsonObject, key: String, parent: String): Boolean? {
        val el = obj[key]
        if (el.isAbsent()) return null
        el.asBool()?.let { return it }
        add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Must be true or false; the field is ignored.")
        return null
    }

    /**
     * An array of strings: wrong entries are dropped (`W_FIELD`), entries past [maxCount] are
     * dropped (`W_LIMIT`), a value that is not an array is ignored (`W_FIELD`).
     */
    private fun stringList(
        obj: JsonObject,
        key: String,
        parent: String,
        maxCount: Int,
        maxLength: Int,
        check: ((String) -> Boolean)? = null,
    ): List<String> {
        val el = obj[key]
        if (el.isAbsent()) return emptyList()
        val path = "$parent.$key"
        val array = el.asArr() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "Must be an array of strings; the field is ignored.")
            return emptyList()
        }
        val out = ArrayList<String>(minOf(array.size, maxCount))
        for ((index, item) in array.withIndex()) {
            if (out.size >= maxCount) {
                add(TsiptvIssueCode.W_LIMIT, path, "More than $maxCount entries; the rest were dropped.")
                break
            }
            val value = shortText(item.asStr(), "$path[$index]")
            if (value == null || !TsiptvRules.lengthIn(value, 1, maxLength) || (check != null && !check(value))) {
                add(TsiptvIssueCode.W_FIELD, "$path[$index]", "Invalid entry; it is ignored.")
                continue
            }
            out += value
        }
        return out
    }

    // --- §5.5 Headers ---------------------------------------------------------------------------

    /** Valid headers (possibly empty), or null when the value is absent or not an object. */
    private fun headers(el: JsonElement?, path: String): Map<String, String>? {
        if (el.isAbsent()) return null
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "Headers must be an object; they are ignored.")
            return null
        }
        val out = LinkedHashMap<String, Pair<String, String>>()
        var overLimit = false
        for ((index, entry) in obj.entries.withIndex()) {
            val (name, value) = entry
            if (!TsiptvRules.isHeaderName(name)) {
                // An invalid name may be anything (a URL, a token): named by position, never echoed.
                add(TsiptvIssueCode.E_HEADER_FORBIDDEN, "$path[$index]", "Header ${index + 1} does not have a valid HTTP header name; it is dropped.")
                continue
            }
            // Valid names are RFC 9110 tokens (no ':', '/', spaces); header values are never echoed.
            val entryPath = "$path.$name"
            if (TsiptvRules.isForbiddenHeader(name)) {
                add(TsiptvIssueCode.E_HEADER_FORBIDDEN, entryPath, "This header may not be set by a source; it is dropped.")
                continue
            }
            val str = value.asStr()
            if (str == null || !TsiptvRules.isHeaderValue(str)) {
                add(TsiptvIssueCode.W_FIELD, entryPath, "Header values are strings of at most 4,096 characters without line breaks; the header is dropped.")
                continue
            }
            val key = name.lowercase()
            if (key !in out && out.size >= TsiptvLimits.MAX_HEADERS) {
                overLimit = true
                continue
            }
            out.remove(key)
            out[key] = name to str
        }
        if (overLimit) add(TsiptvIssueCode.W_LIMIT, path, "More than ${TsiptvLimits.MAX_HEADERS} headers; the rest were dropped.")
        return out.values.associate { it }
    }

    // --- §8.4 mimeType, §8.5 DRM ----------------------------------------------------------------

    private fun mimeType(el: JsonElement?, path: String): String? {
        if (el.isAbsent()) return null
        val value = el.asStr()?.trimJs()
        if (value == null || !TsiptvRules.isMimeTypeSyntax(value)) {
            add(TsiptvIssueCode.W_FIELD, path, "Not a MIME type; the hint is ignored.")
            return null
        }
        return RECOGNISED_MIME_TYPES[value.lowercase()] ?: run {
            add(TsiptvIssueCode.W_UNKNOWN_TYPE, path, "MIME type not recognised; the hint is ignored.")
            null
        }
    }

    private fun drm(el: JsonElement?, path: String): DrmRead {
        if (el.isAbsent()) return DrmRead.Absent
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.E_DRM, path, "\"drm\" must be an object; the stream is dropped.")
            return DrmRead.Invalid
        }
        val systemName = obj["system"].asStr()?.trimJs()
        if (systemName.isNullOrEmpty()) {
            add(TsiptvIssueCode.E_DRM, "$path.system", "The DRM system is missing; the stream is dropped.")
            return DrmRead.Invalid
        }
        val system = when (systemName.lowercase()) {
            "widevine" -> DrmSystem.WIDEVINE
            "playready" -> DrmSystem.PLAYREADY
            "clearkey" -> DrmSystem.CLEARKEY
            else -> {
                // §11: never play such a stream without DRM; the player refuses an unsupported spec.
                add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.system", "Unknown DRM system; the stream will not be played on this device.")
                return DrmRead.Valid(DrmSpec.unsupported("unknown DRM system"))
            }
        }
        val licenseHeaders = headers(obj["licenseHeaders"], "$path.licenseHeaders").orEmpty()

        val licenseEl = obj["licenseUrl"]
        var licenseUrl: String? = null
        if (!licenseEl.isAbsent()) {
            licenseUrl = licenseEl.asStr()?.trimJs()?.takeIf(TsiptvRules::isHttpUrl)
            if (licenseUrl == null) {
                add(TsiptvIssueCode.E_URL, "$path.licenseUrl", "$URL_MESSAGE The stream is dropped.")
                return DrmRead.Invalid
            }
        }

        if (system != DrmSystem.CLEARKEY) {
            if (licenseUrl == null) {
                add(TsiptvIssueCode.E_DRM, "$path.licenseUrl", "Widevine and PlayReady need a licenseUrl; the stream is dropped.")
                return DrmRead.Invalid
            }
            if (!obj["keys"].isAbsent()) {
                add(TsiptvIssueCode.W_FIELD, "$path.keys", "\"keys\" is for clearkey only; it is ignored.")
            }
            return DrmRead.Valid(DrmSpec(system = system, licenseUrl = licenseUrl, licenseHeaders = licenseHeaders))
        }

        val keysEl = obj["keys"]
        val keys = ArrayList<ClearKey>()
        if (!keysEl.isAbsent()) {
            val keysObj = keysEl.asObj()
            // Key ids are data (§4): an `x-…` key is simply not 32 hex digits.
            val entries = keysObj?.entries
            if (entries == null || entries.isEmpty() || entries.size > TsiptvLimits.MAX_CLEARKEY_PAIRS) {
                add(TsiptvIssueCode.E_DRM, "$path.keys", "\"keys\" must map 1–20 key ids to keys; the stream is dropped.")
                return DrmRead.Invalid
            }
            for ((kid, key) in entries) {
                val keyHex = key.asStr()
                if (!TsiptvRules.isHex32(kid) || keyHex == null || !TsiptvRules.isHex32(keyHex)) {
                    add(TsiptvIssueCode.E_DRM, "$path.keys", "Key ids and keys must be 32 hexadecimal characters; the stream is dropped.")
                    return DrmRead.Invalid
                }
                keys += ClearKey(kid.lowercase(), keyHex.lowercase())
            }
        }
        if (licenseUrl == null && keys.isEmpty()) {
            add(TsiptvIssueCode.E_DRM, path, "ClearKey needs a licenseUrl or keys; the stream is dropped.")
            return DrmRead.Invalid
        }
        return DrmRead.Valid(
            DrmSpec(system = system, licenseUrl = licenseUrl, licenseHeaders = licenseHeaders, clearKeys = keys)
        )
    }

    // --- §8.0 Playable fields, §8.4 Stream ------------------------------------------------------

    /** The item's streams with defaults merged, or null when none is left (`E_NO_STREAM` reported). */
    private fun playable(obj: JsonObject, path: String): List<TsiptvStream>? {
        val urlEl = obj["url"]
        val streamsEl = obj["streams"]
        val hasUrl = !urlEl.isAbsent()
        val hasStreams = !streamsEl.isAbsent()
        if (hasUrl && hasStreams) {
            add(TsiptvIssueCode.E_NO_STREAM, path, "An item has either \"url\" or \"streams\", not both; the item is skipped.", 1)
            return null
        }
        if (!hasUrl && !hasStreams) {
            add(TsiptvIssueCode.E_NO_STREAM, path, "No \"url\" or \"streams\"; the item is skipped.", 1)
            return null
        }
        val itemHeaders = headers(obj["headers"], "$path.headers").orEmpty()
        val itemMime = mimeType(obj["mimeType"], "$path.mimeType")
        val itemDrm = drm(obj["drm"], "$path.drm")

        val streams = ArrayList<TsiptvStream>()
        if (hasUrl) {
            val url = urlEl.asStr()?.trimJs()
            if (url == null || !TsiptvRules.isHttpUrl(url)) {
                add(TsiptvIssueCode.E_URL, "$path.url", "$URL_MESSAGE The stream is dropped.")
            } else if (itemDrm != DrmRead.Invalid) {
                streams += TsiptvStream(
                    url = url,
                    headers = itemHeaders,
                    mimeType = itemMime,
                    drm = (itemDrm as? DrmRead.Valid)?.spec,
                )
            }
        } else {
            val array = streamsEl.asArr()
            if (array == null || array.isEmpty()) {
                add(TsiptvIssueCode.E_NO_STREAM, "$path.streams", "\"streams\" must be a non-empty array; the item is skipped.", 1)
                return null
            }
            for ((index, streamEl) in array.withIndex()) {
                if (streams.size >= TsiptvLimits.MAX_STREAMS) {
                    add(TsiptvIssueCode.W_LIMIT, "$path.streams", "More than ${TsiptvLimits.MAX_STREAMS} streams; the rest were dropped.")
                    break
                }
                stream(streamEl, "$path.streams[$index]", itemHeaders, itemMime, itemDrm)?.let { streams += it }
            }
        }
        if (streams.isEmpty()) {
            add(TsiptvIssueCode.E_NO_STREAM, path, "No valid stream is left; the item is skipped.", 1)
            return null
        }
        return streams
    }

    private fun stream(
        el: JsonElement,
        path: String,
        itemHeaders: Map<String, String>,
        itemMime: String?,
        itemDrm: DrmRead,
    ): TsiptvStream? {
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.E_URL, path, "A stream must be an object with a \"url\"; it is dropped.")
            return null
        }
        val url = obj["url"].asStr()?.trimJs()
        if (url == null || !TsiptvRules.isHttpUrl(url)) {
            add(TsiptvIssueCode.E_URL, "$path.url", "$URL_MESSAGE The stream is dropped.")
            return null
        }
        val drm = when (val own = drm(obj["drm"], "$path.drm")) {
            DrmRead.Invalid -> return null
            is DrmRead.Valid -> own.spec
            // The item default was reported where it is written; streams inheriting it are dropped.
            DrmRead.Absent -> when (itemDrm) {
                DrmRead.Invalid -> return null
                is DrmRead.Valid -> itemDrm.spec
                DrmRead.Absent -> null
            }
        }
        val ownHeaders = headers(obj["headers"], "$path.headers").orEmpty()
        val ownMime = mimeType(obj["mimeType"], "$path.mimeType")
        return TsiptvStream(
            url = url,
            name = optText(obj, "name", path),
            headers = mergeHeaders(itemHeaders, ownHeaders).let { (merged, dropped) ->
                // §5.5: the 20-header limit also applies to the effective headers after merging.
                if (dropped > 0) {
                    add(
                        TsiptvIssueCode.W_LIMIT, "$path.headers",
                        "More than ${TsiptvLimits.MAX_HEADERS} headers after merging the item's; $dropped item headers were dropped.",
                    )
                }
                merged
            },
            mimeType = ownMime ?: itemMime,
            drm = drm,
            quality = optString(obj, "quality", path, maxLength = 32),
            language = optLanguage(obj, "language", path),
        )
    }

    private fun optLanguage(obj: JsonObject, key: String, parent: String): String? {
        val el = obj[key]
        if (el.isAbsent()) return null
        val value = el.asStr()?.trimJs()
        if (value != null && TsiptvRules.isLanguageTag(value)) return value
        add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Not a language tag; the field is ignored.")
        return null
    }

    // --- §8.1 Channel ---------------------------------------------------------------------------

    /** Checks an item's `id` against the pattern and the ids already taken; does not claim it. */
    private fun itemId(obj: JsonObject, path: String, alsoTaken: Set<String> = emptySet()): String? {
        val el = obj["id"]
        val id = el.asStr()
        if (id == null || !TsiptvRules.isId(id)) {
            add(
                TsiptvIssueCode.E_ITEM_ID, "$path.id",
                if (el.isAbsent()) "The id is missing; the item is skipped."
                else "Invalid id (1–128 letters, digits, '.', '_' or '-', starting with a letter or digit; no ':'); the item is skipped.",
                1,
            )
            return null
        }
        if (id in ids || id in alsoTaken) {
            add(TsiptvIssueCode.E_DUPLICATE_ID, "$path.id", "This id is already used in the document; the later item is skipped.", 1)
            return null
        }
        return id
    }

    private fun notAnObject(path: String): Nothing? {
        add(TsiptvIssueCode.E_ITEM_ID, path, "Not an object; the item is skipped.", 1)
        return null
    }

    private fun channel(el: JsonElement, path: String): TsiptvChannel? {
        val obj = el.asObj() ?: return notAnObject(path)
        val id = itemId(obj, path) ?: return null
        val name = requiredName(obj, path) ?: return null
        val streams = playable(obj, path) ?: return null

        val type = obj["type"].let { t ->
            if (t.isAbsent()) return@let TsiptvChannelType.TV
            when (t.asStr()?.trimJs()?.lowercase()) {
                "tv" -> TsiptvChannelType.TV
                "radio" -> TsiptvChannelType.RADIO
                else -> {
                    add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.type", "Unknown channel type; treated as \"tv\".")
                    TsiptvChannelType.TV
                }
            }
        }
        ids += id
        return TsiptvChannel(
            id = id,
            name = name,
            type = type,
            number = optInt(obj, "number", path, 0..99_999),
            logo = optUrl(obj, "logo", path),
            groups = stringList(obj, "groups", path, maxCount = 10, maxLength = 100),
            epgId = optString(obj, "epgId", path, maxLength = 200),
            epgShiftHours = optNumber(obj, "epgShiftHours", path, -12.0, 14.0),
            description = optText(obj, "description", path, long = true),
            tags = stringList(obj, "tags", path, maxCount = 20, maxLength = 50),
            catchup = catchup(obj["catchup"], "$path.catchup"),
            streams = streams,
        )
    }

    // --- §8.6 Catchup ---------------------------------------------------------------------------

    private fun catchup(el: JsonElement?, path: String): CatchupSpec? {
        if (el.isAbsent()) return null
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "\"catchup\" must be an object; no catch-up for this channel.")
            return null
        }
        val modeName = obj["mode"].asStr()?.trimJs()
        if (modeName.isNullOrEmpty()) {
            add(TsiptvIssueCode.W_FIELD, "$path.mode", "The catch-up mode is missing; no catch-up for this channel.")
            return null
        }
        val mode = when (modeName.lowercase()) {
            "default" -> CatchupMode.DEFAULT
            "append" -> CatchupMode.APPEND
            "shift" -> CatchupMode.SHIFT
            "flussonic" -> CatchupMode.FLUSSONIC
            "flussonic-ts" -> CatchupMode.FLUSSONIC_TS
            "xc" -> CatchupMode.XC
            else -> {
                add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.mode", "Unknown catch-up mode; no catch-up for this channel.")
                return null
            }
        }
        // §8.6 "source scheme rules". Only `default` and `append` use `source`; the other modes
        // build their URL from the live URL and ignore it silently (whatever it contains).
        val source: String? = when (mode) {
            CatchupMode.DEFAULT, CatchupMode.APPEND -> {
                // Not trimmed: any whitespace, leading or trailing included, makes it invalid (schema).
                val value = obj["source"].asStr()
                val problem = when {
                    value == null -> "Modes \"default\" and \"append\" need a source"
                    !TsiptvRules.isCatchupSource(value) ->
                        "The source must be 1–2,048 characters without whitespace or control characters"
                    // A template, not an HttpUrl yet (placeholders); the substituted URL is checked at play time.
                    mode == CatchupMode.DEFAULT && !TsiptvRules.startsWithHttpScheme(value) ->
                        "The \"default\" template must be an http or https URL"
                    // `append` is query text: nested URLs of any scheme are data (never opened).
                    else -> null
                }
                if (problem != null) {
                    add(TsiptvIssueCode.W_FIELD, "$path.source", "$problem; no catch-up for this channel.")
                    return null
                }
                value
            }
            else -> null
        }
        return CatchupSpec(
            mode = mode,
            source = source,
            days = optInt(obj, "days", path, 1..60) ?: DEFAULT_CATCHUP_DAYS,
            correctionHours = optNumber(obj, "correctionHours", path, -24.0, 24.0) ?: 0.0,
        )
    }

    // --- §8.7 Subtitle --------------------------------------------------------------------------

    private fun subtitles(obj: JsonObject, parent: String): List<TsiptvSubtitle> {
        val el = obj["subtitles"]
        if (el.isAbsent()) return emptyList()
        val path = "$parent.subtitles"
        val array = el.asArr() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "\"subtitles\" must be an array; it is ignored.")
            return emptyList()
        }
        val out = ArrayList<TsiptvSubtitle>()
        for ((index, item) in array.withIndex()) {
            if (out.size >= TsiptvLimits.MAX_SUBTITLES) {
                add(TsiptvIssueCode.W_LIMIT, path, "More than ${TsiptvLimits.MAX_SUBTITLES} subtitles; the rest were dropped.")
                break
            }
            subtitle(item, "$path[$index]")?.let { out += it }
        }
        return out
    }

    private fun subtitle(el: JsonElement, path: String): TsiptvSubtitle? {
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "A subtitle must be an object; it is ignored.")
            return null
        }
        val url = obj["url"].asStr()?.trimJs()
        if (url == null || !TsiptvRules.isHttpUrl(url)) {
            add(TsiptvIssueCode.E_URL, "$path.url", "$URL_MESSAGE The subtitle is dropped.")
            return null
        }
        val language = obj["language"].asStr()?.trimJs()?.takeIf(TsiptvRules::isLanguageTag) ?: run {
            add(TsiptvIssueCode.W_FIELD, "$path.language", "A subtitle needs a language tag; it is ignored.")
            return null
        }
        val fromExtension = if (url.substringBefore('?').substringBefore('#').lowercase().endsWith(".srt")) {
            TsiptvSubtitleFormat.SRT
        } else {
            TsiptvSubtitleFormat.VTT
        }
        val formatEl = obj["format"]
        val format = if (formatEl.isAbsent()) fromExtension else when (formatEl.asStr()?.trimJs()?.lowercase()) {
            "vtt" -> TsiptvSubtitleFormat.VTT
            "srt" -> TsiptvSubtitleFormat.SRT
            else -> {
                add(TsiptvIssueCode.W_FIELD, "$path.format", "Format must be \"vtt\" or \"srt\"; it is taken from the URL.")
                fromExtension
            }
        }
        return TsiptvSubtitle(url = url, language = language, label = optText(obj, "label", path), format = format)
    }

    // --- §8.2 Movie -----------------------------------------------------------------------------

    private fun movie(el: JsonElement, path: String): TsiptvMovie? {
        val obj = el.asObj() ?: return notAnObject(path)
        val id = itemId(obj, path) ?: return null
        val name = requiredName(obj, path) ?: return null
        val streams = playable(obj, path) ?: return null
        ids += id
        return TsiptvMovie(
            id = id,
            name = name,
            originalName = optString(obj, "originalName", path, maxLength = 200),
            poster = optUrl(obj, "poster", path),
            backdrop = optUrl(obj, "backdrop", path),
            logo = optUrl(obj, "logo", path),
            description = optText(obj, "description", path, long = true),
            year = optInt(obj, "year", path, 1870..2100),
            releaseDate = optDate(obj, "releaseDate", path),
            runtimeMinutes = optInt(obj, "runtimeMinutes", path, 1..1440),
            genres = stringList(obj, "genres", path, maxCount = 10, maxLength = 50),
            cast = stringList(obj, "cast", path, maxCount = 50, maxLength = 100),
            directors = stringList(obj, "directors", path, maxCount = 20, maxLength = 100),
            countries = stringList(obj, "countries", path, maxCount = 20, maxLength = 2, check = TsiptvRules::isCountry),
            languages = stringList(obj, "languages", path, maxCount = 20, maxLength = 35, check = TsiptvRules::isLanguageTag),
            ageRating = optString(obj, "ageRating", path, maxLength = 16),
            tags = stringList(obj, "tags", path, maxCount = 20, maxLength = 50),
            subtitles = subtitles(obj, path),
            streams = streams,
        )
    }

    private fun optDate(obj: JsonObject, key: String, parent: String): String? {
        val el = obj[key]
        if (el.isAbsent()) return null
        val value = el.asStr()?.trimJs()
        if (value != null && TsiptvRules.isFullDate(value)) return value
        add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Must be a date written YYYY-MM-DD; the field is ignored.")
        return null
    }

    // --- §8.3 Series, seasons, episodes ---------------------------------------------------------

    private fun series(el: JsonElement, path: String): TsiptvSeries? {
        val obj = el.asObj() ?: return notAnObject(path)
        val id = itemId(obj, path) ?: return null
        val name = requiredName(obj, path) ?: return null

        // Episode ids are claimed only if the series survives.
        val claimed = linkedSetOf(id)
        val seasonsEl = obj["seasons"]
        val array = seasonsEl.asArr()
        if (array == null || array.isEmpty()) {
            add(
                TsiptvIssueCode.E_NO_STREAM, "$path.seasons",
                "A series needs a non-empty \"seasons\" array; the series is skipped.", 1
            )
            return null
        }
        val seasons = ArrayList<TsiptvSeason>()
        val numbers = HashSet<Int>()
        var lostToEpisodeLimit = false
        for ((index, seasonEl) in array.withIndex()) {
            if (seasons.size >= TsiptvLimits.MAX_SEASONS) {
                val rest = array.size - index
                add(TsiptvIssueCode.W_LIMIT, "$path.seasons", "More than ${TsiptvLimits.MAX_SEASONS} seasons; the last $rest were dropped.", rest)
                break
            }
            when (val result = season(seasonEl, "$path.seasons[$index]", numbers, claimed)) {
                is SeasonRead.Ok -> seasons += result.season
                SeasonRead.LostToEpisodeLimit -> lostToEpisodeLimit = true
                SeasonRead.Dropped -> Unit
            }
        }
        if (seasons.isEmpty()) {
            if (lostToEpisodeLimit) {
                seriesDroppedByEpisodeLimit++
            } else {
                add(TsiptvIssueCode.E_NO_STREAM, path, "No valid season is left; the series is skipped.", 1)
            }
            return null
        }
        ids += claimed

        val year = optInt(obj, "year", path, 1870..2100)
        var endYear = optInt(obj, "endYear", path, 1870..2100)
        if (year != null && endYear != null && endYear < year) {
            add(TsiptvIssueCode.W_FIELD, "$path.endYear", "\"endYear\" is before \"year\"; it is ignored.")
            endYear = null
        }
        return TsiptvSeries(
            id = id,
            name = name,
            originalName = optString(obj, "originalName", path, maxLength = 200),
            poster = optUrl(obj, "poster", path),
            backdrop = optUrl(obj, "backdrop", path),
            logo = optUrl(obj, "logo", path),
            description = optText(obj, "description", path, long = true),
            year = year,
            endYear = endYear,
            genres = stringList(obj, "genres", path, maxCount = 10, maxLength = 50),
            cast = stringList(obj, "cast", path, maxCount = 50, maxLength = 100),
            countries = stringList(obj, "countries", path, maxCount = 20, maxLength = 2, check = TsiptvRules::isCountry),
            languages = stringList(obj, "languages", path, maxCount = 20, maxLength = 35, check = TsiptvRules::isLanguageTag),
            ageRating = optString(obj, "ageRating", path, maxLength = 16),
            tags = stringList(obj, "tags", path, maxCount = 20, maxLength = 50),
            seasons = seasons,
        )
    }

    private sealed interface SeasonRead {
        data class Ok(val season: TsiptvSeason) : SeasonRead
        data object Dropped : SeasonRead
        data object LostToEpisodeLimit : SeasonRead
    }

    private fun season(el: JsonElement, path: String, numbers: MutableSet<Int>, claimed: MutableSet<String>): SeasonRead {
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.E_ITEM_ID, path, "Not an object; the season is skipped.", 1)
            return SeasonRead.Dropped
        }
        val number = obj["number"].asInt()?.takeIf { it in 0..999 }?.toInt()
        if (number == null) {
            add(TsiptvIssueCode.E_ITEM_ID, "$path.number", "A season needs a number from 0 to 999; the season is skipped.", 1)
            return SeasonRead.Dropped
        }
        if (number in numbers) {
            add(TsiptvIssueCode.E_DUPLICATE_ID, "$path.number", "This season number is already used in the series; the season is skipped.", 1)
            return SeasonRead.Dropped
        }
        val array = obj["episodes"].asArr()
        if (array == null || array.isEmpty()) {
            add(TsiptvIssueCode.E_NO_STREAM, "$path.episodes", "A season needs a non-empty \"episodes\" array; the season is skipped.", 1)
            return SeasonRead.Dropped
        }
        val episodes = ArrayList<TsiptvEpisode>()
        val episodeNumbers = HashSet<Int>()
        var lost = 0
        for ((index, episodeEl) in array.withIndex()) {
            if (episodes.size >= TsiptvLimits.MAX_EPISODES_PER_SEASON) {
                val rest = array.size - index
                add(TsiptvIssueCode.W_LIMIT, "$path.episodes", "More than ${TsiptvLimits.MAX_EPISODES_PER_SEASON} episodes; the last $rest were dropped.", rest)
                break
            }
            if (episodeCount >= TsiptvLimits.MAX_EPISODES_PER_DOCUMENT) {
                lost++
                episodesOverLimit++
                continue
            }
            episode(episodeEl, "$path.episodes[$index]", episodeNumbers, claimed)?.let {
                episodes += it
                episodeCount++
            }
        }
        if (episodes.isEmpty()) {
            if (lost > 0) return SeasonRead.LostToEpisodeLimit
            add(TsiptvIssueCode.E_NO_STREAM, path, "No valid episode is left; the season is skipped.", 1)
            return SeasonRead.Dropped
        }
        numbers += number
        return SeasonRead.Ok(
            TsiptvSeason(
                number = number,
                name = optText(obj, "name", path),
                poster = optUrl(obj, "poster", path),
                description = optText(obj, "description", path, long = true),
                episodes = episodes,
            )
        )
    }

    private fun episode(el: JsonElement, path: String, numbers: MutableSet<Int>, claimed: MutableSet<String>): TsiptvEpisode? {
        val obj = el.asObj() ?: return notAnObject(path)
        val id = itemId(obj, path, alsoTaken = claimed) ?: return null
        val number = obj["number"].asInt()?.takeIf { it in 0..9_999 }?.toInt()
        if (number == null) {
            add(TsiptvIssueCode.E_ITEM_ID, "$path.number", "An episode needs a number from 0 to 9,999; the episode is skipped.", 1)
            return null
        }
        if (number in numbers) {
            add(TsiptvIssueCode.E_DUPLICATE_ID, "$path.number", "This episode number is already used in the season; the episode is skipped.", 1)
            return null
        }
        val name = requiredName(obj, path) ?: return null
        val streams = playable(obj, path) ?: return null
        numbers += number
        claimed += id
        return TsiptvEpisode(
            id = id,
            number = number,
            name = name,
            description = optText(obj, "description", path, long = true),
            thumbnail = optUrl(obj, "thumbnail", path),
            releaseDate = optDate(obj, "releaseDate", path),
            runtimeMinutes = optInt(obj, "runtimeMinutes", path, 1..1440),
            subtitles = subtitles(obj, path),
            streams = streams,
        )
    }

    // --- §9 EPG links and includes --------------------------------------------------------------

    private fun epgLinks(el: JsonElement?): List<TsiptvEpgLink> {
        if (el.isAbsent()) return emptyList()
        val array = el.asArr() ?: run {
            add(TsiptvIssueCode.W_FIELD, "epg", "\"epg\" must be an array; it is ignored.")
            return emptyList()
        }
        val out = ArrayList<TsiptvEpgLink>()
        for ((index, item) in array.withIndex()) {
            val path = "epg[$index]"
            if (out.size >= TsiptvLimits.MAX_EPG_LINKS) {
                val rest = array.size - index
                add(TsiptvIssueCode.W_LIMIT, "epg", "More than ${TsiptvLimits.MAX_EPG_LINKS} guides; the last $rest were dropped.", rest)
                break
            }
            val obj = item.asObj()
            val url = (if (obj != null) obj["url"] else item).asStr()?.trimJs()
            if (url == null || !TsiptvRules.isHttpUrl(url)) {
                add(TsiptvIssueCode.E_URL, if (obj != null) "$path.url" else path, "$URL_MESSAGE The guide is dropped.")
                continue
            }
            val refresh = obj?.let { optInt(it, "refreshHours", path, TsiptvLimits.MIN_REFRESH_HOURS..TsiptvLimits.MAX_REFRESH_HOURS) }
            out += TsiptvEpgLink(url = url, refreshHours = refresh ?: TsiptvLimits.DEFAULT_REFRESH_HOURS, index = index)
        }
        return out
    }

    private fun include(el: JsonElement, path: String): TsiptvInclude? {
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.E_INCLUDE, path, "An include must be an object; it is skipped.", 1)
            return null
        }
        val idEl = obj["id"]
        val id = idEl.asStr()
        if (id == null || !TsiptvRules.isId(id)) {
            add(
                TsiptvIssueCode.E_INCLUDE, "$path.id",
                if (idEl.isAbsent()) "The include id is missing; the include is skipped." else "Invalid include id; the include is skipped.",
                1,
            )
            return null
        }
        if (id.startsWith(RESERVED_INCLUDE_ID_PREFIX)) {
            // §9.1: `epg-` is reserved for the implicit ids of EPG links (case-sensitive).
            add(TsiptvIssueCode.E_INCLUDE, "$path.id", "Include ids starting with \"epg-\" are reserved for EPG links; the include is skipped.", 1)
            return null
        }
        if (id in ids) {
            add(TsiptvIssueCode.E_DUPLICATE_ID, "$path.id", "This id is already used in the document; the include is skipped.", 1)
            return null
        }
        val typeName = obj["type"].asStr()?.trimJs()
        val type = TsiptvIncludeType.entries.firstOrNull { it.wireName == typeName?.lowercase() }
        if (type == null) {
            add(
                TsiptvIssueCode.E_INCLUDE, "$path.type",
                if (typeName == null) "The include type is missing; the include is skipped." else "Unknown include type; the include is skipped.",
                1,
            )
            return null
        }
        val url = obj["url"].asStr()?.trimJs()
        if (url == null || !TsiptvRules.isHttpUrl(url)) {
            add(TsiptvIssueCode.E_INCLUDE, "$path.url", "$URL_MESSAGE The include is skipped.", 1)
            return null
        }
        if (type == TsiptvIncludeType.STREMIO && !TsiptvRules.isStremioManifestUrl(url)) {
            add(TsiptvIssueCode.E_INCLUDE, "$path.url", "A stremio include must point at a URL ending in /manifest.json; the include is skipped.", 1)
            return null
        }
        if (documentUrl != null && TsiptvUrlKey.of(url) == TsiptvUrlKey.of(documentUrl)) {
            add(TsiptvIssueCode.E_INCLUDE_CYCLE, "$path.url", "The include points back to this document; it is skipped.", 1)
            return null
        }
        val headers = if (type == TsiptvIncludeType.STREMIO) {
            if (!obj["headers"].isAbsent()) {
                add(TsiptvIssueCode.W_FIELD, "$path.headers", "Headers are never sent to addons; they are ignored.")
            }
            emptyMap()
        } else {
            headers(obj["headers"], "$path.headers").orEmpty()
        }
        ids += id
        return TsiptvInclude(
            id = id,
            type = type,
            url = url,
            name = optText(obj, "name", path),
            refreshHours = optInt(obj, "refreshHours", path, TsiptvLimits.MIN_REFRESH_HOURS..TsiptvLimits.MAX_REFRESH_HOURS)
                ?: TsiptvLimits.DEFAULT_REFRESH_HOURS,
            headers = headers,
        )
    }

    // --- §6 Appearance --------------------------------------------------------------------------

    private fun appearance(el: JsonElement?): TsiptvAppearance? {
        if (el.isAbsent()) return null
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, "appearance", "\"appearance\" must be an object; the defaults are used.")
            return null
        }
        val background = obj["background"].let { b ->
            if (b.isAbsent()) return@let null
            val bObj = b.asObj() ?: run {
                add(TsiptvIssueCode.W_FIELD, "appearance.background", "\"background\" must be an object; it is ignored.")
                return@let null
            }
            val path = "appearance.background"
            val gradient = bObj["gradient"].let { g ->
                if (g.isAbsent()) return@let null
                val stops = g.asArr()?.map { it.asStr()?.trimJs() }
                if (stops == null || stops.size !in 2..3 || stops.any { it == null || !TsiptvRules.isColor(it) }) {
                    add(TsiptvIssueCode.W_FIELD, "$path.gradient", "A gradient is 2–3 #RRGGBB colours; it is ignored.")
                    null
                } else {
                    stops.filterNotNull()
                }
            }
            val dim = bObj["imageDim"].let { d ->
                if (d.isAbsent()) return@let TsiptvBackground.DEFAULT_IMAGE_DIM
                val value = d.asNum()
                when {
                    value == null || value > 1.0 || value < 0.0 -> {
                        add(TsiptvIssueCode.W_FIELD, "$path.imageDim", "\"imageDim\" must be a number from 0.3 to 1.0; 0.6 is used.")
                        TsiptvBackground.DEFAULT_IMAGE_DIM
                    }
                    // §6: values below 0.3 are raised to 0.3.
                    value < TsiptvBackground.MIN_IMAGE_DIM -> TsiptvBackground.MIN_IMAGE_DIM
                    else -> value
                }
            }
            TsiptvBackground(
                color = optColor(bObj, "color", path),
                gradient = gradient,
                image = optUrl(bObj, "image", path),
                imageDim = dim,
            )
        }
        return TsiptvAppearance(
            accent = optColor(obj, "accent", "appearance"),
            accentSecondary = optColor(obj, "accentSecondary", "appearance"),
            background = background,
            card = cardStyle(obj["card"], "appearance.card"),
        )
    }

    private fun optColor(obj: JsonObject, key: String, parent: String): String? {
        val el = obj[key]
        if (el.isAbsent()) return null
        val value = el.asStr()?.trimJs()
        if (value != null && TsiptvRules.isColor(value)) return value
        add(TsiptvIssueCode.W_FIELD, "$parent.$key", "Must be a #RRGGBB colour; the field is ignored.")
        return null
    }

    private fun cardStyle(el: JsonElement?, path: String): TsiptvCardStyle? {
        if (el.isAbsent()) return null
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "\"card\" must be an object; it is ignored.")
            return null
        }
        val style = obj["style"].let { s ->
            if (s.isAbsent()) return@let null
            val name = s.asStr()?.trimJs()
            TsiptvCardKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: run {
                add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.style", "Unknown card style; \"auto\" is used.")
                TsiptvCardKind.AUTO
            }
        }
        val corner = obj["corner"].let { c ->
            if (c.isAbsent()) return@let null
            val name = c.asStr()?.trimJs()
            TsiptvCorner.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: run {
                add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.corner", "Unknown card corner; \"medium\" is used.")
                TsiptvCorner.MEDIUM
            }
        }
        return TsiptvCardStyle(style = style, corner = corner, showTitles = optBool(obj, "showTitles", path))
    }

    // --- §7 Layout ------------------------------------------------------------------------------

    private fun layout(el: JsonElement?, includeTypes: Map<String, TsiptvIncludeType>): TsiptvLayout? {
        if (el.isAbsent()) return null
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, "layout", "\"layout\" must be an object; the default layout is used.")
            return null
        }
        val home = obj["home"].asArr()
        if (home == null || home.isEmpty()) {
            add(TsiptvIssueCode.W_FIELD, "layout.home", "\"layout.home\" must be a non-empty array; the default layout is used.")
            return null
        }
        val sections = ArrayList<TsiptvSection>()
        for ((index, sectionEl) in home.withIndex()) {
            if (index >= TsiptvLimits.MAX_SECTIONS) {
                add(TsiptvIssueCode.W_LIMIT, "layout.home", "More than ${TsiptvLimits.MAX_SECTIONS} sections; the rest were dropped.")
                break
            }
            section(sectionEl, "layout.home[$index]", includeTypes)?.let { sections += it }
        }
        // §7.5: every section skipped → default layout.
        return if (sections.isEmpty()) null else TsiptvLayout(sections)
    }

    private fun section(el: JsonElement, path: String, includeTypes: Map<String, TsiptvIncludeType>): TsiptvSection? {
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "A section must be an object; it is skipped.")
            return null
        }
        val typeName = obj["type"].asStr()?.trimJs()
        if (typeName == null) {
            add(TsiptvIssueCode.W_FIELD, "$path.type", "The section type is missing; the section is skipped.")
            return null
        }
        val type = TsiptvSectionType.entries.firstOrNull { it.name.equals(typeName, ignoreCase = true) } ?: run {
            add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.type", "Unknown section type; the section is skipped.")
            return null
        }

        var query: TsiptvQuery? = null
        var items: List<TsiptvHeroItem>? = null
        val hasQuery = !obj["query"].isAbsent()
        // `items: []` says nothing; it is treated as absent so a hero's query still applies.
        val hasItems = !obj["items"].isAbsent() && obj["items"].asArr()?.isEmpty() != true
        if (type == TsiptvSectionType.HERO && hasItems) {
            if (hasQuery) add(TsiptvIssueCode.W_FIELD, "$path.query", "A hero has either \"query\" or \"items\"; the query is ignored.")
            items = heroItems(obj["items"], "$path.items")
            if (items.isEmpty()) {
                add(TsiptvIssueCode.W_FIELD, "$path.items", "No valid banner is left; the section is skipped.")
                return null
            }
        } else {
            if (hasItems) add(TsiptvIssueCode.W_FIELD, "$path.items", "\"items\" is for hero sections only; it is ignored.")
            if (!hasQuery) {
                add(TsiptvIssueCode.W_FIELD, "$path.query", "The section has no query; it is skipped.")
                return null
            }
            query = query(obj["query"], "$path.query", includeTypes) ?: return null
        }

        val from = query?.from
        return TsiptvSection(
            type = type,
            id = obj["id"].let { i ->
                if (i.isAbsent()) return@let null
                i.asStr()?.takeIf(TsiptvRules::isId) ?: run {
                    add(TsiptvIssueCode.W_FIELD, "$path.id", "Invalid section id; it is ignored.")
                    null
                }
            },
            title = optText(obj, "title", path),
            subtitle = optText(obj, "subtitle", path),
            query = query,
            items = items,
            card = cardStyle(obj["card"], "$path.card").takeIf { type != TsiptvSectionType.HERO },
            seeAll = type == TsiptvSectionType.ROW && (optBool(obj, "seeAll", path) ?: true),
            groupChips = type == TsiptvSectionType.GRID &&
                    (from == TsiptvQuerySource.CHANNELS || from == TsiptvQuerySource.RADIO) &&
                    (optBool(obj, "groupChips", path) ?: false),
        )
    }

    private fun heroItems(el: JsonElement?, path: String): List<TsiptvHeroItem> {
        val array = el.asArr() ?: return emptyList()
        val out = ArrayList<TsiptvHeroItem>()
        for ((index, item) in array.withIndex()) {
            if (out.size >= TsiptvLimits.MAX_HERO_ITEMS) {
                add(TsiptvIssueCode.W_LIMIT, path, "More than ${TsiptvLimits.MAX_HERO_ITEMS} banners; the rest were dropped.")
                break
            }
            val itemPath = "$path[$index]"
            val obj = item.asObj() ?: run {
                add(TsiptvIssueCode.W_FIELD, itemPath, "A banner must be an object; it is dropped.")
                null
            } ?: continue
            val image = obj["image"].asStr()?.trimJs()
            if (image == null || !TsiptvRules.isHttpUrl(image)) {
                add(TsiptvIssueCode.E_URL, "$itemPath.image", "$URL_MESSAGE The banner is dropped.")
                continue
            }
            out += TsiptvHeroItem(
                image = image,
                title = optText(obj, "title", itemPath),
                subtitle = optText(obj, "subtitle", itemPath),
                target = obj["target"].let { t ->
                    if (t.isAbsent()) return@let null
                    t.asStr()?.takeIf(TsiptvRules::isItemRef) ?: run {
                        add(TsiptvIssueCode.W_FIELD, "$itemPath.target", "Not an item id; the banner is decorative.")
                        null
                    }
                },
                autoplay = optBool(obj, "autoplay", itemPath) ?: false,
            )
        }
        return out
    }

    private fun query(el: JsonElement?, path: String, includeTypes: Map<String, TsiptvIncludeType>): TsiptvQuery? {
        val obj = el.asObj() ?: run {
            add(TsiptvIssueCode.W_FIELD, path, "\"query\" must be an object; the section is skipped.")
            return null
        }
        val fromName = obj["from"].asStr()?.trimJs()
        if (fromName == null) {
            add(TsiptvIssueCode.W_FIELD, "$path.from", "\"from\" is missing; the section is skipped.")
            return null
        }
        val from = QUERY_SOURCES[fromName] ?: run {
            add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.from", "Unknown query source; the section is skipped.")
            return null
        }

        val includeEl = obj["include"]
        var include: String? = null
        if (!includeEl.isAbsent()) {
            include = includeEl.asStr()?.trimJs()
            val includeType = include?.let { includeTypes[it] }
            if (includeType == null) {
                add(TsiptvIssueCode.W_QUERY_REF, "$path.include", "No include with this id; the section is skipped.")
                return null
            }
            if (includeType == TsiptvIncludeType.XMLTV) {
                add(TsiptvIssueCode.W_QUERY_REF, "$path.include", "An xmltv include has guide data only; the section is skipped.")
                return null
            }
            if (from == TsiptvQuerySource.CATALOG && includeType != TsiptvIncludeType.STREMIO) {
                add(TsiptvIssueCode.W_QUERY_REF, "$path.include", "A catalog query needs a stremio include; the section is skipped.")
                return null
            }
        }

        var catalog: TsiptvCatalogRef? = null
        if (from == TsiptvQuerySource.CATALOG) {
            if (include == null) {
                add(TsiptvIssueCode.W_FIELD, "$path.include", "A catalog query needs \"include\"; the section is skipped.")
                return null
            }
            val cObj = obj["catalog"].asObj()
            val cType = shortText(cObj?.get("type").asStr(), "$path.catalog.type")?.takeIf { TsiptvRules.lengthIn(it, 1, 50) }
            val cId = shortText(cObj?.get("id").asStr(), "$path.catalog.id")?.takeIf { TsiptvRules.lengthIn(it, 1, 200) }
            if (cObj == null || cType == null || cId == null) {
                add(TsiptvIssueCode.W_FIELD, "$path.catalog", "A catalog query needs \"catalog\" with a type and an id; the section is skipped.")
                return null
            }
            catalog = TsiptvCatalogRef(cType, cId, optString(cObj, "genre", "$path.catalog", maxLength = 100))
        } else if (!obj["catalog"].isAbsent()) {
            add(TsiptvIssueCode.W_FIELD, "$path.catalog", "\"catalog\" is for catalog queries only; it is ignored.")
        }

        val ids = obj["ids"].let { i ->
            if (i.isAbsent()) return@let null
            stringList(obj, "ids", path, maxCount = TsiptvLimits.MAX_QUERY_IDS, maxLength = 520, check = TsiptvRules::isItemRef)
                .takeIf { it.isNotEmpty() }
                ?: run {
                    add(TsiptvIssueCode.W_FIELD, "$path.ids", "No valid item id is left; the filter is ignored.")
                    null
                }
        }
        val defaultSort = if (from == TsiptvQuerySource.CONTINUE_WATCHING) TsiptvQuerySort.RECENT else TsiptvQuerySort.SOURCE
        val sort = obj["sort"].let { s ->
            if (s.isAbsent()) return@let defaultSort
            val value = QUERY_SORTS[s.asStr()?.trimJs()] ?: run {
                add(TsiptvIssueCode.W_UNKNOWN_TYPE, "$path.sort", "Unknown sort; \"source\" is used.")
                return@let TsiptvQuerySort.SOURCE
            }
            if (value == TsiptvQuerySort.RECENT && from != TsiptvQuerySource.CONTINUE_WATCHING) {
                add(TsiptvIssueCode.W_FIELD, "$path.sort", "\"recent\" is for continueWatching only; \"source\" is used.")
                TsiptvQuerySort.SOURCE
            } else {
                value
            }
        }
        return TsiptvQuery(
            from = from,
            include = include,
            catalog = catalog,
            ids = ids,
            groups = filterList(obj, "groups", path, 100),
            genres = filterList(obj, "genres", path, 50),
            tags = filterList(obj, "tags", path, 50),
            sort = sort,
            limit = optInt(obj, "limit", path, 1..TsiptvLimits.MAX_QUERY_LIMIT),
        )
    }

    private fun filterList(obj: JsonObject, key: String, parent: String, maxLength: Int): List<String>? {
        if (obj[key].isAbsent()) return null
        return stringList(obj, key, parent, maxCount = 20, maxLength = maxLength).takeIf { it.isNotEmpty() } ?: run {
            add(TsiptvIssueCode.W_FIELD, "$parent.$key", "No valid entry is left; the filter is ignored.")
            null
        }
    }

    companion object {
        // Worded without "://" so that no issue text ever looks like, or contains, a URL.
        private const val URL_MESSAGE = "Must be an absolute http or https URL of at most 2,048 characters."
        private const val DEFAULT_CATCHUP_DAYS = 7
        private const val RESERVED_INCLUDE_ID_PREFIX = "epg-"
        private val LINE_BREAKS = Regex("\\s*[\\r\\n]+\\s*")

        /** An integer written with more digits than a Long holds (`1e20` or 25 digits). */
        private val HUGE_INTEGER = Regex("^-?[0-9]+(\\.0+)?([eE]\\+?[0-9]+)?$")

        /** §8.4 recognised hints; the HLS spellings map to the Media3 constant F1 uses. */
        private val RECOGNISED_MIME_TYPES = mapOf(
            "application/x-mpegurl" to StreamMimeTypes.HLS,
            "application/vnd.apple.mpegurl" to StreamMimeTypes.HLS,
            "application/dash+xml" to StreamMimeTypes.DASH,
            "video/mp4" to "video/mp4",
            "video/mp2t" to StreamMimeTypes.MPEG_TS,
            "video/webm" to "video/webm",
            "video/x-matroska" to "video/x-matroska",
            "audio/aac" to "audio/aac",
            "audio/mpeg" to "audio/mpeg",
            "audio/ogg" to "audio/ogg",
        )

        private val QUERY_SOURCES = TsiptvQuerySource.entries.associateBy {
            when (it) {
                TsiptvQuerySource.CHANNELS -> "channels"
                TsiptvQuerySource.RADIO -> "radio"
                TsiptvQuerySource.MOVIES -> "movies"
                TsiptvQuerySource.SERIES -> "series"
                TsiptvQuerySource.CATALOG -> "catalog"
                TsiptvQuerySource.CONTINUE_WATCHING -> "continueWatching"
                TsiptvQuerySource.FAVORITES -> "favorites"
            }
        }

        private val QUERY_SORTS = TsiptvQuerySort.entries.associateBy {
            when (it) {
                TsiptvQuerySort.SOURCE -> "source"
                TsiptvQuerySort.NAME -> "name"
                TsiptvQuerySort.NUMBER -> "number"
                TsiptvQuerySort.YEAR -> "year"
                TsiptvQuerySort.YEAR_DESC -> "yearDesc"
                TsiptvQuerySort.RECENT -> "recent"
            }
        }

        private fun fmt(value: Double): String =
            if (value == floor(value)) value.toLong().toString() else value.toString()

        /** [text] cut to [max] code points, or null when it already fits. */
        fun truncateCodePoints(text: String, max: Int): String? {
            var count = 0
            var i = 0
            while (i < text.length) {
                if (count == max) return text.substring(0, i)
                val c = text[i]
                i += if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
                count++
            }
            return null
        }

        /**
         * The effective headers of a stream (§5.5, §8.0): the stream's [own] headers first, then
         * the item [defaults] whose name (case-insensitive) the stream does not set, in document
         * order, at most [TsiptvLimits.MAX_HEADERS] distinct names in all.
         *
         * @return the headers, and how many item defaults were dropped by the limit
         */
        fun mergeHeaders(defaults: Map<String, String>, own: Map<String, String>): Pair<Map<String, String>, Int> {
            if (defaults.isEmpty()) return own to 0
            val merged = LinkedHashMap<String, String>()
            val names = HashSet<String>()
            own.forEach { (name, value) ->
                if (names.add(name.lowercase())) merged[name] = value
            }
            var dropped = 0
            defaults.forEach { (name, value) ->
                if (name.lowercase() in names) return@forEach
                if (merged.size >= TsiptvLimits.MAX_HEADERS) {
                    dropped++
                } else {
                    names += name.lowercase()
                    merged[name] = value
                }
            }
            return merged to dropped
        }
    }
}
