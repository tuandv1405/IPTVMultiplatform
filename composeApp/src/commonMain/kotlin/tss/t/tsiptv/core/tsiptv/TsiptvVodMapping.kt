package tss.t.tsiptv.core.tsiptv

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import tss.t.tsiptv.core.parser.tsiptv.LocalizedText
import tss.t.tsiptv.core.parser.tsiptv.TsiptvEpisode
import tss.t.tsiptv.core.parser.tsiptv.TsiptvMovie
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSeason
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSeries
import tss.t.tsiptv.core.parser.tsiptv.TsiptvStream
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSubtitle

/** A stored movie or series, decoded for display (names still localized). */
data class TsiptvVodItem(
    val playlistId: String,
    /** Namespaced pool id. */
    val itemId: String,
    val isSeries: Boolean,
    val originIncludePath: String?,
    val sortIndex: Int,
    val name: LocalizedText,
    val description: LocalizedText?,
    val originalName: String?,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val ageRating: String?,
    val releaseDate: String?,
    val year: Int?,
    val endYear: Int?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val cast: List<String>,
    val directors: List<String>,
    val tags: List<String>,
    val streams: List<TsiptvStream>,
    val subtitles: List<TsiptvSubtitle>,
) {
    override fun toString(): String = "TsiptvVodItem(itemId=$itemId, series=$isSeries)"
}

/** A stored episode, decoded. */
data class TsiptvVodEpisode(
    val playlistId: String,
    val seriesItemId: String,
    val episodeId: String,
    val seasonNumber: Int,
    val seasonName: LocalizedText?,
    val seasonPoster: String?,
    val episodeNumber: Int,
    val name: LocalizedText,
    val description: LocalizedText?,
    val thumbnail: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val streams: List<TsiptvStream>,
    val subtitles: List<TsiptvSubtitle>,
) {
    override fun toString(): String = "TsiptvVodEpisode(episodeId=$episodeId, s=$seasonNumber, e=$episodeNumber)"
}

/** Model objects ↔ `vod_items` / `vod_episodes` rows. */
object TsiptvVodMapping {
    private val json = TsiptvStorageJson
    private val strings = ListSerializer(String.serializer())
    private val streamList = ListSerializer(TsiptvStream.serializer())
    private val subtitleList = ListSerializer(TsiptvSubtitle.serializer())

    private fun text(value: LocalizedText?): String? = value?.let { json.encodeToString(LocalizedText.serializer(), it) }
    private fun list(values: List<String>): String? = values.takeIf { it.isNotEmpty() }?.let { json.encodeToString(strings, it) }

    fun movieRecord(playlistId: String, itemId: String, origin: String?, sortIndex: Int, movie: TsiptvMovie) = TsiptvVodItemRecord(
        rowId = TsiptvStore.rowId(playlistId, itemId),
        playlistId = playlistId,
        itemId = itemId,
        originIncludePath = origin,
        kind = TsiptvVodItemRecord.KIND_MOVIE,
        sortIndex = sortIndex,
        nameJson = text(movie.name)!!,
        descriptionJson = text(movie.description),
        originalName = movie.originalName,
        posterUrl = movie.poster,
        backdropUrl = movie.backdrop,
        logoUrl = movie.logo,
        ageRating = movie.ageRating,
        releaseDate = movie.releaseDate,
        year = movie.year,
        endYear = null,
        runtimeMinutes = movie.runtimeMinutes,
        genresJson = list(movie.genres),
        castJson = list(movie.cast),
        directorsJson = list(movie.directors),
        countriesJson = list(movie.countries),
        languagesJson = list(movie.languages),
        tagsJson = list(movie.tags),
        streamsJson = json.encodeToString(streamList, movie.streams),
        subtitlesJson = movie.subtitles.takeIf { it.isNotEmpty() }?.let { json.encodeToString(subtitleList, it) },
    )

    fun seriesRecord(playlistId: String, itemId: String, origin: String?, sortIndex: Int, series: TsiptvSeries) = TsiptvVodItemRecord(
        rowId = TsiptvStore.rowId(playlistId, itemId),
        playlistId = playlistId,
        itemId = itemId,
        originIncludePath = origin,
        kind = TsiptvVodItemRecord.KIND_SERIES,
        sortIndex = sortIndex,
        nameJson = text(series.name)!!,
        descriptionJson = text(series.description),
        originalName = series.originalName,
        posterUrl = series.poster,
        backdropUrl = series.backdrop,
        logoUrl = series.logo,
        ageRating = series.ageRating,
        releaseDate = null,
        year = series.year,
        endYear = series.endYear,
        runtimeMinutes = null,
        genresJson = list(series.genres),
        castJson = list(series.cast),
        directorsJson = null,
        countriesJson = list(series.countries),
        languagesJson = list(series.languages),
        tagsJson = list(series.tags),
        streamsJson = null,
        subtitlesJson = null,
    )

    fun episodeRecord(
        playlistId: String,
        seriesItemId: String,
        episodeId: String,
        season: TsiptvSeason,
        episode: TsiptvEpisode,
    ) = TsiptvEpisodeRecord(
        rowId = TsiptvStore.rowId(playlistId, episodeId),
        playlistId = playlistId,
        seriesItemId = seriesItemId,
        episodeId = episodeId,
        seasonNumber = season.number,
        seasonNameJson = text(season.name),
        seasonPosterUrl = season.poster,
        seasonDescriptionJson = text(season.description),
        episodeNumber = episode.number,
        nameJson = text(episode.name)!!,
        descriptionJson = text(episode.description),
        thumbnailUrl = episode.thumbnail,
        releaseDate = episode.releaseDate,
        runtimeMinutes = episode.runtimeMinutes,
        streamsJson = json.encodeToString(streamList, episode.streams),
        subtitlesJson = episode.subtitles.takeIf { it.isNotEmpty() }?.let { json.encodeToString(subtitleList, it) },
    )

    fun decodeText(value: String?): LocalizedText? = value?.let {
        runCatching { json.decodeFromString(LocalizedText.serializer(), it) }.getOrNull()
    }

    private fun decodeList(value: String?): List<String> = value?.let {
        runCatching { json.decodeFromString(strings, it) }.getOrNull()
    }.orEmpty()

    fun decodeStreams(value: String?): List<TsiptvStream> = value?.let {
        runCatching { json.decodeFromString(streamList, it) }.getOrNull()
    }.orEmpty()

    fun decodeSubtitles(value: String?): List<TsiptvSubtitle> = value?.let {
        runCatching { json.decodeFromString(subtitleList, it) }.getOrNull()
    }.orEmpty()

    /** @param withStreams false for cards (streams and subtitles are decoded only for the detail page) */
    fun item(record: TsiptvVodItemRecord, withStreams: Boolean = true) = TsiptvVodItem(
        playlistId = record.playlistId,
        itemId = record.itemId,
        isSeries = record.kind == TsiptvVodItemRecord.KIND_SERIES,
        originIncludePath = record.originIncludePath,
        sortIndex = record.sortIndex,
        name = decodeText(record.nameJson) ?: LocalizedText.plain(record.itemId),
        description = decodeText(record.descriptionJson),
        originalName = record.originalName,
        poster = record.posterUrl,
        backdrop = record.backdropUrl,
        logo = record.logoUrl,
        ageRating = record.ageRating,
        releaseDate = record.releaseDate,
        year = record.year,
        endYear = record.endYear,
        runtimeMinutes = record.runtimeMinutes,
        genres = decodeList(record.genresJson),
        cast = decodeList(record.castJson),
        directors = decodeList(record.directorsJson),
        tags = decodeList(record.tagsJson),
        streams = if (withStreams) decodeStreams(record.streamsJson) else emptyList(),
        subtitles = if (withStreams) decodeSubtitles(record.subtitlesJson) else emptyList(),
    )

    fun episode(record: TsiptvEpisodeRecord) = TsiptvVodEpisode(
        playlistId = record.playlistId,
        seriesItemId = record.seriesItemId,
        episodeId = record.episodeId,
        seasonNumber = record.seasonNumber,
        seasonName = decodeText(record.seasonNameJson),
        seasonPoster = record.seasonPosterUrl,
        episodeNumber = record.episodeNumber,
        name = decodeText(record.nameJson) ?: LocalizedText.plain(record.episodeId),
        description = decodeText(record.descriptionJson),
        thumbnail = record.thumbnailUrl,
        releaseDate = record.releaseDate,
        runtimeMinutes = record.runtimeMinutes,
        streams = decodeStreams(record.streamsJson),
        subtitles = decodeSubtitles(record.subtitlesJson),
    )
}
