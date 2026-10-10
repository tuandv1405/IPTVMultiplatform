package tss.t.tsiptv.core.tsiptv

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.parser.EPGParserFactory
import tss.t.tsiptv.core.parser.epg.XMLTVEPGParser
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.core.parser.tsiptv.LocalizedText
import tss.t.tsiptv.core.parser.tsiptv.TsiptvChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvChannelType
import tss.t.tsiptv.core.parser.tsiptv.TsiptvEpgLink
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIds
import tss.t.tsiptv.core.parser.tsiptv.TsiptvInclude
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIncludeContext
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIncludeDecision
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIncludeGuard
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIncludeType
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssue
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode
import tss.t.tsiptv.core.parser.tsiptv.TsiptvLimits
import tss.t.tsiptv.core.parser.tsiptv.TsiptvMovie
import tss.t.tsiptv.core.parser.tsiptv.TsiptvParseResult
import tss.t.tsiptv.core.parser.tsiptv.TsiptvPoolKind
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSeries
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSourceDocument
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSourceParser
import tss.t.tsiptv.core.parser.tsiptv.TsiptvValidationReport
import kotlin.coroutines.cancellation.CancellationException

/** JSON used for the F3 columns (stored model objects). Lenient on read. */
internal val TsiptvStorageJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}

/**
 * Input of one resolve (import or refresh).
 *
 * @property storedIncludes The previous include rows by path (empty on first import)
 * @property storedChannels The previous channels of this source (stale copies of `m3u` includes)
 * @property force Import and manual Refresh fetch every include, ignoring `refreshHours`
 * @property adultConfirmed The user already confirmed 18+ for this source
 * @property isImport First import: adult includes are reported to the caller, which asks before
 *   storing. On a refresh of an unconfirmed source they are withheld instead (spec §9.3).
 */
class TsiptvResolveInput(
    val playlistId: String,
    val document: TsiptvSourceDocument,
    val rootUrl: String?,
    val rootBytes: Long,
    val storedIncludes: Map<String, TsiptvIncludeRecord> = emptyMap(),
    val storedChannels: List<Channel> = emptyList(),
    val force: Boolean,
    val adultConfirmed: Boolean,
    val isImport: Boolean,
    val progress: (done: Int, total: Int) -> Unit = { _, _ -> },
)

/** Everything a resolve produced, ready to store (see [TsiptvStoredContent]). */
class TsiptvResolved(
    val channels: List<Channel>,
    val categories: List<Category>,
    val vodItems: List<TsiptvVodItemRecord>,
    val episodes: List<TsiptvEpisodeRecord>,
    val includes: List<TsiptvIncludeRecord>,
    /** Programme changes per guide (each guide keeps its last good copy, spec §9.3). */
    val guides: TsiptvGuidePlan,
    /** Tree-level issues (includes, limits, nested documents), paths prefixed as in spec §9.3. */
    val report: TsiptvValidationReport,
    /** Include paths whose content is adult (nested source `meta.adult`, addon `behaviorHints.adult`). */
    val adultIncludePaths: List<String>,
    /** Addons to install when storing, by include path. */
    val addonsToInstall: List<Pair<String, AddonProbe.Ready>>,
    /** Addons already installed for this source that must stay (reused, not refetched). */
    val keptAddonIds: Set<String>,
    val includeCount: Int,
    val failedIncludeCount: Int,
    /** Nothing at all to show: no own items and every include failed (spec §9.3 / PRD §1 step 5). */
    val nothingLoaded: Boolean,
    /**
     * No include was downloaded or changed state: every one was not due, `304`, or failed as
     * before. With an unchanged root the stored content stays as it is (AC-T11: no reparse, no rewrite).
     */
    val unchanged: Boolean = false,
)

/**
 * Walks and fetches the include tree of a TS IPTV Source (spec §9.3, PRD §5) and builds the rows
 * to store: namespaced channels (`ts:{playlistId}:{poolId}`), VOD items, episodes, include status
 * rows and guide data. Includes are fetched one after another in document order (depth first),
 * which keeps the tree rules (budget, counts) deterministic.
 */
class TsiptvSourceResolver(
    private val http: SourceHttpTransport,
    private val addons: SourceAddonBridge,
    private val nowMs: () -> Long,
) {
    suspend fun resolve(input: TsiptvResolveInput): TsiptvResolved = Walk(input).run()

    private inner class Walk(private val input: TsiptvResolveInput) {
        private val now = nowMs()
        private val guard = TsiptvIncludeGuard(input.rootUrl).also { it.recordRootFetched(input.rootBytes) }
        private val extraIssues = ArrayList<TsiptvIssue>()
        private val channels = ArrayList<Channel>()
        private val channelIds = HashSet<String>()
        private val vodItems = ArrayList<TsiptvVodItemRecord>()
        private val vodIds = HashSet<String>()
        private val episodes = ArrayList<TsiptvEpisodeRecord>()
        private val episodeIds = HashSet<String>()
        private val includeRows = LinkedHashMap<String, TsiptvIncludeRecord>()
        /** Guide path → programmes parsed now (fresh) or null (its stored rows stay). */
        private val guideOutcomes = LinkedHashMap<String, List<IPTVProgram>?>()
        /** Guide URL → the first guide path that fetched it this refresh (QC #21: once per URL). */
        private val guideUrls = HashMap<String, String>()
        /** XMLTV display name (folded) → guide channel id, for channels without `epgId` (spec §8.1). */
        private val guideNames = HashMap<String, String>()
        /** Channel index → names to match against [guideNames] when it has no guide id. */
        private val nameMatches = HashMap<Int, List<String>>()
        /** Something was downloaded and parsed, or an include changed state. */
        private var changed = false
        private val adultPaths = ArrayList<String>()
        private val addonInstalls = ArrayList<Pair<String, AddonProbe.Ready>>()
        private val keptAddons = HashSet<String>()
        private var discovered = 0
        private var done = 0
        private var failed = 0
        private var anyIncludeContent = false
        private var movieIndex = 0
        private var seriesIndex = 0

        suspend fun run(): TsiptvResolved {
            val root = input.document
            val ownItems = root.channels.size + root.movies.size + root.series.size
            addDocumentItems(root, guard.root)
            discovered = root.includes.size
            input.progress(0, discovered)
            walk(root, guard.root)
            val guidePlan = finishGuides()
            applyNameMatches()
            checkCatalogQueries(root)
            val report = TsiptvValidationReport(guard.report.issues + extraIssues)
            val rowsChanged = includeRows.keys != input.storedIncludes.keys ||
                includeRows.values.any { row ->
                    val old = input.storedIncludes[row.includePath]
                    old == null || old.status != row.status || old.adultWithheld != row.adultWithheld ||
                        old.url != row.url || old.type != row.type
                }
            return TsiptvResolved(
                channels = channels,
                categories = categoriesOf(channels),
                vodItems = vodItems,
                episodes = episodes,
                includes = includeRows.values.toList(),
                guides = guidePlan,
                report = report,
                adultIncludePaths = adultPaths.distinct(),
                addonsToInstall = addonInstalls,
                keptAddonIds = keptAddons,
                includeCount = discovered,
                failedIncludeCount = failed,
                nothingLoaded = ownItems == 0 && !anyIncludeContent,
                unchanged = !changed && !rowsChanged && guidePlan.isEmpty,
            )
        }

        // --- Tree walk ----------------------------------------------------------------------------

        private suspend fun walk(doc: TsiptvSourceDocument, ctx: TsiptvIncludeContext) {
            for ((index, include) in doc.includes.withIndex()) {
                when (val decision = guard.admit(ctx, include, index)) {
                    is TsiptvIncludeDecision.Refused -> Unit
                    is TsiptvIncludeDecision.Accepted -> {
                        handleInclude(ctx, index, include, decision.context)
                        done++
                        input.progress(done, discovered)
                    }
                }
            }
            for (link in doc.epg) {
                val path = ctx.namespaced("epg-${link.index}")
                handleGuide(ctx, TsiptvIncludeGuard.epgEntry(link.index), path, link.url, emptyMap(), link.refreshHours, null)
            }
        }

        private suspend fun handleInclude(parent: TsiptvIncludeContext, index: Int, include: TsiptvInclude, child: TsiptvIncludeContext) {
            val entry = TsiptvIncludeGuard.includeEntry(index)
            val path = child.includePath
            val nameJson = include.name?.let { TsiptvStorageJson.encodeToString(LocalizedText.serializer(), it) }
            when (include.type) {
                TsiptvIncludeType.M3U -> handleM3u(parent, entry, include, child, nameJson)
                TsiptvIncludeType.XMLTV -> handleGuide(parent, entry, path, include.url, include.headers, include.refreshHours, nameJson)
                TsiptvIncludeType.STREMIO -> handleStremio(parent, entry, include, child, nameJson)
                TsiptvIncludeType.TSIPTV_SOURCE -> handleNested(parent, index, include, child, nameJson)
            }
        }

        // --- Fetching -----------------------------------------------------------------------------

        private fun storedFor(path: String, type: TsiptvIncludeType, url: String): TsiptvIncludeRecord? =
            input.storedIncludes[path]?.takeIf { it.type == type.wireName && it.url == url }

        private fun isDue(stored: TsiptvIncludeRecord?, refreshHours: Int): Boolean {
            if (input.force || stored == null) return true
            val last = stored.lastSuccessAt ?: return true
            return now - last >= refreshHours * HOUR_MS || now < last
        }

        private fun hasContent(stored: TsiptvIncludeRecord?) =
            stored != null && stored.status != TsiptvIncludeStatus.FAILED.name && stored.lastSuccessAt != null


        private suspend fun fetchBody(
            parent: TsiptvIncludeContext,
            entry: String,
            url: String,
            headers: Map<String, String>,
            typeCap: Long,
            stored: TsiptvIncludeRecord?,
        ): Body {
            if (!guard.canFetch) {
                guard.recordBudgetSpent(parent, entry)
                return Body.Failed("budget")
            }
            val conditional = hasContent(stored)
            val cap = minOf(typeCap, guard.remainingBudget)
            val result = http.fetch(
                SourceFetchRequest(
                    url = url,
                    headers = headers,
                    maxBytes = cap,
                    etag = stored?.etag?.takeIf { conditional },
                    lastModified = stored?.lastModified?.takeIf { conditional },
                )
            )
            return when (result) {
                SourceFetchResult.NotModified ->
                    if (conditional) Body.NotModified else {
                        guard.recordFetchFailed(parent, entry)
                        Body.Failed("http_304")
                    }

                is SourceFetchResult.Failed -> {
                    guard.recordFetchFailed(parent, entry)
                    Body.Failed(result.code)
                }

                is SourceFetchResult.Ok -> {
                    // The 50 MiB budget counts the documents as read, after gzip (spec §9.3).
                    val plain = runCatchingParse { TsiptvSourceParser.decodeBounded(result.bytes, typeCap) }
                    when {
                        plain == null -> {
                            guard.recordFetchFailed(parent, entry)
                            Body.Failed("too_large")
                        }
                        guard.recordFetched(parent, entry, plain.size.toLong()) -> Body.Ok(plain, result.etag, result.lastModified)
                        else -> Body.Failed("budget")
                    }
                }
            }
        }

        private fun row(
            path: String,
            type: TsiptvIncludeType,
            url: String,
            refreshHours: Int,
            nameJson: String?,
            stored: TsiptvIncludeRecord?,
            status: TsiptvIncludeStatus,
            attempted: Boolean,
            succeeded: Boolean,
            etag: String? = stored?.etag,
            lastModified: String? = stored?.lastModified,
            error: String? = null,
            documentJson: String? = stored?.documentJson,
            adultWithheld: Boolean = false,
        ) {
            includeRows[path] = TsiptvIncludeRecord(
                playlistId = input.playlistId,
                includePath = path,
                type = type.wireName,
                url = url,
                refreshHours = refreshHours,
                etag = etag,
                lastModified = lastModified,
                lastSuccessAt = if (succeeded) now else stored?.lastSuccessAt,
                lastAttemptAt = if (attempted) now else stored?.lastAttemptAt,
                status = status.name,
                lastError = error,
                nameJson = nameJson,
                documentJson = documentJson,
                adultWithheld = adultWithheld,
            )
        }

        private fun failureStatus(stored: TsiptvIncludeRecord?) =
            if (hasContent(stored)) TsiptvIncludeStatus.STALE else TsiptvIncludeStatus.FAILED

        // --- m3u ------------------------------------------------------------------------------

        private suspend fun handleM3u(
            parent: TsiptvIncludeContext,
            entry: String,
            include: TsiptvInclude,
            child: TsiptvIncludeContext,
            nameJson: String?,
        ) {
            val path = child.includePath
            val stored = storedFor(path, include.type, include.url)
            if (!isDue(stored, include.refreshHours) && hasContent(stored)) {
                reuseStoredChannels(path, child)
                row(path, include.type, include.url, include.refreshHours, nameJson, stored,
                    TsiptvIncludeStatus.valueOf(stored!!.status), attempted = false, succeeded = false, error = stored.lastError)
                reuseTvgGuides(parent, entry, path)
                return
            }
            when (val body = fetchBody(parent, entry, include.url, include.headers, TsiptvLimits.MAX_M3U_INCLUDE_BYTES, stored)) {
                Body.NotModified -> {
                    reuseStoredChannels(path, child)
                    row(path, include.type, include.url, include.refreshHours, nameJson, stored, TsiptvIncludeStatus.OK,
                        attempted = true, succeeded = true)
                    reuseTvgGuides(parent, entry, path)
                }

                is Body.Failed -> {
                    failed++
                    if (hasContent(stored)) reuseStoredChannels(path, child)
                    row(path, include.type, include.url, include.refreshHours, nameJson, stored, failureStatus(stored),
                        attempted = true, succeeded = false, error = body.code)
                    if (hasContent(stored)) reuseTvgGuides(parent, entry, path)
                }

                is Body.Ok -> {
                    val parsed = runCatchingParse {
                        val text = decodeText(body.bytes, TsiptvLimits.MAX_M3U_INCLUDE_BYTES)
                        M3UParser().parse(text)
                    }
                    if (parsed == null) {
                        failed++
                        extraIssues += TsiptvIssue(
                            TsiptvIssueCode.E_INCLUDE, parent.issuePath(entry),
                            "The included playlist could not be read; its last good copy is used if there is one.",
                        )
                        if (hasContent(stored)) reuseStoredChannels(path, child)
                        row(path, include.type, include.url, include.refreshHours, nameJson, stored, failureStatus(stored),
                            attempted = true, succeeded = false, error = "unreadable")
                        if (hasContent(stored)) reuseTvgGuides(parent, entry, path)
                        return
                    }
                    changed = true
                    val admitted = guard.admitItems(TsiptvPoolKind.CHANNEL, parsed.channels.size, child)
                    parsed.channels.take(admitted).forEach { addM3uChannel(it, child) }
                    if (admitted > 0) anyIncludeContent = true
                    row(path, include.type, include.url, include.refreshHours, nameJson, stored, TsiptvIncludeStatus.OK,
                        attempted = true, succeeded = true, etag = body.etag, lastModified = body.lastModified, documentJson = null)
                    // `x-tvg-url` guides join the EPG set (spec §9.3).
                    parsed.epgUrls.filter { it.isNotBlank() }.distinct().take(TsiptvLimits.MAX_EPG_LINKS).forEachIndexed { n, url ->
                        handleGuide(parent, entry, "$path:tvg-$n", url, emptyMap(), include.refreshHours, null)
                    }
                }
            }
        }

        /**
         * The `x-tvg-url` guides of an M3U include that was not parsed this time (not due, `304`,
         * stale): they stay in the guide set with their own schedule and last good copy (QC #2).
         */
        private suspend fun reuseTvgGuides(parent: TsiptvIncludeContext, entry: String, m3uPath: String) {
            input.storedIncludes.values
                .filter { it.type == TsiptvIncludeType.XMLTV.wireName && it.includePath.startsWith("$m3uPath:tvg-") }
                .sortedBy { it.includePath }
                .forEach { handleGuide(parent, entry, it.includePath, it.url, emptyMap(), it.refreshHours, null) }
        }

        private fun reuseStoredChannels(path: String, ctx: TsiptvIncludeContext) {
            val stale = input.storedChannels.filter { it.originIncludePath == path }
            val admitted = guard.admitItems(TsiptvPoolKind.CHANNEL, stale.size, ctx)
            stale.take(admitted).forEach { ch ->
                if (channelIds.add(ch.id)) channels += ch.copy(sortIndex = channels.size)
            }
            if (admitted > 0) anyIncludeContent = true
        }

        private fun addM3uChannel(ch: IPTVChannel, ctx: TsiptvIncludeContext) {
            val base = TsiptvIds.sanitizeForeignId(ch.id)
                ?: TsiptvIds.sanitizeForeignId(ch.name)
                ?: "channel"
            val id = uniqueChannelId(ctx.namespaced(base))
            val tvgId = ch.epgId?.takeIf { it.isNotBlank() }
            if (tvgId == null) nameMatches[channels.size] = listOf(ch.name)
            channels += Channel(
                id = id,
                name = ch.name,
                url = ch.url,
                logoUrl = ch.logoUrl,
                categoryId = ch.groupTitle,
                playlistId = input.playlistId,
                number = ch.number,
                groups = ch.groups,
                isRadio = ch.isRadio,
                isVod = ch.isVod,
                headers = ch.headers,
                mimeType = ch.mimeType,
                drm = ch.drm,
                catchup = ch.catchup,
                epgShiftHours = ch.epgShiftHours,
                sortIndex = channels.size,
                epgId = tvgId ?: ch.id,
                originIncludePath = ctx.includePath,
            )
        }

        private fun uniqueChannelId(poolId: String): String {
            var id = TsiptvSourceIds.channelId(input.playlistId, poolId)
            var n = 2
            while (!channelIds.add(id)) id = TsiptvSourceIds.channelId(input.playlistId, "${poolId}_${n++}")
            return id
        }

        // --- xmltv / epg ----------------------------------------------------------------------


        private suspend fun handleGuide(
            parent: TsiptvIncludeContext,
            entry: String,
            path: String,
            url: String,
            headers: Map<String, String>,
            refreshHours: Int,
            nameJson: String?,
        ) {
            val type = TsiptvIncludeType.XMLTV
            val stored = storedFor(path, type, url)
            // The same guide URL twice in one tree (an xmltv include and an x-tvg-url, say) is
            // fetched and charged once; the second path only mirrors the first one's status.
            guideUrls[url]?.let { first ->
                val firstRow = includeRows[first]
                row(path, type, url, refreshHours, nameJson, stored,
                    firstRow?.status?.let(TsiptvIncludeStatus::valueOf) ?: TsiptvIncludeStatus.FAILED,
                    attempted = false, succeeded = firstRow?.status == TsiptvIncludeStatus.OK.name,
                    etag = null, lastModified = null, error = firstRow?.lastError, documentJson = null)
                return
            }
            guideUrls[url] = path
            if (!isDue(stored, refreshHours) && hasContent(stored)) {
                keepGuide(path, stored!!)
                row(path, type, url, refreshHours, nameJson, stored, TsiptvIncludeStatus.valueOf(stored.status),
                    attempted = false, succeeded = false, error = stored.lastError)
                return
            }
            when (val body = fetchBody(parent, entry, url, headers, TsiptvLimits.MAX_XMLTV_INCLUDE_BYTES, stored)) {
                Body.NotModified -> {
                    keepGuide(path, stored!!)
                    row(path, type, url, refreshHours, nameJson, stored, TsiptvIncludeStatus.OK, attempted = true, succeeded = true)
                }

                is Body.Failed -> {
                    failed++
                    if (hasContent(stored)) keepGuide(path, stored!!)
                    row(path, type, url, refreshHours, nameJson, stored, failureStatus(stored),
                        attempted = true, succeeded = false, error = body.code)
                }

                is Body.Ok -> {
                    val parsed = parseGuide(body.bytes)
                    if (parsed == null) {
                        failed++
                        extraIssues += TsiptvIssue(
                            TsiptvIssueCode.E_INCLUDE, parent.issuePath(entry),
                            "The guide could not be read; its last good copy is used if there is one.",
                        )
                        if (hasContent(stored)) keepGuide(path, stored!!)
                        row(path, type, url, refreshHours, nameJson, stored, failureStatus(stored),
                            attempted = true, succeeded = false, error = "unreadable")
                        return
                    }
                    changed = true
                    val (programs, names) = parsed
                    guideOutcomes[path] = programs
                    addGuideNames(names)
                    row(path, type, url, refreshHours, nameJson, stored, TsiptvIncludeStatus.OK,
                        attempted = true, succeeded = true, etag = body.etag, lastModified = body.lastModified,
                        documentJson = names.takeIf { it.isNotEmpty() }?.let { TsiptvStorageJson.encodeToString(GUIDE_NAMES, it) })
                }
            }
        }

        /** The guide keeps its stored programmes (and its display names for name matching). */
        private fun keepGuide(path: String, stored: TsiptvIncludeRecord) {
            guideOutcomes[path] = null
            stored.documentJson?.let { json ->
                runCatching { TsiptvStorageJson.decodeFromString(GUIDE_NAMES, json) }.getOrNull()?.let(::addGuideNames)
            }
        }

        private fun addGuideNames(names: Map<String, String>) {
            names.forEach { (name, id) -> guideNames.getOrPut(nameKey(name)) { id } }
        }

        private fun parseGuide(bytes: ByteArray): Pair<List<IPTVProgram>, Map<String, String>>? = runCatchingParse {
            val text = decodeText(bytes, TsiptvLimits.MAX_XMLTV_INCLUDE_BYTES)
            when (val parser = EPGParserFactory.createParserForContent(text)) {
                is XMLTVEPGParser -> parser.parseDocument(text)
                else -> parser.parse(text) to emptyMap()
            }
        }

        /**
         * Programmes are stored per guide: fresh guides replace their own rows, kept guides (not due,
         * `304`, stale) keep theirs, and guides that left the tree are dropped (spec §9.3).
         */
        private fun finishGuides(): TsiptvGuidePlan {
            val fresh = guideOutcomes.filterValues { it != null }.mapValues { it.value!! }
            val previous = input.storedIncludes.values.filter { it.type == TsiptvIncludeType.XMLTV.wireName }.map { it.includePath }
            val drop = previous.filter { it !in guideOutcomes }.toSet()
            return TsiptvGuidePlan(replace = fresh, drop = drop)
        }

        /**
         * Spec §7.2 / §10 (QC #12): a `catalog` query whose catalogue the addon's manifest does not
         * declare is skipped with `W_QUERY_REF`. Checked once the manifest is known (fresh or stored).
         */
        private fun checkCatalogQueries(root: TsiptvSourceDocument) {
            root.layout?.home?.forEachIndexed { index, section ->
                val query = section.query ?: return@forEachIndexed
                if (query.from != tss.t.tsiptv.core.parser.tsiptv.TsiptvQuerySource.CATALOG) return@forEachIndexed
                val catalog = query.catalog ?: return@forEachIndexed
                val include = includeRows[query.include ?: return@forEachIndexed] ?: return@forEachIndexed
                if (include.type != TsiptvIncludeType.STREMIO.wireName) return@forEachIndexed
                val manifest = include.documentJson ?: return@forEachIndexed
                if (!TsiptvCatalogs.declares(manifest, catalog.type, catalog.id)) {
                    extraIssues += TsiptvIssue(
                        TsiptvIssueCode.W_QUERY_REF, "layout.home[$index].query.catalog",
                        "The addon does not declare this catalogue; the section is skipped.",
                    )
                }
            }
        }

        /** Spec §8.1 / §9.1: a channel without a guide id is matched to a guide channel by name. */
        private fun applyNameMatches() {
            if (guideNames.isEmpty()) return
            for ((index, names) in nameMatches) {
                val channel = channels.getOrNull(index) ?: continue
                val id = names.firstNotNullOfOrNull { guideNames[nameKey(it)] } ?: continue
                channels[index] = channel.copy(epgId = id)
            }
        }

        // --- stremio ----------------------------------------------------------------------------

        private suspend fun handleStremio(
            parent: TsiptvIncludeContext,
            entry: String,
            include: TsiptvInclude,
            child: TsiptvIncludeContext,
            nameJson: String?,
        ) {
            val path = child.includePath
            val stored = storedFor(path, include.type, include.url)
            val storedAddonId = stored?.documentJson?.let(::manifestId)
            if (!isDue(stored, include.refreshHours) && hasContent(stored) && storedAddonId != null) {
                keptAddons += storedAddonId
                anyIncludeContent = true
                row(path, include.type, include.url, include.refreshHours, nameJson, stored,
                    TsiptvIncludeStatus.valueOf(stored!!.status), attempted = false, succeeded = false, error = stored.lastError)
                return
            }
            if (!guard.canFetch) {
                guard.recordBudgetSpent(parent, entry)
                keepStaleAddon(path, include, stored, storedAddonId, nameJson, "budget")
                return
            }
            when (val probe = addons.probe(include.url)) {
                is AddonProbe.Failed -> {
                    guard.recordFetchFailed(parent, entry)
                    keepStaleAddon(path, include, stored, storedAddonId, nameJson, probe.code)
                }

                is AddonProbe.Ready -> {
                    if (!guard.recordFetched(parent, entry, probe.manifestJson.encodeToByteArray().size.toLong())) {
                        keepStaleAddon(path, include, stored, storedAddonId, nameJson, "budget")
                        return
                    }
                    if (probe.isAdult) {
                        adultPaths += path
                        if (withholdAdult()) {
                            // Keep what was there (the addon row as before), mark the include.
                            if (storedAddonId != null && hasContent(stored)) keptAddons += storedAddonId
                            row(path, include.type, include.url, include.refreshHours, nameJson, stored,
                                failureStatus(stored), attempted = true, succeeded = false, error = "adult", adultWithheld = true)
                            return
                        }
                    }
                    addonInstalls += path to probe
                    anyIncludeContent = true
                    if (stored?.documentJson != probe.manifestJson) changed = true
                    row(path, include.type, include.url, include.refreshHours, nameJson, stored, TsiptvIncludeStatus.OK,
                        attempted = true, succeeded = true, etag = null, lastModified = null, documentJson = probe.manifestJson)
                }
            }
        }

        private fun keepStaleAddon(
            path: String,
            include: TsiptvInclude,
            stored: TsiptvIncludeRecord?,
            storedAddonId: String?,
            nameJson: String?,
            code: String,
        ) {
            failed++
            if (hasContent(stored) && storedAddonId != null) {
                keptAddons += storedAddonId
                anyIncludeContent = true
            }
            row(path, include.type, include.url, include.refreshHours, nameJson, stored, failureStatus(stored),
                attempted = true, succeeded = false, error = code)
        }

        private fun manifestId(json: String): String? = runCatching {
            TsiptvStorageJson.parseToJsonElement(json).jsonObject["id"]?.jsonPrimitive?.content
        }.getOrNull()

        // --- nested TS IPTV Sources -------------------------------------------------------------

        private suspend fun handleNested(
            parent: TsiptvIncludeContext,
            index: Int,
            include: TsiptvInclude,
            child: TsiptvIncludeContext,
            nameJson: String?,
        ) {
            val entry = TsiptvIncludeGuard.includeEntry(index)
            val path = child.includePath
            val stored = storedFor(path, include.type, include.url)
            val storedDoc = stored?.documentJson?.let(::decodeDocument)
            if (!isDue(stored, include.refreshHours) && hasContent(stored) && storedDoc != null) {
                row(path, include.type, include.url, include.refreshHours, nameJson, stored,
                    TsiptvIncludeStatus.valueOf(stored!!.status), attempted = false, succeeded = false, error = stored.lastError,
                    adultWithheld = stored.adultWithheld)
                contributeNested(storedDoc, child)
                return
            }
            when (val body = fetchBody(parent, entry, include.url, include.headers, TsiptvLimits.MAX_DOCUMENT_BYTES, stored)) {
                Body.NotModified -> {
                    if (storedDoc == null) {
                        failed++
                        row(path, include.type, include.url, include.refreshHours, nameJson, stored, TsiptvIncludeStatus.FAILED,
                            attempted = true, succeeded = false, error = "unreadable")
                        return
                    }
                    row(path, include.type, include.url, include.refreshHours, nameJson, stored, TsiptvIncludeStatus.OK,
                        attempted = true, succeeded = true, adultWithheld = stored?.adultWithheld ?: false)
                    contributeNested(storedDoc, child)
                }

                is Body.Failed -> nestedFailed(path, include, stored, storedDoc, child, nameJson, body.code)

                is Body.Ok -> {
                    val result = TsiptvSourceParser.parse(body.bytes, include.url)
                    if (result is TsiptvParseResult.Failure && result.cause.isFetchFailure) {
                        guard.recordFetchFailed(parent, entry)
                        nestedFailed(path, include, stored, storedDoc, child, nameJson, result.cause.name.lowercase())
                        return
                    }
                    guard.recordParse(parent, index, child, result)
                    changed = true
                    val doc = result.documentOrNull
                    if (doc == null) {
                        // Rejected by its own validation: dropped, no stale copy (spec §9.3).
                        failed++
                        row(path, include.type, include.url, include.refreshHours, nameJson, stored, TsiptvIncludeStatus.FAILED,
                            attempted = true, succeeded = false, error = "rejected", documentJson = null)
                        return
                    }
                    if (doc.meta.adult) {
                        adultPaths += path
                        if (withholdAdult()) {
                            val previous = storedDoc?.takeIf { !it.meta.adult && hasContent(stored) }
                            row(path, include.type, include.url, include.refreshHours, nameJson, stored,
                                if (previous != null) TsiptvIncludeStatus.STALE else TsiptvIncludeStatus.FAILED,
                                attempted = true, succeeded = false, error = "adult", adultWithheld = true)
                            if (previous != null) contributeNested(previous, child)
                            return
                        }
                    }
                    row(path, include.type, include.url, include.refreshHours, nameJson, stored, TsiptvIncludeStatus.OK,
                        attempted = true, succeeded = true, etag = body.etag, lastModified = body.lastModified,
                        documentJson = TsiptvStorageJson.encodeToString(TsiptvSourceDocument.serializer(), doc))
                    contributeNested(doc, child)
                }
            }
        }

        private suspend fun nestedFailed(
            path: String,
            include: TsiptvInclude,
            stored: TsiptvIncludeRecord?,
            storedDoc: TsiptvSourceDocument?,
            child: TsiptvIncludeContext,
            nameJson: String?,
            code: String,
        ) {
            failed++
            val stale = storedDoc?.takeIf { hasContent(stored) }
            row(path, include.type, include.url, include.refreshHours, nameJson, stored,
                if (stale != null) TsiptvIncludeStatus.STALE else TsiptvIncludeStatus.FAILED,
                attempted = true, succeeded = false, error = code, adultWithheld = stored?.adultWithheld ?: false)
            if (stale != null) contributeNested(stale, child)
        }

        private suspend fun contributeNested(doc: TsiptvSourceDocument, ctx: TsiptvIncludeContext) {
            if (doc.meta.adult && !adultPaths.contains(ctx.includePath)) adultPaths += ctx.includePath
            val before = channels.size + vodItems.size
            addDocumentItems(doc, ctx)
            if (channels.size + vodItems.size > before) anyIncludeContent = true
            discovered += doc.includes.size
            walk(doc, ctx)
        }

        /** Refresh of a source the user has not confirmed as adult: new adult content is withheld. */
        private fun withholdAdult(): Boolean = !input.isImport && !input.adultConfirmed

        private fun decodeDocument(json: String): TsiptvSourceDocument? = runCatching {
            TsiptvStorageJson.decodeFromString(TsiptvSourceDocument.serializer(), json)
        }.getOrNull()

        // --- Items ------------------------------------------------------------------------------

        private fun addDocumentItems(doc: TsiptvSourceDocument, ctx: TsiptvIncludeContext) {
            val admitted = guard.admitDocumentItems(doc, ctx)
            val origin = ctx.includePath.takeIf { it.isNotEmpty() }
            admitted.channels.forEach { addSourceChannel(it, ctx, origin) }
            admitted.movies.forEach { addMovie(it, ctx, origin) }
            admitted.series.forEach { addSeries(it, ctx, origin) }
        }

        private fun addSourceChannel(ch: TsiptvChannel, ctx: TsiptvIncludeContext, origin: String?) {
            val stream = ch.streams.firstOrNull() ?: return
            val poolId = ctx.namespaced(ch.id)
            val id = TsiptvSourceIds.channelId(input.playlistId, poolId)
            if (!channelIds.add(id)) return
            if (ch.epgId == null) nameMatches[channels.size] = ch.name.values.values.toList()
            channels += Channel(
                id = id,
                name = ch.name.resolve(null),
                url = stream.url,
                logoUrl = ch.logo,
                categoryId = ch.groups.firstOrNull(),
                playlistId = input.playlistId,
                number = ch.number,
                groups = ch.groups,
                isRadio = ch.type == TsiptvChannelType.RADIO,
                headers = stream.headers,
                mimeType = stream.mimeType,
                drm = stream.drm,
                catchup = ch.catchup,
                epgShiftHours = ch.epgShiftHours,
                sortIndex = channels.size,
                epgId = ch.epgId,
                nameJson = TsiptvStorageJson.encodeToString(LocalizedText.serializer(), ch.name),
                originIncludePath = origin,
                tagsJson = ch.tags.takeIf { it.isNotEmpty() }?.let {
                    TsiptvStorageJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), it)
                },
                descriptionJson = ch.description?.let { TsiptvStorageJson.encodeToString(LocalizedText.serializer(), it) },
                // Every stream (spec §8.0/§8.4): the player plays the first supported one and offers the others.
                streamsJson = ch.streams.takeIf { it.size > 1 }?.let { TsiptvStorageJson.encodeToString(STREAMS, it) },
            )
        }

        private fun addMovie(movie: TsiptvMovie, ctx: TsiptvIncludeContext, origin: String?) {
            val itemId = ctx.namespaced(movie.id)
            if (!vodIds.add(itemId)) return
            vodItems += TsiptvVodMapping.movieRecord(input.playlistId, itemId, origin, movieIndex++, movie)
        }

        private fun addSeries(series: TsiptvSeries, ctx: TsiptvIncludeContext, origin: String?) {
            val itemId = ctx.namespaced(series.id)
            if (!vodIds.add(itemId)) return
            vodItems += TsiptvVodMapping.seriesRecord(input.playlistId, itemId, origin, seriesIndex++, series)
            for (season in series.seasons) {
                for (episode in season.episodes) {
                    val episodeId = ctx.namespaced(episode.id)
                    if (!episodeIds.add(episodeId)) continue
                    episodes += TsiptvVodMapping.episodeRecord(input.playlistId, itemId, episodeId, season, episode)
                }
            }
        }
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        val GUIDE_NAMES = kotlinx.serialization.builtins.MapSerializer(
            kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<String>()
        )
        val STREAMS = kotlinx.serialization.builtins.ListSerializer(tss.t.tsiptv.core.parser.tsiptv.TsiptvStream.serializer())

        /** Case-, accent- and space-insensitive key for guide name matching. */
        fun nameKey(name: String): String = guideNameKey(name)

        /** Parsers throw on broken input; a broken include must never fail the source. */
        inline fun <T> runCatchingParse(block: () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

        /** Gunzips a `.gz` body with a bound, drops a BOM. */
        fun decodeText(bytes: ByteArray, maxBytes: Long): String {
            val plain = TsiptvSourceParser.decodeBounded(bytes, maxBytes) ?: error("too large")
            return plain.decodeToString().removePrefix("﻿")
        }

        fun categoriesOf(channels: List<Channel>): List<Category> =
            channels.flatMap { it.groups.ifEmpty { listOfNotNull(it.categoryId) } }
                .distinct()
                .map { group ->
                    val playlistId = channels.first().playlistId
                    Category(id = TsiptvSourceIds.categoryId(playlistId, group), name = group, playlistId = playlistId)
                }
    }
}

/** Ids of the rows a source stores (roadmap risk R1: never collide with another playlist). */
object TsiptvSourceIds {
    const val CHANNEL_PREFIX = "ts:"

    /** `ts:{playlistId}:{poolId}`. */
    fun channelId(playlistId: String, poolId: String) = "$CHANNEL_PREFIX$playlistId:$poolId"

    /** The pool id (`cinema:item`) of a stored source channel id, or null for other channels. */
    fun poolIdOf(playlistId: String, channelId: String): String? =
        channelId.removePrefix("$CHANNEL_PREFIX$playlistId:").takeIf { it != channelId }

    fun categoryId(playlistId: String, group: String) = "$CHANNEL_PREFIX$playlistId:group:$group"

    /** The playlist id a source is stored under: stable per source `id`, so a re-import from another link replaces it. */
    fun playlistIdFor(sourceId: String) = "$PLAYLIST_PREFIX$sourceId"

    const val PLAYLIST_PREFIX = "tsiptv:"

    /** Whether [playlistId] is a TS IPTV Source (the UI shows Source Home for it). */
    fun isSourcePlaylist(playlistId: String?): Boolean = playlistId?.startsWith(PLAYLIST_PREFIX) == true
}

private sealed interface Body {
    class Ok(val bytes: ByteArray, val etag: String?, val lastModified: String?) : Body
    data object NotModified : Body
    data class Failed(val code: String) : Body
}


/** Stremio manifest catalogue lookups (shared by the resolver and Source Home). */
object TsiptvCatalogs {
    /** Whether [manifestJson] declares the catalogue [type]/[id]; false when it cannot be read. */
    fun declares(manifestJson: String, type: String, id: String): Boolean {
        val manifest = runCatching { TsiptvStorageJson.parseToJsonElement(manifestJson).jsonObject }.getOrNull() ?: return false
        val catalogs = manifest["catalogs"] as? kotlinx.serialization.json.JsonArray ?: return false
        return catalogs.any { entry ->
            val obj = entry as? kotlinx.serialization.json.JsonObject ?: return@any false
            (obj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content == type &&
                (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content == id
        }
    }
}

/**
 * Guide name matching key (spec §8.1): case, accents and **all** spaces are ignored, so "BBCOne"
 * matches "BBC One".
 */
fun guideNameKey(name: String): String = TextFold.fold(name).lowercase().filterNot { it.isWhitespace() }
