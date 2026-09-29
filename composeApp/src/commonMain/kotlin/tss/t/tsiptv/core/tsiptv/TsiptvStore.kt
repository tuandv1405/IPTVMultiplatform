package tss.t.tsiptv.core.tsiptv

import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist

/** A row of `tsiptv_sources` (PRD "Data model"). */
data class TsiptvSourceRecord(
    val playlistId: String,
    val sourceId: String,
    val revision: Int,
    val rootUrl: String?,
    val metaJson: String,
    val appearanceJson: String?,
    val layoutJson: String?,
    val etag: String?,
    val lastModified: String?,
    val fetchedAt: Long,
    val reportJson: String?,
    val adultConfirmed: Boolean,
    val rootDocumentJson: String?,
    val skippedCount: Int = 0,
    /** A refresh found the root newly adult; new content is withheld until the user confirms. */
    val adultPending: Boolean = false,
    /** The last root refresh failed with this short code (never a URL); null after a good refresh. */
    val lastErrorCode: String? = null,
    val lastErrorAt: Long? = null,
) {
    /** Something is withheld for 18+ confirmation (the root itself or an include). */
    fun needsAdultConfirmation(includes: List<TsiptvIncludeRecord>): Boolean =
        !adultConfirmed && (adultPending || includes.any { it.adultWithheld })

    /** Never the URL (it can carry a token). */
    override fun toString(): String = "TsiptvSourceRecord(playlistId=$playlistId, sourceId=$sourceId, revision=$revision)"
}

/** Include status in "About this source" (PRD §5). */
enum class TsiptvIncludeStatus { OK, STALE, FAILED }

/** A row of `tsiptv_includes`: an include or an EPG link of any document in the tree. */
data class TsiptvIncludeRecord(
    val playlistId: String,
    /** Chained include id (`cinema`, `cinema:partner`, `epg-0`, `cinema:epg-1`). */
    val includePath: String,
    /** [tss.t.tsiptv.core.parser.tsiptv.TsiptvIncludeType.wireName]. */
    val type: String,
    val url: String,
    val refreshHours: Int,
    val etag: String?,
    val lastModified: String?,
    val lastSuccessAt: Long?,
    val lastAttemptAt: Long?,
    val status: String,
    val lastError: String?,
    val nameJson: String? = null,
    val documentJson: String? = null,
    val adultWithheld: Boolean = false,
) {
    override fun toString(): String = "TsiptvIncludeRecord(path=$includePath, type=$type, status=$status)"
}

/** A row of `vod_items`. */
data class TsiptvVodItemRecord(
    val rowId: String,
    val playlistId: String,
    val itemId: String,
    val originIncludePath: String?,
    val kind: String,
    val sortIndex: Int,
    val nameJson: String,
    val descriptionJson: String?,
    val originalName: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val ageRating: String?,
    val releaseDate: String?,
    val year: Int?,
    val endYear: Int?,
    val runtimeMinutes: Int?,
    val genresJson: String?,
    val castJson: String?,
    val directorsJson: String?,
    val countriesJson: String?,
    val languagesJson: String?,
    val tagsJson: String?,
    val streamsJson: String?,
    val subtitlesJson: String?,
) {
    override fun toString(): String = "TsiptvVodItemRecord(itemId=$itemId, kind=$kind)"

    companion object {
        const val KIND_MOVIE = "MOVIE"
        const val KIND_SERIES = "SERIES"
    }
}

/** A row of `vod_episodes`. */
data class TsiptvEpisodeRecord(
    val rowId: String,
    val playlistId: String,
    val seriesItemId: String,
    val episodeId: String,
    val seasonNumber: Int,
    val seasonNameJson: String?,
    val seasonPosterUrl: String?,
    val seasonDescriptionJson: String?,
    val episodeNumber: Int,
    val nameJson: String,
    val descriptionJson: String?,
    val thumbnailUrl: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val streamsJson: String,
    val subtitlesJson: String?,
) {
    override fun toString(): String = "TsiptvEpisodeRecord(episodeId=$episodeId, s=$seasonNumber, e=$episodeNumber)"
}

/**
 * Everything a source import or refresh stores, swapped in **one transaction** (PRD §5
 * "Refresh atomicity"): the playlist row, its categories and channels (carry-over of favourites
 * and history as for any playlist), the source row, its includes and its VOD rows.
 */
class TsiptvStoredContent(
    val playlist: Playlist,
    val categories: List<Category>,
    val channels: List<Channel>,
    val source: TsiptvSourceRecord,
    val includes: List<TsiptvIncludeRecord>,
    val vodItems: List<TsiptvVodItemRecord>,
    val episodes: List<TsiptvEpisodeRecord>,
    /** Guide programmes, per guide (written in the same transaction). */
    val guides: TsiptvGuidePlan = TsiptvGuidePlan(),
)

/**
 * Programme changes of one store, **per guide** (spec §9.3: each include keeps its last good copy).
 * Programme rows of a source are stored with ids prefixed by [TsiptvGuideIds.prefix], so one guide's
 * rows can be replaced or dropped without touching the others.
 *
 * @property replace Guides fetched and parsed now: their rows are replaced by these programmes
 * @property drop Guides no longer in the tree (or duplicates of another guide's URL): rows removed
 * Guides in neither map keep their rows (not due, `304`, failed with a stale copy).
 */
class TsiptvGuidePlan(
    val replace: Map<String, List<tss.t.tsiptv.core.parser.model.IPTVProgram>> = emptyMap(),
    val drop: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = replace.isEmpty() && drop.isEmpty()
    override fun toString(): String = "TsiptvGuidePlan(replace=${replace.mapValues { it.value.size }}, drop=$drop)"
}

object TsiptvGuideIds {
    /** `tsg:{playlistId}#{guidePath}#` — `#` is not in the Id character set. */
    fun prefix(playlistId: String, guidePath: String) = "tsg:$playlistId#$guidePath#"
    fun programId(playlistId: String, guidePath: String, programId: String) = prefix(playlistId, guidePath) + programId
}

/** F3 tables (Room v6), as exposed by `IPTVDatabase.tsiptvStore`. */
interface TsiptvStore {
    fun observeSource(playlistId: String): Flow<TsiptvSourceRecord?>
    fun observeSources(): Flow<List<TsiptvSourceRecord>>
    suspend fun getSource(playlistId: String): TsiptvSourceRecord?
    suspend fun getSourceBySourceId(sourceId: String): TsiptvSourceRecord?
    suspend fun upsertSource(record: TsiptvSourceRecord)

    suspend fun getIncludes(playlistId: String): List<TsiptvIncludeRecord>
    fun observeIncludes(playlistId: String): Flow<List<TsiptvIncludeRecord>>
    suspend fun upsertIncludes(records: List<TsiptvIncludeRecord>)

    fun observeVodItems(playlistId: String): Flow<List<TsiptvVodItemRecord>>
    suspend fun getVodItems(playlistId: String): List<TsiptvVodItemRecord>
    suspend fun getVodItem(playlistId: String, itemId: String): TsiptvVodItemRecord?

    suspend fun getEpisodes(playlistId: String, seriesItemId: String): List<TsiptvEpisodeRecord>
    suspend fun getAllEpisodes(playlistId: String): List<TsiptvEpisodeRecord>
    suspend fun getEpisode(playlistId: String, episodeId: String): TsiptvEpisodeRecord?
    suspend fun countEpisodes(playlistId: String): Int

    /** See [TsiptvStoredContent]. */
    suspend fun replaceSourceContent(content: TsiptvStoredContent)

    companion object {
        fun rowId(playlistId: String, itemId: String) = "$playlistId|$itemId"
    }
}
