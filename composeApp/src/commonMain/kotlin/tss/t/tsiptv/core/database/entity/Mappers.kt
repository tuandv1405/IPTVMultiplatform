package tss.t.tsiptv.core.database.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVProgram


fun PlaylistEntity.toPlaylist(): Playlist {
    return Playlist(
        id = id,
        name = name,
        url = url,
        lastUpdated = lastUpdated
    )
}

fun Playlist.toPlaylistEntity(): PlaylistEntity {
    return PlaylistEntity(
        id = id,
        name = name,
        url = url,
        lastUpdated = lastUpdated,
        format = IPTVFormat.UNKNOWN.name // This will be updated when the playlist is parsed
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
        lastWatched = lastWatched
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
        lastWatched = lastWatched
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

    return IPTVProgram(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
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

fun IPTVProgram.toProgramEntity(playlistId: String): ProgramEntity {
    val creditsJson = credits?.let { Json.encodeToString(it) }
    val attributesJson = if (attributes.isNotEmpty()) Json.encodeToString(attributes) else null

    return ProgramEntity(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        playlistId = playlistId,
        logo = logo,
        credits = creditsJson,
        attributes = attributesJson
    )
}
