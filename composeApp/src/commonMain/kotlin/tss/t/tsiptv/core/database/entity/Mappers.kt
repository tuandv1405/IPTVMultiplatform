package tss.t.tsiptv.core.database.entity

import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.Program
import tss.t.tsiptv.core.parser.IPTVFormat


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

fun ProgramEntity.toProgram(): Program {
    return Program(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        playlistId = playlistId
    )
}

fun Program.toProgramEntity(): ProgramEntity {
    return ProgramEntity(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        playlistId = playlistId
    )
}
