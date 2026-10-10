package tss.t.tsiptv.core.tsiptv

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import okio.Buffer
import okio.GzipSource
import okio.buffer
import okio.use
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.core.network.HttpStatusException
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.tsiptv.TsiptvAppearance
import tss.t.tsiptv.core.parser.tsiptv.TsiptvHosts
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssue
import tss.t.tsiptv.core.parser.tsiptv.TsiptvLayout
import tss.t.tsiptv.core.parser.tsiptv.TsiptvLimits
import tss.t.tsiptv.core.parser.tsiptv.TsiptvMeta
import tss.t.tsiptv.core.parser.tsiptv.TsiptvParseResult
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSourceDocument
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSourceParser
import tss.t.tsiptv.core.parser.tsiptv.TsiptvValidationReport
import tss.t.tsiptv.core.stremio.MediaHistoryStore
import tss.t.tsiptv.core.stremio.MediaSourceKind

/** A document error (spec §10): the source is rejected and nothing is stored. */
class TsiptvSourceRejectedException(val report: TsiptvValidationReport) :
    Exception("TS IPTV Source rejected: ${report.primaryDocumentError}")

/** Every include failed and the root has no items of its own (PRD §1 step 5). Nothing is stored. */
class TsiptvNothingLoadedException : Exception("Nothing could be loaded")

/**
 * What the import preview shows (PRD §1 step 3). Nothing is stored yet.
 *
 * @property rootUrl Null for a file
 * @property existing The installed source with the same `id`, if any
 */
class TsiptvSourcePreview internal constructor(
    val document: TsiptvSourceDocument,
    val report: TsiptvValidationReport,
    val rootUrl: String?,
    val fileName: String?,
    internal val etag: String?,
    internal val lastModified: String?,
    internal val rootBytes: Long,
    val existing: TsiptvSourceRecord?,
    val existingName: String?,
) {
    /** Same `id` from the same link: an update, no question asked. */
    val isRefreshOfExisting: Boolean get() = existing != null && rootUrl != null && existing.rootUrl == rootUrl

    /** Same `id` from another link or a file: "Replace 'Name'?" (PRD §1 "Updates"). */
    val needsReplaceConfirmation: Boolean get() = existing != null && !isRefreshOfExisting

    /** "This source contacts these servers". */
    val hosts: List<String> get() = TsiptvHosts.contacted(rootUrl, document)

    val tvCount: Int get() = document.tvChannels.size
    val radioCount: Int get() = document.radioChannels.size
    val movieCount: Int get() = document.movies.size
    val seriesCount: Int get() = document.series.size
    val episodeCount: Int get() = document.episodeCount
    val includeCount: Int get() = document.includes.size

    /** "N items will be skipped" (PRD Follow-up #3 row 4: dropped items only). */
    val skippedCount: Int get() = report.skippedItemCount

    /** Root `meta.adult` (a non-boolean was read as true): the preview asks for 18+. */
    val rootIsAdult: Boolean get() = document.meta.adult

    override fun toString(): String = "TsiptvSourcePreview(id=${document.id})"
}

/** A resolved import waiting for the include-adult confirmation (or ready to store). */
class TsiptvPendingImport internal constructor(
    val preview: TsiptvSourcePreview,
    internal val resolved: TsiptvResolved,
) {
    /** Include paths that are adult: ask `source_warn_adult_include` before storing (spec §9.3). */
    val adultIncludePaths: List<String> get() = resolved.adultIncludePaths
    val nothingLoaded: Boolean get() = resolved.nothingLoaded
}

/** Outcome of a stored import or refresh. */
class TsiptvStoreResult(
    val playlist: Playlist,
    val channelCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    /** "N items will be skipped" of the whole tree (root + includes). */
    val skippedCount: Int,
    val report: TsiptvValidationReport,
    /** Includes in the stored tree (analytics only). */
    val includeCount: Int = 0,
    /** Whether any guide is part of the tree (analytics only). */
    val hasGuide: Boolean = false,
)

sealed interface TsiptvRefreshResult {
    /**
     * Stored. [rootError] is set when the root itself could not be fetched (network, status): the
     * includes were still refreshed from the stored root, and the failure is shown (QC F3 #7).
     */
    class Stored(val result: TsiptvStoreResult, val rootError: String? = null) : TsiptvRefreshResult

    /** The root (304 or not due) and every include were unchanged: nothing was reparsed or rewritten. */
    data object Unchanged : TsiptvRefreshResult

    /** The root became adult; its new content is withheld until confirmed in About. */
    data object AdultPending : TsiptvRefreshResult

    /** The root could not be fetched or was rejected; the stored copy stays. */
    class Failed(val code: String, val report: TsiptvValidationReport? = null) : TsiptvRefreshResult
}

/** [TsiptvSourceService.store] without the 18+ confirmation the preview asked for. */
class TsiptvAdultConfirmationRequiredException : Exception("18+ confirmation required")

/**
 * TS IPTV Sources end to end (PRD F3 §1, §5, §6): import by link or file, preview, include
 * resolution, one-transaction storage, refresh (conditional GET, `refreshHours`, stale copies,
 * adult rules), confirmation and removal. Never logs URLs.
 *
 * Threading: parsing and resolving run on [Dispatchers.Default] (never on the caller's thread).
 * Concurrency: refreshes of one source run one at a time, and every write (store, confirm, remove)
 * of one source holds that source's write lock; a store whose source was removed meanwhile is
 * dropped (QC F3 #5).
 */
class TsiptvSourceService(
    private val database: IPTVDatabase,
    private val http: SourceHttpTransport,
    private val addons: SourceAddonBridge,
    private val mediaHistory: MediaHistoryStore,
    private val nowMs: () -> Long,
    private val uiLanguage: () -> String? = { null },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val store get() = database.tsiptvStore
    private val resolver = TsiptvSourceResolver(http, addons, nowMs)

    private val locksGuard = Mutex()
    private val writeLocks = HashMap<String, Mutex>()
    private val refreshLocks = HashMap<String, Mutex>()

    private suspend fun writeLock(playlistId: String) = locksGuard.withLock { writeLocks.getOrPut(playlistId) { Mutex() } }
    private suspend fun refreshLock(playlistId: String) = locksGuard.withLock { refreshLocks.getOrPut(playlistId) { Mutex() } }

    /** A link fetched for "Add by link": a source preview, the bytes of a small other format, or neither. */
    sealed interface LinkFetch {
        class Source(val preview: TsiptvSourcePreview) : LinkFetch
        class Other(val bytes: ByteArray) : LinkFetch

        /**
         * Not a TS IPTV Source and larger than the sniffed head: nothing is buffered here; the F1
         * importer downloads it its own way (QC F3 #23).
         */
        data object NotSource : LinkFetch
    }

    /**
     * Downloads [url]. A body that turns out to be a TS IPTV Source (sniffed in its first 64 KiB,
     * gzip or not) is capped at 5 MiB and becomes a preview. Anything else stops after the first
     * 64 KiB: a body that fits is returned for the F1 parsers, a larger one is [LinkFetch.NotSource].
     *
     * @throws HttpStatusException for non-2xx (F1 error messages)
     * @throws TsiptvSourceRejectedException for a document error of a source
     */
    suspend fun fetchLink(url: String): LinkFetch = withContext(dispatcher) {
        var isSource = false
        val result = http.fetch(
            SourceFetchRequest(
                url = url,
                maxBytes = TsiptvLimits.MAX_DOCUMENT_BYTES,
                sniffCap = { head ->
                    isSource = looksLikeSource(head)
                    if (isSource) TsiptvLimits.MAX_DOCUMENT_BYTES else head.size.toLong()
                },
                totalTimeoutMs = null,
            )
        )
        when (result) {
            is SourceFetchResult.Ok ->
                if (isSource) LinkFetch.Source(previewOf(result.bytes, url, null, result.etag, result.lastModified))
                else LinkFetch.Other(result.bytes)

            SourceFetchResult.NotModified -> throw HttpStatusException(304)
            is SourceFetchResult.Failed -> when {
                result.code == "too_large" && !isSource -> LinkFetch.NotSource
                result.code == "too_large" -> throw TsiptvSourceRejectedException(
                    TsiptvValidationReport(
                        listOf(
                            TsiptvIssue(
                                tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_TOO_LARGE, "",
                                "The document is larger than 5 MiB after decompression.",
                            )
                        )
                    )
                )
                result.status != null -> throw HttpStatusException(result.status)
                else -> throw SourceNetworkException(result.code)
            }
        }
    }

    /**
     * Parses and validates a root document (link or file) and builds the preview.
     * @throws TsiptvSourceRejectedException for a document error
     */
    suspend fun preview(
        bytes: ByteArray,
        rootUrl: String?,
        fileName: String?,
        etag: String? = null,
        lastModified: String? = null,
    ): TsiptvSourcePreview = withContext(dispatcher) { previewOf(bytes, rootUrl, fileName, etag, lastModified) }

    private suspend fun previewOf(
        bytes: ByteArray,
        rootUrl: String?,
        fileName: String?,
        etag: String?,
        lastModified: String?,
    ): TsiptvSourcePreview {
        val result = TsiptvSourceParser.parse(bytes, rootUrl)
        val document = when (result) {
            is TsiptvParseResult.Failure -> throw TsiptvSourceRejectedException(result.report)
            is TsiptvParseResult.Success -> result.document
        }
        val existing = store.getSourceBySourceId(document.id)
        return TsiptvSourcePreview(
            document = document,
            report = result.report,
            rootUrl = rootUrl,
            fileName = fileName,
            etag = etag,
            lastModified = lastModified,
            rootBytes = plainSize(bytes),
            existing = existing,
            existingName = existing?.let { database.getPlaylistById(it.playlistId)?.name },
        )
    }

    /** "Import": fetches the includes ("Loading i of n…"). Nothing is stored yet. */
    suspend fun resolve(preview: TsiptvSourcePreview, progress: (Int, Int) -> Unit = { _, _ -> }): TsiptvPendingImport =
        withContext(dispatcher) {
            val playlistId = preview.existing?.playlistId ?: TsiptvSourceIds.playlistIdFor(preview.document.id)
            val storedIncludes = if (preview.existing != null) store.getIncludes(playlistId).associateBy { it.includePath } else emptyMap()
            val storedChannels = if (preview.existing != null) database.getAllChannelsByPlayListId(playlistId).first() else emptyList()
            val resolved = resolver.resolve(
                TsiptvResolveInput(
                    playlistId = playlistId,
                    document = preview.document,
                    rootUrl = preview.rootUrl,
                    rootBytes = preview.rootBytes,
                    storedIncludes = storedIncludes,
                    storedChannels = storedChannels,
                    force = true,
                    adultConfirmed = preview.existing?.adultConfirmed ?: false,
                    isImport = true,
                    progress = progress,
                )
            )
            TsiptvPendingImport(preview, resolved)
        }

    /**
     * Stores a resolved import in one transaction and installs its addons.
     *
     * @param adultConfirmed The user ticked "I am 18 or older" (root or includes)
     * @throws TsiptvNothingLoadedException when nothing could be loaded
     * @throws TsiptvAdultConfirmationRequiredException when the root or an include is adult and
     *   neither this call nor an earlier import confirmed 18+ (QC F3 #23)
     */
    suspend fun store(pending: TsiptvPendingImport, adultConfirmed: Boolean): TsiptvStoreResult = withContext(dispatcher) {
        if (pending.nothingLoaded) throw TsiptvNothingLoadedException()
        val preview = pending.preview
        val confirmed = adultConfirmed || (preview.existing?.adultConfirmed ?: false)
        if (!confirmed && (preview.rootIsAdult || pending.adultIncludePaths.isNotEmpty())) {
            throw TsiptvAdultConfirmationRequiredException()
        }
        val playlistId = preview.existing?.playlistId ?: TsiptvSourceIds.playlistIdFor(preview.document.id)
        writeLock(playlistId).withLock {
            storeResolved(
                playlistId = playlistId,
                document = preview.document,
                rootReport = preview.report,
                resolved = pending.resolved,
                rootUrl = preview.rootUrl,
                fileName = preview.fileName,
                etag = preview.etag,
                lastModified = preview.lastModified,
                adultConfirmed = confirmed,
                previous = preview.existing,
                fetchedAt = nowMs(),
                rootError = null,
            )
        }
    }

    /** Caller holds the write lock of [playlistId]. */
    private suspend fun storeResolved(
        playlistId: String,
        document: TsiptvSourceDocument,
        rootReport: TsiptvValidationReport,
        resolved: TsiptvResolved,
        rootUrl: String?,
        fileName: String?,
        etag: String?,
        lastModified: String?,
        adultConfirmed: Boolean,
        previous: TsiptvSourceRecord?,
        fetchedAt: Long,
        rootError: String?,
    ): TsiptvStoreResult {
        val now = nowMs()
        // Addons first (their own store), undone if the source transaction fails (QC F3 #18).
        val ownedBefore = addons.ownedBy(playlistId)
        val installed = HashSet<String>()
        for ((_, probe) in resolved.addonsToInstall) {
            try {
                installed += addons.install(probe, playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // One addon that cannot be saved must not fail the source.
            }
        }

        val report = TsiptvValidationReport(rootReport.issues + resolved.report.issues)
        val previousPlaylist = database.getPlaylistById(playlistId)
        val playlist = Playlist(
            id = playlistId,
            name = document.meta.name.resolve(uiLanguage()),
            url = rootUrl ?: previousPlaylist?.url?.takeIf { fileName == null } ?: "file:${fileName ?: document.id}",
            lastUpdated = now,
            sourceType = if (rootUrl != null) PlaylistSourceType.URL else PlaylistSourceType.FILE,
            epgUrls = emptyList(),
            format = IPTVFormat.TSIPTV_SOURCE.name,
        )
        val json = TsiptvStorageJson
        val source = TsiptvSourceRecord(
            playlistId = playlistId,
            sourceId = document.id,
            revision = document.revision,
            rootUrl = rootUrl,
            metaJson = json.encodeToString(TsiptvMeta.serializer(), document.meta),
            appearanceJson = document.appearance?.let { json.encodeToString(TsiptvAppearance.serializer(), it) },
            layoutJson = document.layout?.let { json.encodeToString(TsiptvLayout.serializer(), it) },
            etag = etag,
            lastModified = lastModified,
            fetchedAt = fetchedAt,
            reportJson = json.encodeToString(ListSerializer(TsiptvIssue.serializer()), report.details()),
            adultConfirmed = adultConfirmed,
            rootDocumentJson = json.encodeToString(TsiptvSourceDocument.serializer(), document),
            skippedCount = report.skippedItemCount,
            adultPending = false,
            lastErrorCode = rootError,
            lastErrorAt = if (rootError != null) now else null,
        )
        try {
            // A refresh whose source was removed meanwhile must not bring it back (QC F3 #5).
            if (previous != null && store.getSource(playlistId) == null) throw TsiptvSourceRemovedException()
            store.replaceSourceContent(
                TsiptvStoredContent(
                    playlist = playlist,
                    categories = resolved.categories,
                    channels = resolved.channels,
                    source = source,
                    includes = resolved.includes,
                    vodItems = resolved.vodItems,
                    episodes = resolved.episodes,
                    guides = resolved.guides,
                )
            )
        } catch (e: Throwable) {
            // Undo this store's addon installs (the source rows were not written).
            withContext(NonCancellable) {
                runCatching { addons.removeOwned(playlistId, keep = ownedBefore) }
            }
            throw e
        }
        // Addons that are no longer part of the tree.
        addons.removeOwned(playlistId, keep = installed + resolved.keptAddonIds)
        return TsiptvStoreResult(
            playlist = playlist,
            channelCount = resolved.channels.size,
            movieCount = resolved.vodItems.count { it.kind == TsiptvVodItemRecord.KIND_MOVIE },
            seriesCount = resolved.vodItems.count { it.kind == TsiptvVodItemRecord.KIND_SERIES },
            skippedCount = report.skippedItemCount,
            report = report,
            includeCount = resolved.includes.size,
            hasGuide = resolved.includes.any { it.type == tss.t.tsiptv.core.parser.tsiptv.TsiptvIncludeType.XMLTV.wireName } || resolved.guides.replace.isNotEmpty(),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Refresh (PRD §5)
    // ---------------------------------------------------------------------------------------------

    /**
     * Whether a refresh is due when the source becomes active: the root is older than 24 h, or an
     * include is older than its `refreshHours` (failed ones are retried at the same pace).
     */
    suspend fun isRefreshDue(playlistId: String): Boolean {
        val record = store.getSource(playlistId) ?: return false
        val now = nowMs()
        val rootLast = maxOf(record.fetchedAt, record.lastErrorAt ?: 0L)
        if (record.rootUrl != null && (now - rootLast >= ROOT_STALE_MS || now < rootLast)) return true
        return store.getIncludes(playlistId).any { include ->
            val last = maxOf(include.lastSuccessAt ?: 0L, include.lastAttemptAt ?: 0L).takeIf { it > 0 } ?: return@any true
            now - last >= include.refreshHours * HOUR_MS || now < last
        }
    }

    /** Refreshes when [isRefreshDue]; never throws for network problems. */
    suspend fun refreshIfDue(playlistId: String): TsiptvRefreshResult? =
        if (isRefreshDue(playlistId)) refresh(playlistId, force = false) else null

    /**
     * Refreshes a source. [force] (Refresh button) fetches the root and every include, ignoring
     * `refreshHours`; otherwise only what is due. Conditional GET everywhere; failures keep the
     * last good copy. One refresh of a source at a time.
     */
    suspend fun refresh(playlistId: String, force: Boolean): TsiptvRefreshResult = withContext(dispatcher) {
        refreshLock(playlistId).withLock { refreshLocked(playlistId, force) }
    }

    private suspend fun refreshLocked(playlistId: String, force: Boolean): TsiptvRefreshResult {
        val record = store.getSource(playlistId) ?: return TsiptvRefreshResult.Failed("missing")
        val storedDoc = record.rootDocumentJson?.let { json ->
            runCatching { TsiptvStorageJson.decodeFromString(TsiptvSourceDocument.serializer(), json) }.getOrNull()
        }
        val now = nowMs()
        val rootDue = record.rootUrl != null && (force || now - record.fetchedAt >= ROOT_STALE_MS || storedDoc == null)
        var document = storedDoc
        var report = TsiptvValidationReport()
        var etag = record.etag
        var lastModified = record.lastModified
        var rootBytes = 0L
        var rootChanged = false
        var rootFetched = false
        var rootError: String? = null
        if (rootDue && record.rootUrl != null) {
            val result = http.fetch(
                SourceFetchRequest(
                    url = record.rootUrl,
                    maxBytes = TsiptvLimits.MAX_DOCUMENT_BYTES,
                    etag = record.etag.takeIf { storedDoc != null },
                    lastModified = record.lastModified.takeIf { storedDoc != null },
                )
            )
            when (result) {
                SourceFetchResult.NotModified -> rootFetched = true
                is SourceFetchResult.Failed -> {
                    if (storedDoc == null) {
                        recordRootFailure(playlistId, result.code)
                        return TsiptvRefreshResult.Failed(result.code)
                    }
                    rootError = result.code
                }
                is SourceFetchResult.Ok -> when (val parsed = TsiptvSourceParser.parse(result.bytes, record.rootUrl)) {
                    is TsiptvParseResult.Failure -> {
                        recordRootFailure(playlistId, "rejected")
                        return TsiptvRefreshResult.Failed("rejected", parsed.report)
                    }
                    is TsiptvParseResult.Success -> {
                        if (parsed.document.id != record.sourceId) {
                            recordRootFailure(playlistId, "id_changed")
                            return TsiptvRefreshResult.Failed("id_changed")
                        }
                        document = parsed.document
                        report = parsed.report
                        etag = result.etag
                        lastModified = result.lastModified
                        rootBytes = plainSize(result.bytes)
                        rootChanged = true
                        rootFetched = true
                    }
                }
            }
        }
        val doc = document ?: return TsiptvRefreshResult.Failed("missing")
        if (!rootChanged) report = storedReport(record)

        // Spec §9.3: a refresh that makes an unconfirmed source adult withholds the new content.
        if (rootChanged && doc.meta.adult && !record.adultConfirmed && storedDoc?.meta?.adult != true) {
            writeLock(playlistId).withLock {
                store.getSource(playlistId)?.let { store.upsertSource(it.copy(adultPending = true)) }
            }
            return TsiptvRefreshResult.AdultPending
        }

        val resolved = resolver.resolve(
            TsiptvResolveInput(
                playlistId = playlistId,
                document = doc,
                rootUrl = record.rootUrl,
                rootBytes = rootBytes,
                storedIncludes = store.getIncludes(playlistId).associateBy { it.includePath },
                storedChannels = database.getAllChannelsByPlayListId(playlistId).first(),
                force = force,
                adultConfirmed = record.adultConfirmed,
                isImport = false,
            )
        )
        // A kept root keeps its time: a failed root must not look fresh (QC F3 #7).
        val fetchedAt = if (rootFetched) now else record.fetchedAt
        return writeLock(playlistId).withLock {
            val current = store.getSource(playlistId) ?: return@withLock TsiptvRefreshResult.Failed("missing")
            if (!rootChanged && resolved.unchanged) {
                // AC-T11: nothing new; only the times (and a root failure) are recorded.
                store.upsertSource(
                    current.copy(
                        fetchedAt = fetchedAt,
                        lastErrorCode = rootError,
                        lastErrorAt = if (rootError != null) now else null,
                    )
                )
                store.upsertIncludes(resolved.includes)
                return@withLock if (rootError != null) TsiptvRefreshResult.Failed(rootError) else TsiptvRefreshResult.Unchanged
            }
            val result = try {
                storeResolved(
                    playlistId = playlistId,
                    document = doc,
                    rootReport = report,
                    resolved = resolved,
                    rootUrl = record.rootUrl,
                    fileName = null,
                    etag = etag,
                    lastModified = lastModified,
                    adultConfirmed = current.adultConfirmed,
                    previous = current,
                    fetchedAt = fetchedAt,
                    rootError = rootError,
                )
            } catch (_: TsiptvSourceRemovedException) {
                return@withLock TsiptvRefreshResult.Failed("missing")
            }
            TsiptvRefreshResult.Stored(result, rootError)
        }
    }

    private suspend fun recordRootFailure(playlistId: String, code: String) {
        writeLock(playlistId).withLock {
            store.getSource(playlistId)?.let { store.upsertSource(it.copy(lastErrorCode = code, lastErrorAt = nowMs())) }
        }
    }

    /** The root report stored with the source (Details of the last refresh). */
    private fun storedReport(record: TsiptvSourceRecord): TsiptvValidationReport {
        val issues = record.reportJson?.let {
            runCatching { TsiptvStorageJson.decodeFromString(ListSerializer(TsiptvIssue.serializer()), it) }.getOrNull()
        }.orEmpty()
        // Only the root document's own findings (tree findings are recomputed by the resolve).
        return TsiptvValidationReport(issues.filterNot { it.path.startsWith("[") || it.path.startsWith("includes[") || it.path.startsWith("epg[") })
    }

    /** "Confirm" in About: marks the source as confirmed and merges the withheld content. */
    suspend fun confirmAdult(playlistId: String): TsiptvRefreshResult = withContext(dispatcher) {
        val confirmed = writeLock(playlistId).withLock {
            val record = store.getSource(playlistId) ?: return@withLock false
            store.upsertSource(record.copy(adultConfirmed = true, adultPending = false))
            true
        }
        if (!confirmed) TsiptvRefreshResult.Failed("missing") else refresh(playlistId, force = true)
    }

    /**
     * "Remove source" (AC-T24): its owned addons, its `media_history` rows, its guide data, then the
     * playlist (channels, VOD rows and include rows cascade). Waits for a store in progress.
     */
    suspend fun remove(playlistId: String) = withContext(dispatcher) {
        writeLock(playlistId).withLock {
            addons.removeOwned(playlistId)
            mediaHistory.deleteForSource(MediaSourceKind.TSIPTV, playlistId)
            database.deleteProgramsForPlaylist(playlistId)
            database.deletePlaylistById(playlistId)
        }
    }

    private fun plainSize(bytes: ByteArray): Long =
        runCatching { TsiptvSourceParser.decodeBounded(bytes, TsiptvLimits.MAX_DOCUMENT_BYTES)?.size?.toLong() }.getOrNull()
            ?: bytes.size.toLong()

    companion object {
        /** "Add by link" for any format: F1 had no cap; this only stops a runaway download. */
        const val MAX_LINK_BYTES: Long = 512L * 1024 * 1024
        private const val HOUR_MS = 3_600_000L
        private const val ROOT_STALE_MS = 24 * HOUR_MS

        /** §3 detection on the first bytes of a body, gzip-compressed or not. */
        fun looksLikeSource(head: ByteArray): Boolean = TsiptvSourceParser.looksLikeSource(sniffText(head))

        internal fun sniffText(head: ByteArray): String {
            val gzip = head.size >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()
            if (!gzip) return head.decodeToString()
            val out = Buffer()
            try {
                GzipSource(Buffer().write(head)).buffer().use { source ->
                    while (out.size < 64L * 1024) {
                        if (source.read(out, 8192) == -1L) break
                    }
                }
            } catch (_: Exception) {
                // A partial gzip stream ends early: what was inflated so far is enough to sniff.
            }
            return out.readByteArray().decodeToString()
        }
    }
}

/** The source was removed while a refresh was running: its store is dropped. */
internal class TsiptvSourceRemovedException : Exception("Source removed")

/** Network failure without a status (timeout, DNS, connection). Carries a code, never a URL. */
class SourceNetworkException(val code: String) : Exception("Network error: $code")
