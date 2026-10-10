package tss.t.tsiptv.feature.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.core.tsiptv.TsiptvSourceIds
import tss.t.tsiptv.core.tsiptv.TsiptvSourceService
import tss.t.tsiptv.usecase.playlist.ImportOutcome
import tss.t.tsiptv.usecase.playlist.PlaylistImporter

sealed interface PushResult {
    data class Pushed(val count: Int, val skippedFiles: Int, val remainingSyncs: Long) : PushResult
    data object NoneLeft : PushResult
    data object TooLarge : PushResult
    data object SignedOut : PushResult
    data object Failed : PushResult
}

enum class SyncMode { MERGE, REPLACE }

data class ApplyResult(val added: Int, val updated: Int, val removed: Int, val failed: List<String>)

/**
 * Device sync of playlist definitions through `users/{uid}/sync/current` (PRD §5). Pushing counts
 * against the daily quota in the same transaction; applying is free.
 */
class SyncService(
    private val cloud: AccountCloud,
    private val sessions: DeviceSessionManager,
    private val quotas: QuotaService,
    private val local: LocalDevice,
    private val database: IPTVDatabase,
    private val importer: PlaylistImporter,
    private val sources: TsiptvSourceService?,
    private val storage: KeyValueStorage,
    private val clock: () -> Long,
) {
    private val _incoming = MutableStateFlow<SyncDocument?>(null)

    /** A sync from another device that this one has not applied or dismissed yet. */
    val incoming: StateFlow<SyncDocument?> = _incoming

    /** The incoming sync already offered on Home in this app session (not offered again). */
    var promptedCreatedAt: Long = 0

    private fun seenKey(uid: String) = "sync_seen_created_at_$uid"

    suspend fun localPlaylists(): List<Playlist> = database.getAllPlaylists().first()

    suspend fun push(): PushResult {
        val uid = sessions.uid.value ?: return PushResult.SignedOut
        val build = SyncPlanner.build(localPlaylists())
        if (build.tooLarge) return PushResult.TooLarge
        val now = clock()
        val doc = SyncDocument(
            fromDeviceId = local.installationId(),
            fromDeviceName = local.name(),
            createdAt = now,
            playlistCount = build.payload.playlists.size.toLong(),
            payload = build.json,
        )
        var noneLeft = false
        return try {
            val after = cloud.pushSync(uid, doc) { stored ->
                val state = quotas.policy.rollover(stored, quotas.today())
                if (quotas.policy.canSync(state)) quotas.policy.afterSync(state, now) else {
                    noneLeft = true
                    null
                }
            }
            if (after == null) {
                if (noneLeft) PushResult.NoneLeft else PushResult.Failed
            } else {
                storage.putLong(seenKey(uid), now)
                _incoming.value = null
                PushResult.Pushed(build.payload.playlists.size, build.skippedFiles.size, quotas.policy.remainingSyncs(after))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PushResult.Failed
        }
    }

    /** The current sync document of the account (any device), or null. */
    suspend fun current(): SyncDocument? {
        val uid = sessions.uid.value ?: return null
        return try {
            cloud.loadSync(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Re-reads the slot and updates [incoming]. */
    suspend fun refresh() {
        val uid = sessions.uid.value
        if (uid == null) {
            _incoming.value = null
            return
        }
        val doc = current()
        val seen = storage.getLong(seenKey(uid))
        _incoming.value = doc.takeIf { SyncPlanner.isNewForThisDevice(it, local.installationId(), seen) }
    }

    /** "Để sau": hide this sync until a newer one arrives. */
    suspend fun dismiss(doc: SyncDocument) {
        val uid = sessions.uid.value ?: return
        storage.putLong(seenKey(uid), doc.createdAt)
        _incoming.value = null
    }

    suspend fun plan(doc: SyncDocument, mode: SyncMode): SyncPlan? {
        val payload = SyncPlanner.parse(doc.payload) ?: return null
        val local = localPlaylists()
        return when (mode) {
            SyncMode.MERGE -> SyncPlanner.planMerge(local, payload.playlists)
            SyncMode.REPLACE -> SyncPlanner.planReplace(local, payload.playlists)
        }
    }

    /**
     * Applies [plan]: downloads new and updated playlists one by one through the normal importer
     * (one failure never stops the others), then removes what replace removes.
     */
    suspend fun apply(doc: SyncDocument, plan: SyncPlan, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): ApplyResult {
        val failed = mutableListOf<String>()
        var added = 0
        var updated = 0
        var removed = 0
        val work = plan.toAdd.map { it to true } + plan.toUpdate.map { it to false }
        val total = work.size + plan.toRemove.size
        var done = 0
        for ((item, isNew) in work) {
            if (import(item)) {
                if (isNew) added++ else updated++
            } else {
                failed += item.name
            }
            progress(++done, total)
        }
        for (playlist in plan.toRemove) {
            try {
                if (TsiptvSourceIds.isSourcePlaylist(playlist.id) && sources != null) {
                    sources.remove(playlist.id)
                } else {
                    database.deleteProgramsForPlaylist(playlist.id)
                    database.deletePlaylistById(playlist.id)
                }
                removed++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed += playlist.name
            }
            progress(++done, total)
        }
        sessions.uid.value?.let { storage.putLong(seenKey(it), doc.createdAt) }
        _incoming.value = null
        return ApplyResult(added, updated, removed, failed)
    }

    private suspend fun import(item: SyncedPlaylist): Boolean = try {
        when (val outcome = importer.importFromUrl(item.name, item.url)) {
            is ImportOutcome.Imported -> true
            is ImportOutcome.SingleStream -> {
                importer.importSingleStream(item.name, item.url)
                true
            }
            is ImportOutcome.SourcePreview -> {
                val service = sources ?: return false
                val pending = service.resolve(outcome.preview)
                // A source that needs 18+ confirmation throws here; the user imports it by hand.
                service.store(pending, adultConfirmed = false)
                true
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
