package tss.t.tsiptv.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.core.stremio.StoredAddon

/**
 * `stremio_addons` (Room v5, PRD F2 "Data model"). [transportUrlEnc] holds the manifest URL
 * encrypted with `SecretCipher`; the plain URL is never stored.
 */
@Entity(tableName = "stremio_addons")
data class StremioAddonEntity(
    @PrimaryKey val addonId: String,
    val transportUrlEnc: String,
    val transportHost: String,
    val manifestJson: String,
    val name: String,
    val version: String,
    val logoUrl: String?,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isP2p: Boolean = false,
    /** Set by F3 for addons that come from a TS IPTV Source include; NULL = added by the user. */
    val ownerSourceId: String?,
    val addedAt: Long,
    val lastFetchedAt: Long,
    @ColumnInfo(defaultValue = "0") val failCount: Int = 0,
    /** Short error code, never a URL. */
    val lastError: String?,
    @ColumnInfo(defaultValue = "0") val blocked: Boolean = false,
)

/** `media_history` (Room v5, shared with F3). No foreign key: addon content is not a playlist. */
@Entity(
    tableName = "media_history",
    indices = [
        Index(value = ["sourceKind", "sourceId", "itemId", "videoId"], unique = true),
        Index(value = ["updatedAt"]),
    ],
)
data class MediaHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    @ColumnInfo(defaultValue = "0") val positionMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val finished: Boolean = false,
    val updatedAt: Long,
)

fun StremioAddonEntity.toStoredAddon() = StoredAddon(
    addonId = addonId, transportUrlEnc = transportUrlEnc, transportHost = transportHost, manifestJson = manifestJson,
    name = name, version = version, logoUrl = logoUrl, enabled = enabled, sortOrder = sortOrder, isAdult = isAdult,
    isP2p = isP2p, ownerSourceId = ownerSourceId, addedAt = addedAt, lastFetchedAt = lastFetchedAt,
    failCount = failCount, lastError = lastError, blocked = blocked,
)

fun StoredAddon.toEntity() = StremioAddonEntity(
    addonId = addonId, transportUrlEnc = transportUrlEnc, transportHost = transportHost, manifestJson = manifestJson,
    name = name, version = version, logoUrl = logoUrl, enabled = enabled, sortOrder = sortOrder, isAdult = isAdult,
    isP2p = isP2p, ownerSourceId = ownerSourceId, addedAt = addedAt, lastFetchedAt = lastFetchedAt,
    failCount = failCount, lastError = lastError, blocked = blocked,
)

fun MediaHistoryEntity.toRecord() = MediaHistoryRecord(
    id = id, sourceKind = sourceKind, sourceId = sourceId, itemType = itemType, itemId = itemId, videoId = videoId,
    title = title, subtitle = subtitle, posterUrl = posterUrl, season = season, episode = episode,
    lastAddonId = lastAddonId, lastBingeGroup = lastBingeGroup, positionMs = positionMs, durationMs = durationMs,
    finished = finished, updatedAt = updatedAt,
)

fun MediaHistoryRecord.toEntity() = MediaHistoryEntity(
    id = id, sourceKind = sourceKind, sourceId = sourceId, itemType = itemType, itemId = itemId, videoId = videoId,
    title = title, subtitle = subtitle, posterUrl = posterUrl, season = season, episode = episode,
    lastAddonId = lastAddonId, lastBingeGroup = lastBingeGroup, positionMs = positionMs, durationMs = durationMs,
    finished = finished, updatedAt = updatedAt,
)
