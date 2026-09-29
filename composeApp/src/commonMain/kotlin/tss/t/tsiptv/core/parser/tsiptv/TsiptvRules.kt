package tss.t.tsiptv.core.parser.tsiptv

/**
 * Size and count limits of TS IPTV Source version 1 (docs/tsiptv-source-format.md §2, §4, §7, §8, §9).
 *
 * Readers MUST enforce them before building UI (§12). Per-document limits are applied by
 * [TsiptvSourceParser]; tree-wide limits by [TsiptvIncludeGuard].
 */
object TsiptvLimits {
    /** §2: any single document, root or included, after decompression. */
    const val MAX_DOCUMENT_BYTES: Long = 5L * 1024 * 1024

    // §4 per document
    const val MAX_CHANNELS = 10_000
    const val MAX_MOVIES = 5_000
    const val MAX_SERIES = 1_000
    const val MAX_EPG_LINKS = 10
    const val MAX_INCLUDES_PER_DOCUMENT = 20

    // §8.3
    const val MAX_EPISODES_PER_DOCUMENT = 20_000
    const val MAX_SEASONS = 100
    const val MAX_EPISODES_PER_SEASON = 500

    // §8.0, §8.4, §8.5, §8.7
    const val MAX_STREAMS = 10
    const val MAX_SUBTITLES = 30
    const val MAX_CLEARKEY_PAIRS = 20

    // §5
    const val MAX_TEXT_CHARS = 200
    const val MAX_LONG_TEXT_CHARS = 5_000
    const val MAX_LOCALIZED_ENTRIES = 30
    const val MAX_URL_CHARS = 2_048
    const val MAX_HEADERS = 20
    const val MAX_HEADER_NAME_CHARS = 100
    const val MAX_HEADER_VALUE_CHARS = 4_096

    // §7
    const val MAX_SECTIONS = 30
    const val MAX_HERO_ITEMS = 10
    const val MAX_QUERY_IDS = 500
    const val MAX_QUERY_LIMIT = 500
    const val DEFAULT_ROW_LIMIT = 20
    const val MAX_ROW_LIMIT = 100

    // §9.3, tree-wide
    const val MAX_INCLUDE_DEPTH = 3
    const val MAX_INCLUDES_IN_TREE = 20
    const val MAX_FETCHED_BYTES_PER_REFRESH: Long = 50L * 1024 * 1024
    const val MAX_MERGED_CHANNELS = 20_000
    const val MAX_MERGED_MOVIES = 5_000
    const val MAX_MERGED_SERIES = 1_000

    // prd-tsiptv-source-format.md §5: per include type, after decompression
    const val MAX_M3U_INCLUDE_BYTES: Long = 20L * 1024 * 1024
    const val MAX_XMLTV_INCLUDE_BYTES: Long = 100L * 1024 * 1024

    const val DEFAULT_REFRESH_HOURS = 24
    const val MIN_REFRESH_HOURS = 1
    const val MAX_REFRESH_HOURS = 720
}

/** Lexical rules shared by the parser, the include guard and step-2 code (§5.3, §5.4, §5.2, §5.6). */
object TsiptvRules {
    private val ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val ITEM_REF_SEGMENT = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val HTTP_URL = Regex("^[Hh][Tt][Tt][Pp][Ss]?://[^\\s/?#]+[^\\s]*$")
    private val LANGUAGE_TAG = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$")
    private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    private val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
    private val MIME_TYPE = Regex("^[a-z]+/[A-Za-z0-9.+-]+$")
    private val HEX_32 = Regex("^[0-9A-Fa-f]{32}$")
    private val COUNTRY = Regex("^[A-Z]{2}$")
    private val FULL_DATE = Regex("^([0-9]{4})-([0-9]{2})-([0-9]{2})$")
    private val DATE_TIME = Regex(
        "^([0-9]{4}-[0-9]{2}-[0-9]{2})[Tt]([0-9]{2}):([0-9]{2}):([0-9]{2})(\\.[0-9]+)?([Zz]|[+-]([0-9]{2}):([0-9]{2}))$"
    )

    /** §5.4: `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`; no `:`. */
    fun isId(value: String): Boolean = ID.matches(value)

    /**
     * An item reference in `query.ids` / `hero.target`: an Id, or ids chained with `:` for
     * items that come from includes (`cinema:big-buck-bunny`, at most 4 segments as in the schema).
     */
    fun isItemRef(value: String): Boolean {
        val parts = value.split(':')
        return parts.size in 1..4 && parts.all { ITEM_REF_SEGMENT.matches(it) }
    }

    /** §5.3: absolute `http`/`https` URL (scheme case-insensitive), at most 2,048 characters. */
    fun isHttpUrl(value: String): Boolean =
        codePointCount(value) <= TsiptvLimits.MAX_URL_CHARS && HTTP_URL.matches(value) &&
            // HTTP_URL's \s is ASCII-only: reject every ECMA-262 whitespace (NBSP, U+FEFF, U+2028,
            // U+3000, …) and every control character (C0, DEL, C1) anywhere in the URL.
            value.none { it.isISOControl() || isEcmaWhitespace(it) }

    fun isLanguageTag(value: String): Boolean = LANGUAGE_TAG.matches(value)

    fun isColor(value: String): Boolean = COLOR.matches(value)

    /** §5.5: RFC 9110 token. */
    fun isHeaderName(value: String): Boolean =
        value.length <= TsiptvLimits.MAX_HEADER_NAME_CHARS && HEADER_NAME.matches(value)

    /** §5.5: names a source may never set; compared case-insensitively. */
    val FORBIDDEN_HEADERS: Set<String> = setOf("host", "content-length", "connection", "transfer-encoding")

    fun isForbiddenHeader(name: String): Boolean = name.lowercase() in FORBIDDEN_HEADERS

    /** §5.5: 0–4,096 characters, no CR or LF (nor other control characters an HTTP stack rejects). */
    fun isHeaderValue(value: String): Boolean =
        codePointCount(value) <= TsiptvLimits.MAX_HEADER_VALUE_CHARS && value.none { it != '\t' && it.isISOControl() }

    fun isMimeTypeSyntax(value: String): Boolean = MIME_TYPE.matches(value)

    fun isHex32(value: String): Boolean = HEX_32.matches(value)

    fun isCountry(value: String): Boolean = COUNTRY.matches(value)

    /** RFC 3339 full-date that exists in the calendar (`2026-02-30` is rejected). */
    fun isFullDate(value: String): Boolean {
        val match = FULL_DATE.matchEntire(value) ?: return false
        val (y, m, d) = match.destructured
        val year = y.toInt()
        val month = m.toInt()
        val day = d.toInt()
        if (month !in 1..12 || day < 1) return false
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val days = when (month) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= days
    }

    /**
     * RFC 3339 date-time (`2026-09-27T10:00:00Z`, `2026-09-27T10:00:00.5+07:00`) with RFC 3339
     * ranges (PRD Follow-up #3 row 5): a real calendar date, hour 00–23, minute and second 00–59
     * (no leap second `:60`), offset hours 00–23 and minutes 00–59. Lowercase `t`/`z` are accepted.
     * Callers trim surrounding whitespace first.
     */
    fun isDateTime(value: String): Boolean {
        val match = DATE_TIME.matchEntire(value) ?: return false
        val g = match.groupValues
        if (!isFullDate(g[1])) return false
        if (g[2].toInt() > 23 || g[3].toInt() > 59 || g[4].toInt() > 59) return false
        // Group 7/8 are empty for `Z`.
        if (g[7].isNotEmpty() && (g[7].toInt() > 23 || g[8].toInt() > 59)) return false
        return true
    }

    /** §9.2: a Stremio include URL must be a manifest URL whose path ends with `/manifest.json`. */
    fun isStremioManifestUrl(url: String): Boolean {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return false
        val afterScheme = url.substring(schemeEnd + 3)
        // The authority ends at the first of / ? #; only a '/' starts a real path
        // (`https://host?x=/manifest.json` and `https://host#/manifest.json` have an empty path).
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (authorityEnd < 0 || afterScheme[authorityEnd] != '/') return false
        val path = afterScheme.substring(authorityEnd).substringBefore('?').substringBefore('#')
        return path.endsWith("/manifest.json")
    }

    /** Length in Unicode code points (the spec's "characters"); a surrogate pair counts once. */
    fun codePointCount(value: String): Int {
        var count = 0
        var i = 0
        while (i < value.length) {
            i += if (value[i].isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return count
    }

    /** [codePointCount] of [value] is within [min]..[max]. */
    fun lengthIn(value: String, min: Int, max: Int): Boolean = codePointCount(value) in min..max

    /** §8.6: a catch-up `source` has 1–2,048 code points and no whitespace or control characters (C0, DEL, C1). */
    fun isCatchupSource(value: String): Boolean =
        lengthIn(value, 1, 2048) && value.none { isEcmaWhitespace(it) || it.isWhitespace() || it.isISOControl() }

    /**
     * ECMA-262 WhiteSpace + LineTerminator: exactly what the schema's `\S` treats as whitespace.
     * Unlike Kotlin's `isWhitespace()`, it includes U+FEFF and excludes U+001C–U+001F.
     */
    fun isEcmaWhitespace(c: Char): Boolean = when (c) {
        '\u0009', '\u000B', '\u000C', ' ', ' ', '﻿', // WhiteSpace (non-Zs)
        '\u000A', '\u000D', ' ', ' ',                       // LineTerminator
        ' ', ' ', ' ', '　' -> true                  // Zs
        else -> c in ' '..' '                                 // Zs
    }

    /** Trims [isEcmaWhitespace] characters from both ends (spec §4 "Blank strings"). */
    fun trimEcma(value: String): String = value.trim(::isEcmaWhitespace)

    /** C0 control characters, U+0000–U+001F. */
    fun isC0(c: Char): Boolean = c < ' '

    /** Starts with `http://` or `https://`, case-insensitively. */
    fun startsWithHttpScheme(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)
}

/** Id helpers for include namespacing (§9.3). */
object TsiptvIds {
    /** The separator readers put between an include id and the ids of its items. */
    const val NAMESPACE_SEPARATOR = ':'

    /** `includeId:itemId`, chained for nested includes (`archive:partner:item`). */
    fun namespaced(includePath: List<String>, itemId: String): String =
        if (includePath.isEmpty()) itemId else includePath.joinToString(":") + ":" + itemId

    /**
     * An id from another format (an M3U `tvg-id`, a name slug) made into an Id segment: every
     * character outside `[A-Za-z0-9._-]` becomes `_` (`ExampleTV.us@HD` → `ExampleTV.us_HD`).
     * Returns null when nothing usable is left.
     */
    fun sanitizeForeignId(raw: String): String? {
        val replaced = buildString(raw.length) {
            for (c in raw.trim()) {
                append(if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '_' || c == '-') c else '_')
            }
        }
        return replaced.takeIf { it.isNotEmpty() && it.any { c -> c.isLetterOrDigit() } }
    }
}
