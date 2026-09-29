package tss.t.tsiptv.core.parser.tsiptv

/*
 * Tree-wide include rules of spec §9.3: depth, cycles, include count, bytes fetched per refresh
 * and merged item counts. Pure bookkeeping, no network: step 2 (TsiptvSourceResolver) drives the
 * fetches and asks [TsiptvIncludeGuard] before each one; [TsiptvIncludeTreePlanner] is a complete
 * depth-first walk over an injected loader that step 2 can use as is.
 */

/** Normalised URL used to recognise the same document on the include path (§9.3). */
object TsiptvUrlKey {
    /**
     * Scheme ignored (`http` and `https` name the same document for cycle purposes), user info
     * dropped, host lower-cased, default port dropped, fragment dropped, empty path = `/`.
     * Path and query are kept as written (they are case-sensitive on most servers).
     */
    fun of(url: String): String {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        val scheme = if (schemeEnd > 0) trimmed.substring(0, schemeEnd).lowercase() else ""
        val rest = (if (schemeEnd > 0) trimmed.substring(schemeEnd + 3) else trimmed).substringBefore('#')
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        var authority = rest.substring(0, authorityEnd).substringAfterLast('@').lowercase()
        authority = when (scheme) {
            "http" -> authority.removeSuffix(":80")
            "https" -> authority.removeSuffix(":443")
            else -> authority
        }
        val tail = rest.substring(authorityEnd)
        val pathAndQuery = if (tail.isEmpty() || tail.startsWith("?")) "/$tail" else tail
        return authority + pathAndQuery
    }
}

/** PRD §1 preview: "This source contacts these servers". */
object TsiptvHosts {
    /**
     * Distinct hosts (lower-case, without port or user info) of [rootUrl] and of every include
     * and EPG URL of [document], in first-seen order. Stream and image hosts are not listed.
     */
    fun contacted(rootUrl: String?, document: TsiptvSourceDocument): List<String> {
        val urls = listOfNotNull(rootUrl) + document.includes.map { it.url } + document.epg.map { it.url }
        return urls.mapNotNull(::hostOf).distinct()
    }

    fun hostOf(url: String): String? {
        val afterScheme = url.trim().substringAfter("://", "")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').let { a ->
            if (a.startsWith("[")) a.substringBefore(']') + "]" else a.substringBefore(':')
        }
        return host.lowercase().takeIf { it.isNotEmpty() }
    }
}

/**
 * Where a document sits in the include tree.
 *
 * @property depth 1 for the root document (§9.3)
 * @property urlKeys [TsiptvUrlKey]s of the documents on the path from the root to this one, root first
 * @property idPath Include ids from the root to this document (empty for the root)
 */
data class TsiptvIncludeContext(
    val depth: Int,
    val urlKeys: List<String>,
    val idPath: List<String>,
) {
    /** Chained include id, e.g. `cinema` or `cinema:partner` (`tsiptv_includes.includePath`). */
    val includePath: String get() = idPath.joinToString(TsiptvIds.NAMESPACE_SEPARATOR.toString())

    /** The pool id of an item of this document: `cinema:partner:item` (§9.3). */
    fun namespaced(itemId: String): String = TsiptvIds.namespaced(idPath, itemId)

    /** Prefixes a JSON path with this document's include path so issues of nested documents stay findable. */
    fun issuePath(path: String): String = when {
        idPath.isEmpty() -> path
        path.isEmpty() -> "[$includePath]"
        else -> "[$includePath] $path"
    }

    companion object {
        fun root(rootUrl: String?) = TsiptvIncludeContext(1, listOfNotNull(rootUrl?.let(TsiptvUrlKey::of)), emptyList())
    }
}

sealed interface TsiptvIncludeDecision {
    /** Fetch it; items and nested includes of the included document use [context]. */
    data class Accepted(val context: TsiptvIncludeContext) : TsiptvIncludeDecision

    /** Skip it; [issue] is already recorded in the guard's report. */
    data class Refused(val issue: TsiptvIssue) : TsiptvIncludeDecision
}

/** Pools with a tree-wide count limit (§9.3). Radio channels count as channels. */
enum class TsiptvPoolKind(val limit: Int) {
    CHANNEL(TsiptvLimits.MAX_MERGED_CHANNELS),
    MOVIE(TsiptvLimits.MAX_MERGED_MOVIES),
    SERIES(TsiptvLimits.MAX_MERGED_SERIES),
}

/**
 * Enforces §9.3 across one import or refresh. One instance per refresh; not thread-safe
 * (callers fetching in parallel must call it from one coroutine, or serialise the calls).
 *
 * Rules, all counted over every include type:
 * - an included document is one level deeper than the document that includes it; deeper than
 *   [TsiptvLimits.MAX_INCLUDE_DEPTH] (root = 1) → `E_INCLUDE_CYCLE`;
 * - an include whose [TsiptvUrlKey] is already on its path → `E_INCLUDE_CYCLE`;
 * - more than [TsiptvLimits.MAX_INCLUDES_IN_TREE] accepted includes → `W_LIMIT` for the rest.
 *   `epg` links are not counted here (they have their own per-document limit of 10);
 * - more than [TsiptvLimits.MAX_FETCHED_BYTES_PER_REFRESH] bytes fetched in the refresh, the root
 *   included → `E_INCLUDE` on the entry that went over (a fetch failure: last good copy kept);
 * - any other fetch failure → `E_INCLUDE` ([recordFetchFailed]);
 * - more merged items than [TsiptvPoolKind.limit] → `W_LIMIT`, extra items dropped.
 */
class TsiptvIncludeGuard(rootUrl: String?) {
    val root: TsiptvIncludeContext = TsiptvIncludeContext.root(rootUrl)

    private val issues = ArrayList<TsiptvIssue>()
    private val admittedItems = HashMap<TsiptvPoolKind, Int>()

    var includeCount: Int = 0
        private set
    var fetchedBytes: Long = 0
        private set

    val report: TsiptvValidationReport get() = TsiptvValidationReport(issues.toList())

    /**
     * Decides whether [include] (entry [index] of the `includes` of the document at [parent]) is
     * followed. Accepted includes count towards the tree limit.
     */
    fun admit(parent: TsiptvIncludeContext, include: TsiptvInclude, index: Int): TsiptvIncludeDecision {
        val path = parent.issuePath("includes[$index]")
        if (parent.depth + 1 > TsiptvLimits.MAX_INCLUDE_DEPTH) {
            return refuse(
                TsiptvIssueCode.E_INCLUDE_CYCLE, path,
                "Includes may nest at most ${TsiptvLimits.MAX_INCLUDE_DEPTH} documents deep; this one is skipped.",
            )
        }
        val key = TsiptvUrlKey.of(include.url)
        if (key in parent.urlKeys) {
            return refuse(
                TsiptvIssueCode.E_INCLUDE_CYCLE, "$path.url",
                "This include is already on the include path (a cycle); it is skipped.",
            )
        }
        if (includeCount >= TsiptvLimits.MAX_INCLUDES_IN_TREE) {
            return refuse(
                TsiptvIssueCode.W_LIMIT, path,
                "More than ${TsiptvLimits.MAX_INCLUDES_IN_TREE} includes in the whole source; this one is skipped.",
            )
        }
        includeCount++
        return TsiptvIncludeDecision.Accepted(
            TsiptvIncludeContext(parent.depth + 1, parent.urlKeys + key, parent.idPath + include.id)
        )
    }

    /** Whether another fetch may start in this refresh. */
    val canFetch: Boolean get() = fetchedBytes < TsiptvLimits.MAX_FETCHED_BYTES_PER_REFRESH

    /** Bytes of the budget not yet used, e.g. to cap the next download. */
    val remainingBudget: Long get() = maxOf(0L, TsiptvLimits.MAX_FETCHED_BYTES_PER_REFRESH - fetchedBytes)

    /**
     * §9.3: the root document counts towards the 50 MiB budget too (decompressed bytes; a
     * `304 Not Modified` counts 0). Call once per refresh, before the includes.
     */
    fun recordRootFetched(bytes: Long) {
        fetchedBytes += bytes
    }

    /**
     * Counts [bytes] (decompressed; `304` = 0) fetched for [entry] (`includes[i]` / `epg[i]`, see
     * [includeEntry], [epgEntry]) of the document at [parent]: includes, EPG links, Stremio
     * manifests. Returns false when this fetch goes over the refresh budget: its content must not
     * be used (the last good copy is kept) and an `E_INCLUDE` is recorded.
     */
    fun recordFetched(parent: TsiptvIncludeContext, entry: String, bytes: Long): Boolean {
        fetchedBytes += bytes
        if (fetchedBytes <= TsiptvLimits.MAX_FETCHED_BYTES_PER_REFRESH) return true
        issues += TsiptvIssue(
            FETCH_FAILED_CODE, parent.issuePath(entry),
            "More than 50 MiB fetched in this refresh; this include was not loaded (its last good copy is used if there is one).",
        )
        return false
    }

    /** [entry] of the document at [parent] was not fetched because the refresh budget is already spent. */
    fun recordBudgetSpent(parent: TsiptvIncludeContext, entry: String) {
        issues += TsiptvIssue(
            FETCH_FAILED_CODE, parent.issuePath(entry),
            "50 MiB were already fetched in this refresh; this include was not loaded (its last good copy is used if there is one).",
        )
    }

    /**
     * Reserves room for [count] items of [kind] contributed by the document at [context].
     * @return how many of them may be added (the first ones, in document order)
     */
    fun admitItems(kind: TsiptvPoolKind, count: Int, context: TsiptvIncludeContext): Int {
        val used = admittedItems[kind] ?: 0
        val allowed = minOf(count, maxOf(0, kind.limit - used))
        admittedItems[kind] = used + allowed
        if (allowed < count) {
            val dropped = count - allowed
            issues += TsiptvIssue(
                TsiptvIssueCode.W_LIMIT, context.issuePath(""),
                "More than ${kind.limit} ${kind.name.lowercase()} items in the whole source; $dropped were dropped.",
                dropped,
            )
        }
        return allowed
    }

    /**
     * Records the parse result of the included document at [context] (entry [index] of its
     * parent's `includes`). A valid document contributes its item errors and warnings, prefixed
     * with its include path. A rejected one becomes a single `E_INCLUDE` on the including
     * document: a failing include never fails the root (§9.3), so a nested document error must
     * not appear as a document-level issue of the tree.
     */
    fun recordParse(parent: TsiptvIncludeContext, index: Int, context: TsiptvIncludeContext, result: TsiptvParseResult) {
        when (result) {
            // §9.3: meta, appearance and layout of an included document are ignored, so their
            // warnings (W_CONTRAST, W_UNKNOWN_TYPE of a section, …) would only confuse the creator.
            is TsiptvParseResult.Success -> result.report.issues
                .filterNot { isIgnoredPartOfIncludedDocument(it.path) }
                .forEach { issues += it.copy(path = context.issuePath(it.path)) }

            // Size cap / corrupt gzip: a fetch failure (keep the last good copy), not a rejection.
            is TsiptvParseResult.Failure -> if (result.cause.isFetchFailure) {
                recordFetchFailed(parent, includeEntry(index))
            } else {
                issues += TsiptvIssue(
                    TsiptvIssueCode.E_INCLUDE, parent.issuePath("includes[$index]"),
                    "The included document was rejected (${result.report.primaryDocumentError}); it is skipped.", 1,
                )
            }
        }
    }

    /**
     * §9.3 fetch failure of [entry] (`includes[i]` / `epg[i]`) of the document at [parent]: network
     * error, timeout, non-2xx other than 304, over its size cap, decompression failure. The caller
     * keeps the last good copy (stale); nothing is dropped here, so `droppedItems` is 0.
     */
    fun recordFetchFailed(parent: TsiptvIncludeContext, entry: String) {
        issues += TsiptvIssue(
            FETCH_FAILED_CODE, parent.issuePath(entry),
            "The include could not be loaded; its last good copy is used if there is one.",
        )
    }

    /**
     * Applies the merged item limits (§9.3) to [document] contributed at [context]: the first
     * items in document order are kept, the rest are dropped with `W_LIMIT`.
     */
    fun admitDocumentItems(document: TsiptvSourceDocument, context: TsiptvIncludeContext): TsiptvSourceDocument {
        val channels = admitItems(TsiptvPoolKind.CHANNEL, document.channels.size, context)
        val movies = admitItems(TsiptvPoolKind.MOVIE, document.movies.size, context)
        val series = admitItems(TsiptvPoolKind.SERIES, document.series.size, context)
        if (channels == document.channels.size && movies == document.movies.size && series == document.series.size) {
            return document
        }
        return document.copy(
            channels = document.channels.take(channels),
            movies = document.movies.take(movies),
            series = document.series.take(series),
        )
    }

    private fun refuse(code: TsiptvIssueCode, path: String, message: String): TsiptvIncludeDecision.Refused {
        val issue = TsiptvIssue(code, path, message, 1)
        issues += issue
        return TsiptvIncludeDecision.Refused(issue)
    }

    companion object {
        /** Code of a failed include/EPG fetch, including size cap and budget (spec §9.3, §10). */
        val FETCH_FAILED_CODE: TsiptvIssueCode = TsiptvIssueCode.E_INCLUDE

        /** Issue path of entry [index] of `includes`. */
        fun includeEntry(index: Int) = "includes[$index]"

        /** Issue path of entry [index] of `epg`. */
        fun epgEntry(index: Int) = "epg[$index]"

        private val IGNORED_PARTS = listOf("meta", "appearance", "layout")

        internal fun isIgnoredPartOfIncludedDocument(path: String): Boolean =
            IGNORED_PARTS.any { path == it || path.startsWith("$it.") }
    }
}

/** One include of the planned tree. */
data class TsiptvIncludeNode(
    val include: TsiptvInclude,
    /** null when refused. */
    val context: TsiptvIncludeContext?,
    val status: Status,
    /** The included document (`tsiptv-source` includes that loaded). */
    val document: TsiptvSourceDocument? = null,
    val children: List<TsiptvIncludeNode> = emptyList(),
) {
    enum class Status {
        /** Accepted; for `tsiptv-source`, loaded and valid. Other types still have to be fetched. */
        ACCEPTED,

        /** Skipped by a tree rule (cycle, depth, count). */
        REFUSED,

        /**
         * A `tsiptv-source` include whose document was fetched but rejected by its own validation
         * (§9.3 "rejected nested documents"): **drop** the include, including any stored copy.
         */
        REJECTED,

        /**
         * A `tsiptv-source` include that could not be fetched (network, timeout, non-2xx, size cap,
         * budget, corrupt gzip — §9.3 "failure"): **keep the last good copy** (stale), if any.
         */
        FETCH_FAILED,
    }
}

/**
 * @property root The root document, after the merged item limits
 * @property guard The guard of this refresh, still in use: step 2 continues with it for the
 *   includes it fetches itself (m3u, xmltv): `recordFetched`, `admitItems`, `recordFetchFailed`
 */
class TsiptvIncludeTree(
    val root: TsiptvSourceDocument,
    val nodes: List<TsiptvIncludeNode>,
    val guard: TsiptvIncludeGuard,
) {
    /** Tree-level issues so far (a snapshot of [guard]'s report). */
    val report: TsiptvValidationReport get() = guard.report

    /**
     * §9.3 / §12 adult rule over the documents the planner loaded. The root needs the 18+
     * confirmation when the root, any accepted included TS IPTV Source at any depth, or any
     * accepted `stremio` include whose manifest says `behaviorHints.adult: true` is adult.
     * (`meta.adult` present but not a boolean was already read as `true` by the parser.)
     *
     * @param stremioAdultIncludePaths [TsiptvIncludeContext.includePath]s of the `stremio`
     *   includes whose fetched manifest has `behaviorHints.adult: true` (manifests are fetched by
     *   step 2, not by the planner)
     */
    fun adultCheck(stremioAdultIncludePaths: Set<String> = emptySet()): TsiptvAdultCheck {
        val adultIncludes = flatten().filter { node ->
            val path = node.context?.includePath ?: return@filter false
            node.status == TsiptvIncludeNode.Status.ACCEPTED && when (node.include.type) {
                TsiptvIncludeType.TSIPTV_SOURCE -> node.document?.meta?.adult == true
                TsiptvIncludeType.STREMIO -> path in stremioAdultIncludePaths
                else -> false
            }
        }.mapNotNull { it.context?.includePath }
        return TsiptvAdultCheck(rootIsAdult = root.meta.adult, adultIncludePaths = adultIncludes)
    }

    /** [adultCheck] without Stremio manifest information. */
    val requiresAdultConfirmation: Boolean get() = adultCheck().requiresConfirmation

    /** Every node, depth-first in document order. */
    fun flatten(): List<TsiptvIncludeNode> {
        val out = ArrayList<TsiptvIncludeNode>()
        collect(nodes, out)
        return out
    }

    private fun collect(list: List<TsiptvIncludeNode>, out: MutableList<TsiptvIncludeNode>) {
        list.forEach {
            out += it
            collect(it.children, out)
        }
    }
}

/**
 * Result of the §9.3 adult rule.
 *
 * @property adultIncludePaths Include paths (`cinema`, `cinema:partner`) that make the source adult;
 *   on a refresh of an unconfirmed source their new content must be withheld until confirmed
 */
data class TsiptvAdultCheck(
    val rootIsAdult: Boolean,
    val adultIncludePaths: List<String>,
) {
    val requiresConfirmation: Boolean get() = rootIsAdult || adultIncludePaths.isNotEmpty()
}

/**
 * What a loader returns for a fetched `tsiptv-source` include.
 *
 * @property byteCount Bytes fetched (decompressed), counted towards the 50 MiB refresh budget
 */
data class TsiptvFetchedDocument(
    val result: TsiptvParseResult,
    val byteCount: Long,
)

object TsiptvIncludeTreePlanner {
    /**
     * Walks the include tree of [root] depth-first in document order, applying every rule of
     * [TsiptvIncludeGuard]: depth, cycles, include count, the 50 MiB fetch budget (checked before
     * each load, counted after it) and the merged item limits (root items first, then each nested
     * document in walk order; [TsiptvIncludeNode.document] and [TsiptvIncludeTree.root] are
     * already trimmed).
     *
     * [load] is called for accepted `tsiptv-source` includes only: it fetches and parses the
     * document (with [TsiptvSourceParser.parse], passing the include URL as `documentUrl`) and
     * returns it with its size, or null when the fetch failed (recorded as `E_INCLUDE`). Other
     * include types, and every document's EPG links, are left to the caller, which fetches them
     * through [TsiptvIncludeTree.guard].
     *
     * @param rootBytes Decompressed size of the root document as fetched in this refresh (`304` = 0);
     *   it counts towards the 50 MiB budget (§9.3)
     */
    suspend fun plan(
        root: TsiptvSourceDocument,
        rootUrl: String?,
        rootBytes: Long = 0,
        load: suspend (TsiptvInclude, TsiptvIncludeContext) -> TsiptvFetchedDocument?,
    ): TsiptvIncludeTree {
        val guard = TsiptvIncludeGuard(rootUrl)
        guard.recordRootFetched(rootBytes)
        val admittedRoot = guard.admitDocumentItems(root, guard.root)
        val nodes = walk(admittedRoot, guard.root, guard, load)
        return TsiptvIncludeTree(admittedRoot, nodes, guard)
    }

    private suspend fun walk(
        document: TsiptvSourceDocument,
        context: TsiptvIncludeContext,
        guard: TsiptvIncludeGuard,
        load: suspend (TsiptvInclude, TsiptvIncludeContext) -> TsiptvFetchedDocument?,
    ): List<TsiptvIncludeNode> = document.includes.mapIndexed { index, include ->
        when (val decision = guard.admit(context, include, index)) {
            is TsiptvIncludeDecision.Refused ->
                TsiptvIncludeNode(include, null, TsiptvIncludeNode.Status.REFUSED)

            is TsiptvIncludeDecision.Accepted -> {
                val child = decision.context
                if (include.type != TsiptvIncludeType.TSIPTV_SOURCE) {
                    TsiptvIncludeNode(include, child, TsiptvIncludeNode.Status.ACCEPTED)
                } else {
                    loadNested(include, index, context, child, guard, load)
                }
            }
        }
    }

    private suspend fun loadNested(
        include: TsiptvInclude,
        index: Int,
        parent: TsiptvIncludeContext,
        child: TsiptvIncludeContext,
        guard: TsiptvIncludeGuard,
        load: suspend (TsiptvInclude, TsiptvIncludeContext) -> TsiptvFetchedDocument?,
    ): TsiptvIncludeNode {
        val fetchFailed = TsiptvIncludeNode(include, child, TsiptvIncludeNode.Status.FETCH_FAILED)
        val entry = TsiptvIncludeGuard.includeEntry(index)
        if (!guard.canFetch) {
            guard.recordBudgetSpent(parent, entry)
            return fetchFailed
        }
        val fetched = load(include, child)
        if (fetched == null) {
            guard.recordFetchFailed(parent, entry)
            return fetchFailed
        }
        if (!guard.recordFetched(parent, entry, fetched.byteCount)) return fetchFailed
        val result = fetched.result
        // Over 5 MiB or undecompressable is a fetch failure (keep stale), not a rejection (drop).
        if (result is TsiptvParseResult.Failure && result.cause.isFetchFailure) {
            guard.recordFetchFailed(parent, entry)
            return fetchFailed
        }
        guard.recordParse(parent, index, child, result)
        val nested = result.documentOrNull
            ?: return TsiptvIncludeNode(include, child, TsiptvIncludeNode.Status.REJECTED)
        val admitted = guard.admitDocumentItems(nested, child)
        return TsiptvIncludeNode(
            include, child, TsiptvIncludeNode.Status.ACCEPTED,
            document = admitted,
            children = walk(admitted, child, guard, load),
        )
    }
}
