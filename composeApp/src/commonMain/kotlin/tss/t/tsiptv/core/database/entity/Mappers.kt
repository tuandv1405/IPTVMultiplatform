package tss.t.tsiptv.core.database.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import kotlin.time.ExperimentalTime


/**
 * Lenient on read: a row written by a newer build (or damaged) must still load as a channel,
 * just without the field that failed.
 */
private val entityJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private inline fun <reified T> String?.decodeOrNull(): T? =
    this?.let { runCatching { entityJson.decodeFromString<T>(it) }.getOrNull() }

fun PlaylistEntity.toPlaylist(): Playlist {
    val urls = epgUrlsJson.decodeOrNull<List<String>>()
        ?: listOfNotNull(epgUrl?.takeIf { it.isNotBlank() })
    return Playlist(
        id = id,
        name = name,
        url = url,
        lastUpdated = lastUpdated,
        epgUrl = epgUrl?.takeIf { it.isNotBlank() } ?: urls.firstOrNull(),
        sourceType = PlaylistSourceType.fromStored(sourceType),
        epgUrls = urls,
        format = format,
    )
}

fun Playlist.toPlaylistEntity(): PlaylistEntity {
    return PlaylistEntity(
        id = id,
        name = name,
        url = url,
        lastUpdated = lastUpdated,
        format = format,
        epgUrl = epgUrl ?: epgUrls.firstOrNull() ?: "",
        sourceType = sourceType.name,
        epgUrlsJson = epgUrls.takeIf { it.isNotEmpty() }?.let { entityJson.encodeToString(it) },
    )
}

fun ChannelEntity.toChannel(): Channel {
    return Channel(
        id = id,
        name = name,
        url = url,
        logoUrl = logoUrl,
        categoryId = categoryId,
        playlistId = playlistId,
        isFavorite = isFavorite,
        lastWatched = lastWatched,
        number = channelNumber,
        groups = groupsJson.decodeOrNull<List<String>>() ?: listOfNotNull(categoryId),
        isRadio = isRadio,
        isVod = isVod,
        headers = headersJson.decodeOrNull<Map<String, String>>().orEmpty(),
        mimeType = mimeType,
        drm = drmJson.decodeOrNull<DrmSpec>(),
        catchup = catchupJson.decodeOrNull<CatchupSpec>(),
        epgShiftHours = epgShiftHours,
        sortIndex = sortIndex,
        epgId = epgId,
        nameJson = nameJson,
        originIncludePath = originIncludePath,
        tagsJson = tagsJson,
        descriptionJson = descriptionJson,
        streamsJson = streamsJson,
    )
}

fun Channel.toChannelEntity(): ChannelEntity {
    return ChannelEntity(
        id = id,
        name = name,
        url = url,
        logoUrl = logoUrl,
        categoryId = categoryId,
        playlistId = playlistId,
        isFavorite = isFavorite,
        lastWatched = lastWatched,
        channelNumber = number,
        groupsJson = groups.takeIf { it.isNotEmpty() }?.let { entityJson.encodeToString(it) },
        isRadio = isRadio,
        isVod = isVod,
        headersJson = headers.takeIf { it.isNotEmpty() }?.let { entityJson.encodeToString(it) },
        mimeType = mimeType,
        drmJson = drm?.let { entityJson.encodeToString(it) },
        catchupJson = catchup?.let { entityJson.encodeToString(it) },
        epgShiftHours = epgShiftHours,
        sortIndex = sortIndex,
        epgId = epgId,
        nameJson = nameJson,
        originIncludePath = originIncludePath,
        tagsJson = tagsJson,
        descriptionJson = descriptionJson,
        streamsJson = streamsJson,
    )
}

fun CategoryEntity.toCategory(): Category {
    return Category(
        id = id,
        name = name,
        playlistId = playlistId
    )
}

fun Category.toCategoryEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        playlistId = playlistId
    )
}

@OptIn(ExperimentalTime::class)
fun ProgramEntity.toIPTVProgram(): IPTVProgram {
    val creditsObj = credits?.let {
        try {
            Json.decodeFromString<IPTVProgram.Credits>(it)
        } catch (e: Exception) {
            null
        }
    }
    val attributesMap = attributes?.let {
        try {
            Json.decodeFromString<Map<String, String>>(it)
        } catch (e: Exception) {
            emptyMap()
        }
    } ?: emptyMap()
    val cateList = category?.let {
        try {
            Json.decodeFromString<List<String>>(it)
        } catch (_: Exception) {
            null
        }
    }

    return IPTVProgram(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = cateList,
        logo = logo,
        credits = creditsObj,
        attributes = attributesMap
    ).apply {
        startTimeStr = format.format(
            Instant.fromEpochMilliseconds(startTime)
                .toLocalDateTime(TimeZone.currentSystemDefault())
        )
        endTimeStr = format.format(
            Instant.fromEpochMilliseconds(endTime)
                .toLocalDateTime(TimeZone.currentSystemDefault())
        )
    }
}

private val format = LocalDateTime.Format {
    hour();chars(":");minute()
}

/**
 * The programme moved by a channel's `tvg-shift`, with its display times recomputed. The
 * guide stores XMLTV times as published; the shift is applied where a programme is matched to
 * "now" and shown, so one guide can serve channels with different shifts.
 */
@OptIn(ExperimentalTime::class)
fun IPTVProgram.shiftedBy(shiftMs: Long): IPTVProgram {
    if (shiftMs == 0L) return this
    return copy(startTime = startTime + shiftMs, endTime = endTime + shiftMs).apply {
        startTimeStr = format.format(
            Instant.fromEpochMilliseconds(startTime).toLocalDateTime(TimeZone.currentSystemDefault())
        )
        endTimeStr = format.format(
            Instant.fromEpochMilliseconds(endTime).toLocalDateTime(TimeZone.currentSystemDefault())
        )
    }
}

fun IPTVProgram.toProgramEntity(playlistId: String): ProgramEntity {
    val creditsJson = credits?.let { Json.encodeToString(it) }
    val attributesJson = if (attributes.isNotEmpty()) Json.encodeToString(attributes) else null
    val cateList = Json.encodeToString(category)
    return ProgramEntity(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = cateList,
        playlistId = playlistId,
        logo = logo,
        credits = creditsJson,
        attributes = attributesJson
    )
}
