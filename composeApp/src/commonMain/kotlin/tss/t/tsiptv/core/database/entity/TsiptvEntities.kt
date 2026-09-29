package tss.t.tsiptv.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import tss.t.tsiptv.core.tsiptv.TsiptvEpisodeRecord
import tss.t.tsiptv.core.tsiptv.TsiptvIncludeRecord
import tss.t.tsiptv.core.tsiptv.TsiptvSourceRecord
import tss.t.tsiptv.core.tsiptv.TsiptvVodItemRecord

/*
 * F3 (Room v6): TS IPTV Source tables (PRD "Data model"). Every table hangs off `playlists` with
 * ON DELETE CASCADE, so deleting the playlist removes the source, its includes and its VOD rows.
 * URLs stored here are never logged; `lastError` holds a code only.
 */

/** `tsiptv_sources`: one row per imported source. */
@Entity(
    tableName = "tsiptv_sources",
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["sourceId"], unique = true)],
)
data class TsiptvSourceEntity(
    @PrimaryKey val playlistId: String,
    val sourceId: String,
    @ColumnInfo(defaultValue = "0") val revision: Int = 0,
    /** NULL for file imports. */
    val rootUrl: String?,
    val metaJson: String,
    val appearanceJson: String?,
    /** NULL = default layout (spec §7.5). */
    val layoutJson: String?,
    val etag: String?,
    val lastModified: String?,
    val fetchedAt: Long,
    /** Last validation report (at most 100 entries). */
    val reportJson: String?,
    @ColumnInfo(defaultValue = "0") val adultConfirmed: Boolean = false,
    /**
     * The validated root document (JSON), so a `304 Not Modified` on the root reuses it without a
     * re-parse, and a file-imported source can still refresh its includes.
     */
    val rootDocumentJson: String? = null,
    /** Skipped-item count of the last import/refresh (About). */
    @ColumnInfo(defaultValue = "0") val skippedCount: Int = 0,
    /** A refresh found the root newly adult; its new content is withheld until the user confirms. */
    @ColumnInfo(defaultValue = "0") val adultPending: Boolean = false,
    /** Last root refresh that failed (short code, never a URL) and when; null after a good one. */
    val lastErrorCode: String? = null,
    val lastErrorAt: Long? = null,
)

/** `tsiptv_includes`: one row per include (and per EPG link), keyed by its chained include path. */
@Entity(
    tableName = "tsiptv_includes",
    primaryKeys = ["playlistId", "includePath"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId")],
)
data class TsiptvIncludeEntity(
    val playlistId: String,
    val includePath: String,
    val type: String,
    val url: String,
    val refreshHours: Int,
    val etag: String?,
    val lastModified: String?,
    val lastSuccessAt: Long?,
    val lastAttemptAt: Long?,
    /** `OK`, `STALE`, `FAILED`. */
    val status: String,
    /** Short code, never a URL. */
    val lastError: String?,
    /** Localized include name (JSON), for "About this source". */
    val nameJson: String? = null,
    /** `tsiptv-source` includes: the parsed document (JSON), reused on `304 Not Modified`. */
    val documentJson: String? = null,
    /** The include's content is adult and withheld until the user confirms (spec §9.3). */
    @ColumnInfo(defaultValue = "0") val adultWithheld: Boolean = false,
)

/** `vod_items`: movies and series. */
@Entity(
    tableName = "vod_items",
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["playlistId", "kind", "sortIndex"])],
)
data class VodItemEntity(
    /** `{playlistId}|{itemId}`. */
    @PrimaryKey val rowId: String,
    val playlistId: String,
    /** Namespaced id (`cinema:big-buck-bunny`). */
    val itemId: String,
    /** NULL = root document. */
    val originIncludePath: String?,
    /** `MOVIE`, `SERIES`. */
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
    /** Movies only: streams with the item's header/DRM/MIME defaults already merged. */
    val streamsJson: String?,
    val subtitlesJson: String?,
)

/** `vod_episodes`. */
@Entity(
    tableName = "vod_episodes",
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["playlistId", "seriesItemId", "seasonNumber", "episodeNumber"])],
)
data class VodEpisodeEntity(
    /** `{playlistId}|{episodeId}`. */
    @PrimaryKey val rowId: String,
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
)

fun TsiptvSourceEntity.toRecord() = TsiptvSourceRecord(
    playlistId = playlistId, sourceId = sourceId, revision = revision, rootUrl = rootUrl, metaJson = metaJson,
    appearanceJson = appearanceJson, layoutJson = layoutJson, etag = etag, lastModified = lastModified,
    fetchedAt = fetchedAt, reportJson = reportJson, adultConfirmed = adultConfirmed, rootDocumentJson = rootDocumentJson,
    skippedCount = skippedCount, adultPending = adultPending,
    lastErrorCode = lastErrorCode, lastErrorAt = lastErrorAt,
)

fun TsiptvSourceRecord.toEntity() = TsiptvSourceEntity(
    playlistId = playlistId, sourceId = sourceId, revision = revision, rootUrl = rootUrl, metaJson = metaJson,
    appearanceJson = appearanceJson, layoutJson = layoutJson, etag = etag, lastModified = lastModified,
    fetchedAt = fetchedAt, reportJson = reportJson, adultConfirmed = adultConfirmed, rootDocumentJson = rootDocumentJson,
    skippedCount = skippedCount, adultPending = adultPending,
    lastErrorCode = lastErrorCode, lastErrorAt = lastErrorAt,
)

fun TsiptvIncludeEntity.toRecord() = TsiptvIncludeRecord(
    playlistId = playlistId, includePath = includePath, type = type, url = url, refreshHours = refreshHours,
    etag = etag, lastModified = lastModified, lastSuccessAt = lastSuccessAt, lastAttemptAt = lastAttemptAt,
    status = status, lastError = lastError, nameJson = nameJson, documentJson = documentJson, adultWithheld = adultWithheld,
)

fun TsiptvIncludeRecord.toEntity() = TsiptvIncludeEntity(
    playlistId = playlistId, includePath = includePath, type = type, url = url, refreshHours = refreshHours,
    etag = etag, lastModified = lastModified, lastSuccessAt = lastSuccessAt, lastAttemptAt = lastAttemptAt,
    status = status, lastError = lastError, nameJson = nameJson, documentJson = documentJson, adultWithheld = adultWithheld,
)

fun VodItemEntity.toRecord() = TsiptvVodItemRecord(
    rowId = rowId, playlistId = playlistId, itemId = itemId, originIncludePath = originIncludePath, kind = kind,
    sortIndex = sortIndex, nameJson = nameJson, descriptionJson = descriptionJson, originalName = originalName,
    posterUrl = posterUrl, backdropUrl = backdropUrl, logoUrl = logoUrl, ageRating = ageRating, releaseDate = releaseDate,
    year = year, endYear = endYear, runtimeMinutes = runtimeMinutes, genresJson = genresJson, castJson = castJson,
    directorsJson = directorsJson, countriesJson = countriesJson, languagesJson = languagesJson, tagsJson = tagsJson,
    streamsJson = streamsJson, subtitlesJson = subtitlesJson,
)

fun TsiptvVodItemRecord.toEntity() = VodItemEntity(
    rowId = rowId, playlistId = playlistId, itemId = itemId, originIncludePath = originIncludePath, kind = kind,
    sortIndex = sortIndex, nameJson = nameJson, descriptionJson = descriptionJson, originalName = originalName,
    posterUrl = posterUrl, backdropUrl = backdropUrl, logoUrl = logoUrl, ageRating = ageRating, releaseDate = releaseDate,
    year = year, endYear = endYear, runtimeMinutes = runtimeMinutes, genresJson = genresJson, castJson = castJson,
    directorsJson = directorsJson, countriesJson = countriesJson, languagesJson = languagesJson, tagsJson = tagsJson,
    streamsJson = streamsJson, subtitlesJson = subtitlesJson,
)

fun VodEpisodeEntity.toRecord() = TsiptvEpisodeRecord(
    rowId = rowId, playlistId = playlistId, seriesItemId = seriesItemId, episodeId = episodeId,
    seasonNumber = seasonNumber, seasonNameJson = seasonNameJson, seasonPosterUrl = seasonPosterUrl,
    seasonDescriptionJson = seasonDescriptionJson, episodeNumber = episodeNumber, nameJson = nameJson,
    descriptionJson = descriptionJson, thumbnailUrl = thumbnailUrl, releaseDate = releaseDate,
    runtimeMinutes = runtimeMinutes, streamsJson = streamsJson, subtitlesJson = subtitlesJson,
)

fun TsiptvEpisodeRecord.toEntity() = VodEpisodeEntity(
    rowId = rowId, playlistId = playlistId, seriesItemId = seriesItemId, episodeId = episodeId,
    seasonNumber = seasonNumber, seasonNameJson = seasonNameJson, seasonPosterUrl = seasonPosterUrl,
    seasonDescriptionJson = seasonDescriptionJson, episodeNumber = episodeNumber, nameJson = nameJson,
    descriptionJson = descriptionJson, thumbnailUrl = thumbnailUrl, releaseDate = releaseDate,
    runtimeMinutes = runtimeMinutes, streamsJson = streamsJson, subtitlesJson = subtitlesJson,
)
