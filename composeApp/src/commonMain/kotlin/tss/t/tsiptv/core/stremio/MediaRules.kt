package tss.t.tsiptv.core.stremio

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** One season of a series, ready for the season selector (PRD §6). */
data class SeasonGroup(
    /** null for videos without a season number. */
    val season: Int?,
    val episodes: List<StremioVideo>,
) {
    val isSpecials: Boolean get() = season == 0
}

/**
 * Pure rules for the detail page, the stream picker and watch history (PRD §6, §7, §9).
 * No I/O, no clock: callers pass `nowMs`.
 */
@OptIn(ExperimentalTime::class)
object MediaRules {

    /** Epoch ms of an ISO-8601 date (`released`), or null when absent/unparseable. */
    fun parseReleased(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val text = value.trim()
        return try {
            Instant.parse(text).toEpochMilliseconds()
        } catch (_: Exception) {
            // Date only: "1941-11-28".
            try {
                Instant.parse(text + "T00:00:00Z").toEpochMilliseconds()
            } catch (_: Exception) {
                null
            }
        }
    }

    /** `released` in the future → `meta_upcoming`, not selectable. Unknown dates are selectable. */
    fun isUpcoming(video: StremioVideo, nowMs: Long): Boolean =
        parseReleased(video.released)?.let { it > nowMs } ?: false

    /**
     * Seasons ascending with season 0 (specials) last and videos without a season after them;
     * episodes sorted by `episode` (videos without a number keep their order at the end).
     */
    fun groupSeasons(videos: List<StremioVideo>): List<SeasonGroup> {
        val bySeason = LinkedHashMap<Int?, MutableList<StremioVideo>>()
        // Video ids are list keys and stream-request ids: keep the first of any duplicate.
        videos.filter { !it.id.isNullOrBlank() }.distinctBy { it.id }.forEach { bySeason.getOrPut(it.season) { mutableListOf() } += it }
        val order = bySeason.keys.sortedWith(compareBy<Int?>(
            { when (it) { null -> 2; 0 -> 1; else -> 0 } },
            { it ?: 0 },
        ))
        return order.map { season ->
            val episodes = bySeason.getValue(season).withIndex()
                .sortedWith(compareBy({ it.value.episodeNumber == null }, { it.value.episodeNumber ?: 0 }, { it.index }))
                .map { it.value }
            SeasonGroup(season, episodes)
        }
    }

    /** Series-like when the meta has more than one video or any video with a season. */
    fun isSeriesLike(meta: StremioMeta): Boolean {
        val videos = meta.videos.orEmpty()
        return videos.size > 1 || videos.any { it.season != null } ||
            (videos.size == 1 && videos[0].id != meta.id)
    }

    /** "S1 · E3 Title" (PRD §9), "S1 · E3" without a title, the title alone without numbers. */
    fun episodeLabel(video: StremioVideo): String {
        val numbers = listOfNotNull(video.season?.let { "S$it" }, video.episodeNumber?.let { "E$it" }).joinToString(" · ")
        val title = video.displayTitle
        return when {
            numbers.isEmpty() -> title
            title.isEmpty() -> numbers
            else -> "$numbers $title"
        }
    }

    const val RESUME_MIN_MS = 60_000L
    const val FINISHED_RATIO = 0.95

    /** An item counts as finished at ≥ 95 % of its duration. */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= (durationMs * FINISHED_RATIO).toLong()

    /** Resume from the saved position only if it is between 60 s and 95 % (PRD §9); else from 0. */
    fun resumePosition(positionMs: Long, durationMs: Long): Long =
        if (positionMs >= RESUME_MIN_MS && (durationMs <= 0 || !isFinished(positionMs, durationMs))) positionMs else 0L

    /**
     * The episode after [videoId] in watching order (seasons ascending, specials excluded unless
     * the current one is a special), if it is already released.
     */
    fun nextEpisode(videos: List<StremioVideo>, videoId: String, nowMs: Long): StremioVideo? {
        val current = videos.firstOrNull { it.id == videoId } ?: return null
        val groups = groupSeasons(videos).filter { current.season == 0 || it.season != 0 }
        val ordered = groups.flatMap { it.episodes }
        val index = ordered.indexOfFirst { it.id == videoId }
        if (index < 0) return null
        return ordered.getOrNull(index + 1)?.takeIf { !isUpcoming(it, nowMs) }
    }

    /**
     * Initial focus in the picker: the stream from the same addon and `bingeGroup` as the last
     * one played for this title, else the first playable stream (PRD §7). Returns an index into
     * [groups] flattened in order, or -1 when nothing is playable.
     */
    fun preferredStreamIndex(
        groups: List<Pair<String, List<ClassifiedStream>>>,
        lastAddonId: String?,
        lastBingeGroup: String?,
    ): Int {
        val flat = groups.flatMap { (addonId, rows) -> rows.map { addonId to it } }
        if (lastAddonId != null && lastBingeGroup != null) {
            flat.indexOfFirst { (addonId, row) -> row.isSelectable && addonId == lastAddonId && row.bingeGroup == lastBingeGroup }
                .takeIf { it >= 0 }?.let { return it }
        }
        return flat.indexOfFirst { it.second.isSelectable }
    }
}
