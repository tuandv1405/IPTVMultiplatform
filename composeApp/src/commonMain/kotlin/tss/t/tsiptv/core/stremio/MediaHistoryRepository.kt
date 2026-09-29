package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The episode that replaces a finished one in Continue watching. */
data class NextEpisodeInfo(
    val videoId: String,
    val subtitle: String,
    val season: Int?,
    val episode: Int?,
)

/**
 * What is playing, for `media_history` (PRD §9). Built by the stream picker when a stream is
 * chosen. Contains no stream URL.
 */
data class MediaPlaybackContext(
    /** The addon whose catalogue/meta the title came from (`media_history.sourceId`). */
    val sourceAddonId: String,
    val itemType: String,
    val itemId: String,
    val videoId: String,
    val title: String,
    /** Episode label (`S1 · E3 Title`) or null for movies/tv. */
    val subtitle: String?,
    val posterUrl: String?,
    val season: Int?,
    val episode: Int?,
    /** The addon that provided the stream, and its `bingeGroup` (stream preference). */
    val streamAddonId: String?,
    val bingeGroup: String?,
    val isLive: Boolean,
    val next: NextEpisodeInfo? = null,
    /** Where playback should start (resume), in ms. */
    val startPositionMs: Long = 0,
    /**
     * [MediaSourceKind.STREMIO], or [MediaSourceKind.TSIPTV] for TS IPTV Source movies and episodes
     * (then [sourceAddonId] is the source's playlist id, and history stays within that source).
     */
    val sourceKind: String = MediaSourceKind.STREMIO,
)

/**
 * Watch history and Continue watching for addon titles.
 *
 * History is keyed by the **title and video** (`itemId`, `videoId`), not by which addon happened to
 * answer the meta request first: that can change between visits (addon order, an addon offline),
 * and resume and Continue watching must not split into several rows. The first row's `sourceId`
 * is kept for later saves (it ties the rows to an addon for the cascade on removal).
 */
class MediaHistoryRepository(
    private val store: MediaHistoryStore,
    private val nowMs: () -> Long,
) {
    val history: Flow<List<MediaHistoryRecord>> = store.observeHistory()

    /**
     * One entry per title (its latest row), unfinished, newest first. Rows without a known
     * duration (live `tv` items, streams that never loaded) are not resumable and are left out;
     * queued next episodes ([QUEUED_NEXT]) are kept.
     */
    /** Discover's Continue watching: addon titles only (TS IPTV Source titles live in their source's home). */
    val continueWatching: Flow<List<MediaHistoryRecord>> = store.observeHistory().map { rows ->
        resumable(rows.filter { it.sourceKind == MediaSourceKind.STREMIO })
    }

    /** F3: Continue watching of one TS IPTV Source (`sourceId` = its playlist id). */
    fun continueWatchingForSource(playlistId: String): Flow<List<MediaHistoryRecord>> = store.observeHistory().map { rows ->
        resumable(rows.filter { it.sourceKind == MediaSourceKind.TSIPTV && it.sourceId == playlistId })
    }

    /**
     * The latest *resumable* row per title: filter before distinctBy, so a newer row without a
     * duration (e.g. a dead episode link) never hides a half-watched one.
     */
    private fun resumable(rows: List<MediaHistoryRecord>): List<MediaHistoryRecord> =
        rows.filter { it.durationMs > 0 || it.durationMs == QUEUED_NEXT }
            .sortedByDescending { it.updatedAt }
            .distinctBy { it.sourceKind to it.itemId }
            .filter { !it.finished }

    suspend fun find(context: MediaPlaybackContext): MediaHistoryRecord? = findRow(context, context.videoId)

    private suspend fun findRow(context: MediaPlaybackContext, videoId: String): MediaHistoryRecord? =
        if (context.sourceKind == MediaSourceKind.TSIPTV) {
            // Scoped to the source: two sources may use the same item ids.
            store.find(MediaSourceKind.TSIPTV, context.sourceAddonId, context.itemId, videoId)
        } else {
            store.findVideo(MediaSourceKind.STREMIO, context.itemId, videoId)
        }

    suspend fun latestForItem(itemId: String, sourceKind: String = MediaSourceKind.STREMIO): MediaHistoryRecord? =
        store.latestForItem(sourceKind, itemId)

    /**
     * Saves progress (every 10 s and on pause/stop). At ≥ 95 % the row is finished; a finished
     * episode is replaced in Continue watching by [MediaPlaybackContext.next] with position 0.
     */
    suspend fun saveProgress(context: MediaPlaybackContext, positionMs: Long, durationMs: Long) {
        val now = nowMs()
        val finished = !context.isLive && MediaRules.isFinished(positionMs, durationMs)
        val kind = context.sourceKind
        val existing = findRow(context, context.videoId)
        val sourceId = existing?.sourceId ?: context.sourceAddonId
        // An item that never reported a duration (stream failed, still loading, gave up) has nothing
        // to resume: never write it, and never overwrite a known position/duration with it.
        if (!context.isLive && durationMs <= 0) return
        store.upsert(
            MediaHistoryRecord(
                sourceKind = kind,
                sourceId = sourceId,
                itemType = context.itemType,
                itemId = context.itemId,
                videoId = context.videoId,
                title = context.title,
                subtitle = context.subtitle,
                posterUrl = context.posterUrl,
                season = context.season,
                episode = context.episode,
                lastAddonId = context.streamAddonId,
                lastBingeGroup = context.bingeGroup,
                positionMs = if (context.isLive) 0 else positionMs.coerceAtLeast(0),
                durationMs = if (context.isLive) 0 else durationMs.coerceAtLeast(0),
                finished = finished,
                updatedAt = now,
            )
        )
        val next = context.next
        if (finished && next != null) {
            val nextRow = findRow(context, next.videoId)
            if (nextRow == null || nextRow.finished) {
                store.upsert(
                    MediaHistoryRecord(
                        sourceKind = kind,
                        sourceId = nextRow?.sourceId ?: sourceId,
                        itemType = context.itemType,
                        itemId = context.itemId,
                        videoId = next.videoId,
                        title = context.title,
                        subtitle = next.subtitle,
                        posterUrl = context.posterUrl,
                        season = next.season,
                        episode = next.episode,
                        lastAddonId = context.streamAddonId,
                        lastBingeGroup = context.bingeGroup,
                        positionMs = 0,
                        durationMs = QUEUED_NEXT,
                        finished = false,
                        updatedAt = now + 1,
                    )
                )
            } else {
                // The next episode already has a (partly watched) row: make it the title's latest row,
                // or the finished episode would hide the series from Continue watching.
                store.upsert(nextRow.copy(updatedAt = now + 1))
            }
        }
    }

    /** Removes a title from history (all its episodes, from any addon). */
    suspend fun remove(record: MediaHistoryRecord) = store.deleteForItem(record.sourceKind, record.itemId)

    suspend fun clear() = store.clear()

    companion object {
        /** `durationMs` of a queued next episode (not played yet, duration unknown). */
        const val QUEUED_NEXT = -1L
    }
}
