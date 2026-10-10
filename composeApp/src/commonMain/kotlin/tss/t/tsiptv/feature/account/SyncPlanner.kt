package tss.t.tsiptv.feature.account

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.feature.lan.LanValidation

/** One playlist definition in the account's sync slot. Never channels or streams (PRD §5.1). */
@Serializable
data class SyncedPlaylist(
    val id: String,
    val name: String,
    val url: String,
    val epgUrls: List<String> = emptyList(),
    /** Reserved: playlists carry no headers today. */
    val headers: Map<String, String> = emptyMap(),
    val sourceType: String = PlaylistSourceType.URL.name,
    val format: String = "UNKNOWN",
    val lastUpdated: Long = 0,
) {
    override fun toString(): String = "SyncedPlaylist(id=$id, name=$name)"
}

@Serializable
data class SyncPayload(
    val v: Int = 1,
    val playlists: List<SyncedPlaylist> = emptyList(),
)

/** `users/{uid}/sync/current` (PRD §5.1). [payload] is a [SyncPayload] as JSON. */
@Serializable
data class SyncDocument(
    val v: Long = 1,
    val fromDeviceId: String = "",
    val fromDeviceName: String = "",
    val createdAt: Long = 0,
    val playlistCount: Long = 0,
    val payload: String = "",
) {
    override fun toString(): String = "SyncDocument(from=$fromDeviceName, createdAt=$createdAt, count=$playlistCount)"
}

/** What a push would store, and what it leaves out. */
data class SyncBuild(val payload: SyncPayload, val skippedFiles: List<String>, val json: String) {
    val tooLarge: Boolean
        get() = payload.playlists.size > SyncPlanner.MAX_PLAYLISTS || json.encodeToByteArray().size > SyncPlanner.MAX_PAYLOAD_BYTES
}

/** A merge or replace, computed before anything changes (PRD §5.2). */
data class SyncPlan(
    /** Synced playlists this device does not have. */
    val toAdd: List<SyncedPlaylist>,
    /** Same playlist on both sides; the synced copy wins (re-imported under its name). */
    val toUpdate: List<SyncedPlaylist>,
    /** Local playlists that will be deleted (replace only). Shown to the user before applying. */
    val toRemove: List<Playlist>,
    /** Same playlist, nothing to do. */
    val unchanged: Int,
) {
    val isEmpty: Boolean get() = toAdd.isEmpty() && toUpdate.isEmpty() && toRemove.isEmpty()
}

/** Pure push / merge / replace logic. */
object SyncPlanner {
    const val MAX_PLAYLISTS = 100
    const val MAX_PAYLOAD_BYTES = 256 * 1024

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Link playlists (and link sources) only: file playlists have no content to sync. */
    fun build(local: List<Playlist>): SyncBuild {
        val (syncable, skipped) = local.partition { it.sourceType == PlaylistSourceType.URL && LanValidation.isHttpUrl(it.url) }
        val payload = SyncPayload(
            playlists = syncable.map {
                SyncedPlaylist(
                    id = it.id,
                    name = it.name,
                    url = it.url,
                    epgUrls = it.epgUrls,
                    sourceType = it.sourceType.name,
                    format = it.format,
                    lastUpdated = it.lastUpdated,
                )
            },
        )
        return SyncBuild(payload, skipped.map { it.name }, json.encodeToString(SyncPayload.serializer(), payload))
    }

    /** null when the payload is not a readable [SyncPayload]. Unusable entries are dropped. */
    fun parse(text: String): SyncPayload? = runCatching { json.decodeFromString(SyncPayload.serializer(), text) }
        .getOrNull()
        ?.let { p -> p.copy(playlists = p.playlists.filter { LanValidation.isHttpUrl(it.url) && it.name.isNotBlank() }.distinctBy { it.id }) }

    private fun matches(local: Playlist, remote: SyncedPlaylist) =
        local.id == remote.id || (local.sourceType == PlaylistSourceType.URL && local.url == remote.url)

    private fun differs(local: Playlist, remote: SyncedPlaylist) =
        local.name != remote.name || local.epgUrls != remote.epgUrls

    /**
     * Merge: union by id / URL. Same playlist on both sides: the newer `lastUpdated` wins, so the
     * synced copy is applied only when it is newer and different. Nothing is removed.
     */
    fun planMerge(local: List<Playlist>, remote: List<SyncedPlaylist>): SyncPlan {
        val toAdd = mutableListOf<SyncedPlaylist>()
        val toUpdate = mutableListOf<SyncedPlaylist>()
        var unchanged = 0
        for (r in remote) {
            val l = local.firstOrNull { matches(it, r) }
            when {
                l == null -> toAdd += r
                differs(l, r) && r.lastUpdated > l.lastUpdated -> toUpdate += r
                else -> unchanged++
            }
        }
        return SyncPlan(toAdd, toUpdate, emptyList(), unchanged)
    }

    /**
     * Replace: the local set becomes the synced set. Local playlists with no synced counterpart are
     * removed (including file playlists, which cannot come back); matched ones take the synced copy.
     */
    fun planReplace(local: List<Playlist>, remote: List<SyncedPlaylist>): SyncPlan {
        val toAdd = mutableListOf<SyncedPlaylist>()
        val toUpdate = mutableListOf<SyncedPlaylist>()
        var unchanged = 0
        for (r in remote) {
            val l = local.firstOrNull { matches(it, r) }
            when {
                l == null -> toAdd += r
                differs(l, r) -> toUpdate += r
                else -> unchanged++
            }
        }
        val toRemove = local.filter { l -> remote.none { r -> matches(l, r) } }
        return SyncPlan(toAdd, toUpdate, toRemove, unchanged)
    }

    /**
     * Whether [doc] should be offered on this device: it is newer than what this device last pushed
     * or applied, and it came from another device.
     */
    fun isNewForThisDevice(doc: SyncDocument?, myDeviceId: String, lastSeenCreatedAt: Long): Boolean =
        doc != null && doc.fromDeviceId != myDeviceId && doc.createdAt > lastSeenCreatedAt
}
