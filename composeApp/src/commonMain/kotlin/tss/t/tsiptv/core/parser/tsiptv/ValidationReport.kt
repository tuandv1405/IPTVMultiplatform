package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.serialization.Serializable

/** The three validation levels of spec §10. */
@Serializable
enum class TsiptvIssueLevel {
    /** The whole file is rejected; nothing is stored. */
    DOCUMENT,

    /** The offending item, stream, field or include is dropped; the rest is imported. */
    ITEM,

    /** Nothing is dropped beyond what the code says; reported only. */
    WARNING,
}

/**
 * Every code of spec §10, with its level. The PRD maps each one to a `source_issue_*` string
 * (and the document codes to `source_error_*`); the enum name is the code as written in the spec.
 */
@Serializable
enum class TsiptvIssueCode(val level: TsiptvIssueLevel) {
    E_NOT_JSON(TsiptvIssueLevel.DOCUMENT),
    E_NOT_SOURCE(TsiptvIssueLevel.DOCUMENT),
    E_VERSION(TsiptvIssueLevel.DOCUMENT),
    E_TOO_LARGE(TsiptvIssueLevel.DOCUMENT),
    E_ID(TsiptvIssueLevel.DOCUMENT),
    E_META(TsiptvIssueLevel.DOCUMENT),
    E_EMPTY(TsiptvIssueLevel.DOCUMENT),

    E_ITEM_ID(TsiptvIssueLevel.ITEM),
    E_DUPLICATE_ID(TsiptvIssueLevel.ITEM),
    E_ITEM_NAME(TsiptvIssueLevel.ITEM),
    E_NO_STREAM(TsiptvIssueLevel.ITEM),
    E_URL(TsiptvIssueLevel.ITEM),
    E_HEADER_FORBIDDEN(TsiptvIssueLevel.ITEM),
    E_DRM(TsiptvIssueLevel.ITEM),
    E_INCLUDE(TsiptvIssueLevel.ITEM),
    E_INCLUDE_CYCLE(TsiptvIssueLevel.ITEM),

    W_UNKNOWN_TYPE(TsiptvIssueLevel.WARNING),
    W_QUERY_REF(TsiptvIssueLevel.WARNING),
    W_LIMIT(TsiptvIssueLevel.WARNING),
    W_CONTRAST(TsiptvIssueLevel.WARNING),
    W_TEXT(TsiptvIssueLevel.WARNING),
    W_FIELD(TsiptvIssueLevel.WARNING),
}

/**
 * One finding.
 *
 * @property path JSON path of the offending value, e.g. `channels[1].url`, `meta.name`,
 *   `layout.home[2].query.from`; `""` for the root. Nested include documents are prefixed with
 *   their include path in brackets: `[cinema] movies[0].url`.
 * @property message Short English sentence for creators (the Details list). Never contains a
 *   URL, header value or key from the document (§12 "Credentials").
 * @property droppedItems How many content items (channels, movies, series, seasons, episodes)
 *   or includes this finding removed; 0 when only a field, stream, header or section was dropped.
 */
@Serializable
data class TsiptvIssue(
    val code: TsiptvIssueCode,
    val path: String,
    val message: String,
    val droppedItems: Int = 0,
) {
    val level: TsiptvIssueLevel get() = code.level

    /** `path — message`, the Details line format of the PRD. */
    fun detailLine(): String = if (path.isEmpty()) message else "$path — $message"
}

/**
 * The result of validating one document (or, with [plus], a whole include tree).
 */
@Serializable
data class TsiptvValidationReport(
    val issues: List<TsiptvIssue> = emptyList(),
) {
    val documentErrors: List<TsiptvIssue> get() = issues.filter { it.level == TsiptvIssueLevel.DOCUMENT }
    val itemErrors: List<TsiptvIssue> get() = issues.filter { it.level == TsiptvIssueLevel.ITEM }
    val warnings: List<TsiptvIssue> get() = issues.filter { it.level == TsiptvIssueLevel.WARNING }

    /** A document error was found: nothing may be stored. */
    val isRejected: Boolean get() = issues.any { it.level == TsiptvIssueLevel.DOCUMENT }

    /** "N items will be skipped" in the preview. */
    val skippedItemCount: Int get() = issues.sumOf { it.droppedItems }

    /** The first document error, which picks the user-facing message of the error dialog. */
    val primaryDocumentError: TsiptvIssueCode? get() = documentErrors.firstOrNull()?.code

    fun has(code: TsiptvIssueCode): Boolean = issues.any { it.code == code }

    fun count(code: TsiptvIssueCode): Int = issues.count { it.code == code }

    /** Details list / `reportJson`: at most [max] entries, document errors first, then item errors, then warnings. */
    fun details(max: Int = MAX_DETAILS): List<TsiptvIssue> =
        issues.sortedBy { it.level.ordinal }.take(max)

    operator fun plus(other: TsiptvValidationReport): TsiptvValidationReport =
        TsiptvValidationReport(issues + other.issues)

    companion object {
        /** PRD §3: the Details list shows at most 100 entries. */
        const val MAX_DETAILS = 100
    }
}
