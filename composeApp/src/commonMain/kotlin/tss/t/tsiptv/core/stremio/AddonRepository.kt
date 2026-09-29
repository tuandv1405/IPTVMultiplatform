package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.tsiptv.core.security.SecretCipher
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Small key-value persistence (multiplatform-settings in the app). */
interface AddonSettings {
    suspend fun getLong(key: String): Long?
    suspend fun putLong(key: String, value: Long)
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
}

/**
 * [SECRET_LOST]: the stored link cannot be decrypted any more (key reset, restore to another
 * device); the addon must be added again. [OFF]: disabled by the user.
 */
enum class AddonStatus { OK, OFF, UNREACHABLE, BLOCKED, SECRET_LOST }

/** An installed addon with its parsed manifest and decrypted transport (null if the key was lost). */
data class InstalledAddon(
    val stored: StoredAddon,
    val manifest: StremioManifest,
    val transport: StremioTransport?,
    /** The stored link will never decrypt (authentication failed). False while the key is only unavailable. */
    val secretLost: Boolean = false,
) {
    val id: String get() = stored.addonId
    val name: String get() = stored.name
    val host: String get() = stored.transportHost
    val enabled: Boolean get() = stored.enabled
    val isAdult: Boolean get() = stored.isAdult
    val status: AddonStatus
        get() = when {
            stored.blocked -> AddonStatus.BLOCKED
            // Disabled wins over any key state.
            !stored.enabled -> AddonStatus.OFF
            transport == null && secretLost -> AddonStatus.SECRET_LOST
            // Key store temporarily unavailable: retried with backoff, shown as not responding.
            transport == null -> AddonStatus.UNREACHABLE
            stored.failCount >= AddonRepository.UNREACHABLE_AFTER_FAILURES -> AddonStatus.UNREACHABLE
            else -> AddonStatus.OK
        }

    /** Enabled, but its link cannot be decrypted right now (key store unavailable; retried). */
    val keyUnavailable: Boolean get() = stored.enabled && !stored.blocked && transport == null && !secretLost

    /** Takes part in Discover, meta and stream fan-out. */
    val isActive: Boolean get() = stored.enabled && !stored.blocked && transport != null

    /**
     * F3: installed by a TS IPTV Source include (`ownerSourceId` = its playlist). Listed under
     * "From your sources", never on Discover, can be disabled but not removed on its own.
     */
    val isFromSource: Boolean get() = stored.ownerSourceId != null

    override fun toString(): String = "InstalledAddon(id=$id, host=$host, enabled=$enabled)"
}

/** Everything the preview sheet shows before saving (PRD §1). */
data class AddonPreview(
    val transport: StremioTransport,
    val manifest: StremioManifest,
    val rawManifestJson: String,
    /** The installed addon with the same manifest id (→ `addon_replace_existing`). */
    val existing: InstalledAddon?,
) {
    val isHttp: Boolean get() = !transport.isHttps
    val isP2p: Boolean get() = manifest.hints.isP2p
    val isAdult: Boolean get() = manifest.hints.isAdult
    val providesStreams: Boolean get() = ResourceMatcher.declaresResource(manifest, StremioResource.STREAM)
    val catalogCount: Int get() = manifest.catalogList.size
}

sealed interface AddonPreviewResult {
    data class Ready(val preview: AddonPreview) : AddonPreviewResult
    data class InvalidUrl(val error: ManifestUrlError) : AddonPreviewResult
    data object Unreachable : AddonPreviewResult
    data class InvalidManifest(val field: String?) : AddonPreviewResult
    /** `configurationRequired`: open [configureUrl] and paste the configured manifest link. */
    data class NeedsConfiguration(val configureUrl: String, val host: String) : AddonPreviewResult
    data object Blocked : AddonPreviewResult
}

sealed interface AddonRefreshResult {
    data class Updated(val version: String) : AddonRefreshResult
    data object Unchanged : AddonRefreshResult
    data object Failed : AddonRefreshResult
    data object Blocked : AddonRefreshResult
}

/** A Discover home row (PRD §5). */
data class BoardRow(
    val addon: InstalledAddon,
    val catalog: ManifestCatalog,
    val request: CatalogRequest,
) {
    val key: String get() = "${addon.id}|${catalog.type}|${catalog.id}"
}

/** One addon/catalog answer of a search fan-out. */
data class SearchGroup(
    val addon: InstalledAddon,
    val catalog: ManifestCatalog,
    val items: List<StremioMeta>,
    val failed: Boolean,
) {
    val key: String get() = "${addon.id}|${catalog.type}|${catalog.id}"
}

data class SearchState(
    /** Groups that answered with items, in addon then manifest order. */
    val groups: List<SearchGroup>,
    val pending: Int,
    val failedAddons: Int,
) {
    val isDone: Boolean get() = pending == 0
}

data class MetaAggregate(
    val meta: StremioMeta?,
    /** The addon whose answer was used first (null when built from the preview). */
    val sourceAddonId: String?,
    val failedAddons: Int,
)

/** One addon's streams in the picker. */
data class StreamGroup(
    val addon: InstalledAddon?,
    val streams: List<ClassifiedStream>,
    val loading: Boolean,
    val failed: Boolean,
) {
    val addonId: String get() = addon?.id ?: INLINE
    companion object {
        const val INLINE = "__inline__"
    }
}

data class StreamsState(val groups: List<StreamGroup>) {
    val loading: Boolean get() = groups.any { it.loading }
    val failedAddons: Int get() = groups.count { it.failed }
    val hasPlayable: Boolean get() = groups.any { g -> g.streams.any { it.isSelectable } }
}

/**
 * Installed addons, their order and state, and the fan-out over them (PRD implementation step 3).
 *
 * Transport URLs are decrypted only in memory; nothing here logs a URL.
 */
@OptIn(ExperimentalAtomicApi::class)
class AddonRepository(
    private val store: StremioAddonStore,
    private val history: MediaHistoryStore,
    private val client: StremioClient,
    private val cipher: SecretCipher,
    private val blocklistFetcher: AddonBlocklistFetcher,
    private val settings: AddonSettings,
    private val nowMs: () -> Long,
    scope: CoroutineScope,
) {
    private val writeLock = Mutex()
    private val retryScope = scope
    /** Re-reads the addons (re-attempting decryption) while a key store is unavailable. */
    private val keyRetryTick = kotlinx.coroutines.flow.MutableStateFlow(0)
    private var keyRetryAttempt = 0
    private var keyRetryJob: kotlinx.coroutines.Job? = null
    /** addonId → (token, transport). Replaced as a whole: read from several threads, never mutated in place. */
    private val decrypted = AtomicReference<Map<String, Pair<String, StremioTransport?>>>(emptyMap())

    /** Installed addons in the user's order. */
    /** null until the database has emitted once (e.g. right after process death). */
    private val loadedAddons: StateFlow<List<InstalledAddon>?> = combine(store.observeAddons(), keyRetryTick) { rows, _ -> rows }
        .map<List<StoredAddon>, List<InstalledAddon>?> { rows ->
            rows.map { toInstalled(it) }.also { list -> scheduleKeyRetry(list.any { it.keyUnavailable }) }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val addons: StateFlow<List<InstalledAddon>> = loadedAddons
        .map { it.orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Discover exists only while at least one addon is enabled (PRD §5). null = not known yet: the
     * UI must not act on it (a restored Discover screen would be popped before Room emits).
     */
    val hasActiveAddons: Flow<Boolean?> = loadedAddons.map { all ->
        // F3: addons that come from a TS IPTV Source are not on Discover (PRD F3 §5 `stremio`).
        val list = all?.filter { !it.isFromSource }
        when {
            list == null -> null
            list.any { it.isActive } -> true
            // Only addons whose key is temporarily unavailable: not known yet (never a known false).
            list.any { it.keyUnavailable } -> null
            else -> false
        }
    }

    private fun scheduleKeyRetry(needed: Boolean) {
        if (!needed) {
            keyRetryAttempt = 0
            return
        }
        // After KEY_RETRY_MAX_ATTEMPTS failures no further retry is scheduled; hasActiveAddons then stays
        // unknown (null) for the rest of the process unless the addons change (a new Room emission).
        if (keyRetryJob?.isActive == true || keyRetryAttempt >= KEY_RETRY_MAX_ATTEMPTS) return
        val delayMs = (KEY_RETRY_FIRST_MS shl keyRetryAttempt).coerceAtMost(KEY_RETRY_MAX_MS)
        keyRetryAttempt++
        keyRetryJob = retryScope.launch {
            delay(delayMs)
            // Clear the reference first: bumping the tick re-runs the mapping synchronously, and it
            // must see no active job, or the next retry would not be scheduled.
            keyRetryJob = null
            keyRetryTick.value = keyRetryTick.value + 1
        }
    }

    private fun toInstalled(row: StoredAddon): InstalledAddon {
        val (transport, lost) = decryptTransport(row)
        val manifest = StremioManifestParser.parseOrNull(row.manifestJson)
            ?: StremioManifest(id = row.addonId, version = row.version, name = row.name, types = emptyList(), resources = emptyList())
        return InstalledAddon(row, manifest, transport, secretLost = lost)
    }

    private fun synchronizedDecrypt(row: StoredAddon): StremioTransport? = decryptTransport(row).first

    /**
     * (transport, lost). Successful and permanently failed decryptions are cached per token; a
     * transient failure (key store unavailable) is **not** cached, so the next read retries.
     */
    private fun decryptTransport(row: StoredAddon): Pair<StremioTransport?, Boolean> {
        val cached = decrypted.load()[row.addonId]
        if (cached != null && cached.first == row.transportUrlEnc) return cached.second to (cached.second == null)
        val transport = when (val r = cipher.decryptResult(row.transportUrlEnc)) {
            is SecretCipher.DecryptResult.Ok -> StremioTransport.parse(r.plain)
            SecretCipher.DecryptResult.Invalid -> null
            SecretCipher.DecryptResult.KeyUnavailable -> return null to false
        }
        while (true) {
            val current = decrypted.load()
            if (decrypted.compareAndSet(current, current + (row.addonId to (row.transportUrlEnc to transport)))) break
        }
        return transport to (transport == null)
    }

    suspend fun current(): List<InstalledAddon> = store.getAddons().map { toInstalled(it) }

    fun activeAddons(list: List<InstalledAddon> = addons.value): List<InstalledAddon> = list.filter { it.isActive }

    // -----------------------------------------------------------------------------------------
    // Add / manage (PRD §1, §2)
    // -----------------------------------------------------------------------------------------

    suspend fun preview(input: String): AddonPreviewResult {
        val transport = when (val r = ManifestUrlNormalizer.normalize(input)) {
            is ManifestUrlResult.Error -> return AddonPreviewResult.InvalidUrl(r.error)
            is ManifestUrlResult.Ok -> r.transport
        }
        checkBlocklistIfDue()
        val blocklist = cachedBlocklist()
        if (blocklist.isHostBlocked(transport.host, transport.port, transport.scheme)) return AddonPreviewResult.Blocked
        return when (val r = client.fetchManifest(transport)) {
            is ManifestFetchResult.Unreachable -> AddonPreviewResult.Unreachable
            is ManifestFetchResult.Invalid -> AddonPreviewResult.InvalidManifest(r.field)
            is ManifestFetchResult.Success -> {
                val manifest = r.manifest
                when {
                    blocklist.isIdBlocked(manifest.id) -> AddonPreviewResult.Blocked
                    manifest.hints.isConfigurationRequired ->
                        AddonPreviewResult.NeedsConfiguration(transport.configureUrl, transport.displayHost)
                    else -> AddonPreviewResult.Ready(
                        AddonPreview(transport, manifest, r.rawJson, current().firstOrNull { it.id == manifest.id })
                    )
                }
            }
        }
    }

    /** Saves a previewed addon. Replacing keeps the position and enabled state (PRD §1). */
    suspend fun install(preview: AddonPreview): InstalledAddon = writeLock.withLock {
        val now = nowMs()
        val rows = store.getAddons()
        val existing = rows.firstOrNull { it.addonId == preview.manifest.id }
        val row = StoredAddon(
            addonId = preview.manifest.id,
            transportUrlEnc = cipher.encrypt(preview.transport.manifestUrl),
            transportHost = preview.transport.displayHost,
            manifestJson = preview.rawManifestJson,
            name = preview.manifest.name,
            version = preview.manifest.versionText,
            logoUrl = preview.manifest.logoUrl,
            enabled = existing?.enabled ?: true,
            sortOrder = existing?.sortOrder ?: ((rows.maxOfOrNull { it.sortOrder } ?: -1) + 1),
            isAdult = preview.manifest.hints.isAdult,
            isP2p = preview.manifest.hints.isP2p,
            // Added by the user: theirs from now on, even if a TS IPTV Source had installed it (F3).
            ownerSourceId = null,
            addedAt = existing?.addedAt ?: now,
            lastFetchedAt = now,
        )
        store.upsertAddon(row)
        if (existing != null && existing.transportUrlEnc != row.transportUrlEnc) {
            toInstalled(existing).transport?.let { client.invalidate(it) }
        }
        toInstalled(row)
    }

    /**
     * F3: installs an addon for a TS IPTV Source include, owned by [ownerSourceId] (its playlist).
     * An addon the user already added keeps belonging to the user (reused, never taken over); an
     * addon owned by another source stays with that source. New rows are enabled (PRD F3 §5).
     */
    suspend fun installOwned(preview: AddonPreview, ownerSourceId: String): InstalledAddon = writeLock.withLock {
        val existing = store.getAddon(preview.manifest.id)
        // The user's own addon is never taken over.
        if (existing != null && existing.ownerSourceId == null) return@withLock toInstalled(existing)
        val owners = ownersOf(existing)
        if (existing != null && ownerSourceId !in owners) {
            // Owned by another source too: add this owner, keep that source's row (QC F3 #20).
            val shared = existing.copy(ownerSourceId = (owners + ownerSourceId).joinToString(OWNER_SEPARATOR))
            store.upsertAddon(shared)
            return@withLock toInstalled(shared)
        }
        val now = nowMs()
        val rows = store.getAddons()
        val row = StoredAddon(
            addonId = preview.manifest.id,
            transportUrlEnc = cipher.encrypt(preview.transport.manifestUrl),
            transportHost = preview.transport.displayHost,
            manifestJson = preview.rawManifestJson,
            name = preview.manifest.name,
            version = preview.manifest.versionText,
            logoUrl = preview.manifest.logoUrl,
            enabled = existing?.enabled ?: true,
            sortOrder = existing?.sortOrder ?: ((rows.maxOfOrNull { it.sortOrder } ?: -1) + 1),
            isAdult = preview.manifest.hints.isAdult,
            isP2p = preview.manifest.hints.isP2p,
            ownerSourceId = (owners + ownerSourceId).distinct().joinToString(OWNER_SEPARATOR),
            addedAt = existing?.addedAt ?: now,
            lastFetchedAt = now,
            blocked = existing?.blocked ?: false,
        )
        store.upsertAddon(row)
        if (existing != null && existing.transportUrlEnc != row.transportUrlEnc) {
            toInstalled(existing).transport?.let { client.invalidate(it) }
        }
        toInstalled(row)
    }

    /**
     * F3: [ownerSourceId] no longer owns the addons it owns except [keep]. An addon that another
     * source still owns stays (owners are ref-counted, QC F3 #20); the rest are removed with their history.
     */
    suspend fun removeOwnedBy(ownerSourceId: String, keep: Set<String> = emptySet()) {
        val toRemove = writeLock.withLock {
            store.getAddons().filter { ownerSourceId in ownersOf(it) && it.addonId !in keep }.mapNotNull { row ->
                val remaining = ownersOf(row) - ownerSourceId
                if (remaining.isEmpty()) row.addonId
                else {
                    store.upsertAddon(row.copy(ownerSourceId = remaining.joinToString(OWNER_SEPARATOR)))
                    null
                }
            }
        }
        toRemove.forEach { remove(it) }
    }

    /** F3: ids of the addons [ownerSourceId] owns (alone or with other sources). */
    suspend fun ownedBy(ownerSourceId: String): Set<String> =
        store.getAddons().filter { ownerSourceId in ownersOf(it) }.map { it.addonId }.toSet()

    private fun ownersOf(row: StoredAddon?): List<String> =
        row?.ownerSourceId?.split(OWNER_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    suspend fun setEnabled(addonId: String, enabled: Boolean) = writeLock.withLock {
        val row = store.getAddon(addonId) ?: return@withLock
        if (row.blocked && enabled) return@withLock
        store.upsertAddon(row.copy(enabled = enabled))
    }

    /** Move up (−1) or down (+1) one position. */
    suspend fun move(addonId: String, delta: Int) = writeLock.withLock {
        val rows = store.getAddons().sortedBy { it.sortOrder }.toMutableList()
        val index = rows.indexOfFirst { it.addonId == addonId }
        val target = index + delta
        if (index < 0 || target !in rows.indices) return@withLock
        rows.add(target, rows.removeAt(index))
        persistOrder(rows)
    }

    /** Drag-and-drop: the full new order. Unknown ids are ignored; missing ones keep their order at the end. */
    suspend fun reorder(orderedIds: List<String>) = writeLock.withLock {
        val rows = store.getAddons().sortedBy { it.sortOrder }
        val byId = rows.associateBy { it.addonId }
        val ordered = orderedIds.mapNotNull { byId[it] } + rows.filter { it.addonId !in orderedIds }
        persistOrder(ordered)
    }

    private suspend fun persistOrder(rows: List<StoredAddon>) {
        rows.forEachIndexed { i, row -> if (row.sortOrder != i) store.upsertAddon(row.copy(sortOrder = i)) }
    }

    /** Removes the addon and its watch history. */
    suspend fun remove(addonId: String) = writeLock.withLock {
        val row = store.getAddon(addonId) ?: return@withLock
        toInstalled(row).transport?.let { client.invalidate(it) }
        store.deleteAddon(addonId)
        while (true) {
            val current = decrypted.load()
            if (decrypted.compareAndSet(current, current - addonId)) break
        }
    }

    /** "Update now" / daily refresh. After 3 consecutive failures the addon shows as unreachable. */
    suspend fun refresh(addonId: String): AddonRefreshResult {
        val row = store.getAddon(addonId) ?: return AddonRefreshResult.Failed
        val installed = toInstalled(row)
        val transport = installed.transport ?: return markFailure(row, if (installed.secretLost) "secret_lost" else "key_unavailable")
        if (cachedBlocklist().isBlocked(addonId, transport)) return AddonRefreshResult.Blocked
        return when (val r = client.fetchManifest(transport)) {
            is ManifestFetchResult.Unreachable -> markFailure(row, r.error.code)
            is ManifestFetchResult.Invalid -> markFailure(row, "invalid_manifest")
            is ManifestFetchResult.Success -> {
                if (r.manifest.id != addonId) return markFailure(row, "id_changed")
                if (r.manifest.hints.isConfigurationRequired) return markFailure(row, "needs_configuration")
                writeLock.withLock {
                    val latest = store.getAddon(addonId) ?: return AddonRefreshResult.Failed
                    store.upsertAddon(
                        latest.copy(
                            manifestJson = r.rawJson,
                            name = r.manifest.name,
                            version = r.manifest.versionText,
                            logoUrl = r.manifest.logoUrl,
                            isAdult = r.manifest.hints.isAdult,
                            isP2p = r.manifest.hints.isP2p,
                            lastFetchedAt = nowMs(),
                            failCount = 0,
                            lastError = null,
                        )
                    )
                }
                client.invalidate(transport)
                if (r.manifest.versionText != row.version) AddonRefreshResult.Updated(r.manifest.versionText)
                else AddonRefreshResult.Unchanged
            }
        }
    }

    private suspend fun markFailure(row: StoredAddon, code: String): AddonRefreshResult = writeLock.withLock {
        val latest = store.getAddon(row.addonId) ?: return AddonRefreshResult.Failed
        store.upsertAddon(latest.copy(failCount = latest.failCount + 1, lastError = code, lastFetchedAt = nowMs()))
        AddonRefreshResult.Failed
    }

    /** At app start: refresh manifests older than a day, then the blocklist. Never throws. */
    suspend fun dailyMaintenance() {
        checkBlocklistIfDue()
        val now = nowMs()
        store.getAddons().filter { now - it.lastFetchedAt >= DAY_MS || now < it.lastFetchedAt }.forEach {
            runCatchingNonCancel { refresh(it.addonId) }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Blocklist (kill switch)
    // -----------------------------------------------------------------------------------------

    suspend fun cachedBlocklist(): AddonBlocklist = settings.getString(KEY_BLOCKLIST)?.let {
        runCatching { StremioJson.decodeFromString(AddonBlocklist.serializer(), it) }.getOrNull()
    } ?: AddonBlocklist.EMPTY

    /** Fetches the blocklist at most once a day and disables matching addons (`addon_blocked`). */
    suspend fun checkBlocklistIfDue(force: Boolean = false) {
        val now = nowMs()
        if (!force && !AddonBlocklistFetcher.isDue(settings.getLong(KEY_BLOCKLIST_CHECKED), now)) return
        val list = blocklistFetcher.fetch() ?: return
        settings.putString(KEY_BLOCKLIST, StremioJson.encodeToString(AddonBlocklist.serializer(), list))
        settings.putLong(KEY_BLOCKLIST_CHECKED, now)
        applyBlocklist(list)
    }

    suspend fun applyBlocklist(list: AddonBlocklist) = writeLock.withLock {
        store.getAddons().forEach { row ->
            val transport = synchronizedDecrypt(row)
            val blocked = list.isIdBlocked(row.addonId) ||
                (transport?.let { list.isHostBlocked(it.host, it.port, it.scheme) } ?: false)
            if (blocked != row.blocked) {
                store.upsertAddon(row.copy(blocked = blocked, enabled = if (blocked) false else row.enabled))
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Fan-out (PRD §5–§7)
    // -----------------------------------------------------------------------------------------

    /** Board rows of enabled, non-adult addons, grouped by addon in addon order. Source-owned addons are not on Discover. */
    fun boardRows(list: List<InstalledAddon> = addons.value): List<BoardRow> =
        activeAddons(list).filter { !it.isAdult && !it.isFromSource }.flatMap { addon ->
            ResourceMatcher.boardCatalogs(addon.manifest).mapNotNull { request ->
                ResourceMatcher.findCatalog(addon.manifest, request.type, request.catalogId)?.let { BoardRow(addon, it, request) }
            }
        }

    suspend fun catalog(addon: InstalledAddon, request: CatalogRequest): AddonResult<List<StremioMeta>> {
        val transport = addon.transport ?: return AddonResult.Failure(AddonError(AddonError.Kind.NETWORK))
        return client.catalog(transport, request)
    }

    /**
     * Search fan-out to every catalogue declaring `search` of every enabled, non-adult addon.
     * Emits progressively; cancelling the collector cancels in-flight requests.
     */
    fun search(query: String, list: List<InstalledAddon> = addons.value): Flow<SearchState> = channelFlow {
        val targets = activeAddons(list).filter { !it.isAdult && !it.isFromSource }.flatMap { addon ->
            ResourceMatcher.searchCatalogs(addon.manifest, query).mapNotNull { request ->
                ResourceMatcher.findCatalog(addon.manifest, request.type, request.catalogId)?.let { Triple(addon, it, request) }
            }
        }
        val results = arrayOfNulls<SearchGroup>(targets.size)
        val lock = Mutex()
        suspend fun emitState() {
            val snapshot = lock.withLock { results.toList() }
            val done = snapshot.filterNotNull()
            send(
                SearchState(
                    groups = done.filter { it.items.isNotEmpty() },
                    pending = snapshot.count { it == null },
                    failedAddons = done.filter { it.failed }.map { it.addon.id }.distinct().size,
                )
            )
        }
        emitState()
        targets.forEachIndexed { i, (addon, catalog, request) ->
            launch {
                val r = catalog(addon, request)
                lock.withLock {
                    results[i] = SearchGroup(addon, catalog, r.valueOr(emptyList()), failed = r.isFailure)
                }
                emitState()
            }
        }
    }

    /**
     * Meta from every addon whose `meta` resource matches, in addon order: the first non-empty
     * answer wins, missing fields are filled from later ones; else the catalogue [preview].
     */
    suspend fun meta(type: String, id: String, preview: StremioMeta? = null, list: List<InstalledAddon> = addons.value): MetaAggregate =
        coroutineScope {
            val candidates = activeAddons(list).filter { ResourceMatcher.supportsMeta(it.manifest, type, id) && it.transport != null }
            val answers = candidates.map { addon -> async { addon to client.meta(addon.transport!!, type, id) } }.awaitAll()
            val found = answers.mapNotNull { (addon, r) -> r.valueOrNull?.let { addon to it } }
            val failed = answers.count { (_, r) -> r is AddonResult.Failure && !r.error.isNotFound }
            if (found.isEmpty()) {
                MetaAggregate(preview, null, failed)
            } else {
                var merged = found.first().second
                found.drop(1).forEach { merged = merged.fillMissingFrom(it.second) }
                MetaAggregate(merged.fillMissingFrom(preview), found.first().first.id, failed)
            }
        }

    /**
     * Streams for a video: inline `video.streams` exclusively when present (no request, AC-S18);
     * else every matching addon in parallel, emitted progressively in addon order.
     */
    fun streams(
        type: String,
        videoId: String,
        inline: List<StremioStream>? = null,
        list: List<InstalledAddon> = addons.value,
    ): Flow<StreamsState> = channelFlow {
        if (!inline.isNullOrEmpty()) {
            send(StreamsState(listOf(StreamGroup(null, StreamClassifier.classifyAll(inline), loading = false, failed = false))))
            return@channelFlow
        }
        val candidates = activeAddons(list).filter { ResourceMatcher.supportsStream(it.manifest, type, videoId) }
        val groups = candidates.map { StreamGroup(it, emptyList(), loading = true, failed = false) }.toTypedArray()
        val lock = Mutex()
        send(StreamsState(groups.toList()))
        candidates.forEachIndexed { i, addon ->
            launch {
                val r = client.streams(addon.transport!!, type, videoId)
                val snapshot = lock.withLock {
                    groups[i] = StreamGroup(
                        addon,
                        StreamClassifier.classifyAll(r.valueOr(emptyList())),
                        loading = false,
                        failed = r is AddonResult.Failure && !r.error.isNotFound,
                    )
                    groups.toList()
                }
                send(StreamsState(snapshot))
            }
        }
    }

    fun addonById(addonId: String?, list: List<InstalledAddon> = addons.value): InstalledAddon? =
        addonId?.let { id -> list.firstOrNull { it.id == id } }

    companion object {
        /** `ownerSourceId` holds every owning source playlist, newline-separated (ids have no newline). */
        const val OWNER_SEPARATOR = "\n"
        const val UNREACHABLE_AFTER_FAILURES = 3
        /** Key store retries: 1 s, 2 s, 4 s … capped at 30 s, at most 8 attempts per episode. */
        const val KEY_RETRY_FIRST_MS = 1_000L
        const val KEY_RETRY_MAX_MS = 30_000L
        const val KEY_RETRY_MAX_ATTEMPTS = 8
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val KEY_BLOCKLIST = "stremio_blocklist_json"
        const val KEY_BLOCKLIST_CHECKED = "stremio_blocklist_checked_at"
    }
}

private suspend inline fun runCatchingNonCancel(block: () -> Unit) {
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
    }
}
