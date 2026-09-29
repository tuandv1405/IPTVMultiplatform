package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.flow.Flow

/**
 * A row of `stremio_addons` (PRD "Data model"). [transportUrlEnc] is the manifest URL encrypted
 * with `SecretCipher`; it is never shown, logged or sent anywhere.
 */
data class StoredAddon(
    val addonId: String,
    val transportUrlEnc: String,
    val transportHost: String,
    val manifestJson: String,
    val name: String,
    val version: String,
    val logoUrl: String?,
    val enabled: Boolean = true,
    val sortOrder: Int,
    val isAdult: Boolean = false,
    val isP2p: Boolean = false,
    val ownerSourceId: String? = null,
    val addedAt: Long,
    val lastFetchedAt: Long,
    val failCount: Int = 0,
    val lastError: String? = null,
    val blocked: Boolean = false,
) {
    override fun toString(): String = "StoredAddon(addonId=$addonId, host=$transportHost, enabled=$enabled, order=$sortOrder)"
}

/** Persistence of installed addons. Implemented over Room (and in memory for tests/previews). */
interface StremioAddonStore {
    /** All addons ordered by `sortOrder`. */
    fun observeAddons(): Flow<List<StoredAddon>>
    suspend fun getAddons(): List<StoredAddon>
    suspend fun getAddon(addonId: String): StoredAddon?
    suspend fun upsertAddon(addon: StoredAddon)
    /** Deletes the addon **and its `media_history` rows** in one transaction. */
    suspend fun deleteAddon(addonId: String)
}

/** `media_history.sourceKind` values (shared with F3). */
object MediaSourceKind {
    const val STREMIO = "STREMIO"
    const val TSIPTV = "TSIPTV"
}

/** A row of `media_history`. Stream URLs are never stored (they expire). */
data class MediaHistoryRecord(
    val id: Long = 0,
    val sourceKind: String,
    val sourceId: String,
    val itemType: String,
    val itemId: String,
    val videoId: String,
    val title: String?,
    val subtitle: String?,
    val posterUrl: String?,
    val season: Int?,
    val episode: Int?,
    val lastAddonId: String?,
    val lastBingeGroup: String?,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
)

/** Both F2 tables, as exposed by `IPTVDatabase.stremioStores`. */
interface StremioStores : StremioAddonStore, MediaHistoryStore

interface MediaHistoryStore {
    /** Newest first. */
    fun observeHistory(): Flow<List<MediaHistoryRecord>>
    suspend fun find(sourceKind: String, sourceId: String, itemId: String, videoId: String): MediaHistoryRecord?
    /** Latest row of a video whichever addon supplied the meta (history is keyed by the title, not the addon). */
    suspend fun findVideo(sourceKind: String, itemId: String, videoId: String): MediaHistoryRecord?
    /** Latest row of an item (any video), for "last watched episode" and stream preference. */
    suspend fun latestForItem(sourceKind: String, itemId: String): MediaHistoryRecord?
    /** Insert or replace on the unique key (`sourceKind`, `sourceId`, `itemId`, `videoId`). */
    suspend fun upsert(record: MediaHistoryRecord)
    suspend fun delete(id: Long)
    /** Removes every row of a title (all its videos, from any addon). */
    suspend fun deleteForItem(sourceKind: String, itemId: String)

    /** Removes every row of one source (F3: a removed TS IPTV Source, `sourceId` = playlist id). */
    suspend fun deleteForSource(sourceKind: String, sourceId: String)
    suspend fun clear()
}
