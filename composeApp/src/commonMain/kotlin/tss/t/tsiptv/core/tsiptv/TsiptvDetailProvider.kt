package tss.t.tsiptv.core.tsiptv

import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.parser.tsiptv.TsiptvStream
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSubtitle
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSubtitleFormat
import tss.t.tsiptv.core.stremio.StreamBehaviorHints
import tss.t.tsiptv.core.stremio.StremioMeta
import tss.t.tsiptv.core.stremio.StremioStream
import tss.t.tsiptv.core.stremio.StremioType
import tss.t.tsiptv.core.stremio.StremioVideo
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.SubtitleTrack

/**
 * A TS IPTV Source movie or series as F2's detail page needs it (PRD F3 §4 "Detail pages"): a
 * [StremioMeta] whose videos carry their streams inline, so the picker never asks an addon, plus
 * the original streams so playback keeps their DRM, MIME hint and subtitles.
 */
class TsiptvDetail(
    val playlistId: String,
    val meta: StremioMeta,
    /** Video id → the source's streams, in picker order. */
    val streamsByVideo: Map<String, List<TsiptvStream>>,
    val subtitlesByVideo: Map<String, List<TsiptvSubtitle>>,
) {
    /** The source stream behind a picker row (same index as in the video's inline streams). */
    fun streamFor(videoId: String, index: Int): TsiptvStream? = streamsByVideo[videoId]?.getOrNull(index)

    /**
     * The player item for [stream] of [videoId]: merged headers, DRM, MIME hint and side-loaded
     * subtitles. Its id (`tsvod:{playlistId}:{videoId}`) is not a channel.
     */
    fun mediaItem(videoId: String, stream: TsiptvStream, title: String, subtitle: String, artwork: String?, uiLanguage: String?) = MediaItem(
        id = "$MEDIA_ID_PREFIX$playlistId:$videoId",
        uri = stream.url,
        title = title,
        description = subtitle,
        artist = subtitle,
        artworkUri = artwork,
        mimeType = stream.mimeType,
        headers = stream.headers,
        drm = stream.drm,
        subtitles = subtitlesByVideo[videoId].orEmpty().map { sub ->
            SubtitleTrack(
                url = sub.url,
                language = sub.language,
                // No label: the language name in the UI language (spec §8.7), not the raw code.
                label = sub.label?.resolve(uiLanguage)?.takeIf { it.isNotBlank() }
                    ?: tss.t.tsiptv.core.language.languageDisplayName(sub.language, uiLanguage) ?: sub.language,
                mimeType = if (sub.format == TsiptvSubtitleFormat.SRT) SubtitleTrack.MIME_SRT else SubtitleTrack.MIME_VTT,
            )
        },
    )

    override fun toString(): String = "TsiptvDetail(itemId=${meta.id})"

    companion object {
        /** Same as `ui.screens.player.SOURCE_VOD_MEDIA_ID_PREFIX`. */
        const val MEDIA_ID_PREFIX = "tsvod:"
    }
}

/** Loads [TsiptvDetail]s from the stored source. */
class TsiptvDetailProvider(private val database: IPTVDatabase) {
    /**
     * @param streamLabel Picker label of a stream without a name ("Source N", 1-based)
     */
    suspend fun load(
        playlistId: String,
        itemId: String,
        uiLanguage: String?,
        streamLabel: (Int) -> String,
    ): TsiptvDetail? {
        val store = database.tsiptvStore
        val record = store.getVodItem(playlistId, itemId) ?: return null
        val item = TsiptvVodMapping.item(record)
        val streamsByVideo = LinkedHashMap<String, List<TsiptvStream>>()
        val subtitlesByVideo = LinkedHashMap<String, List<TsiptvSubtitle>>()

        fun inline(videoId: String, streams: List<TsiptvStream>, subtitles: List<TsiptvSubtitle>): List<StremioStream> {
            streamsByVideo[videoId] = streams
            subtitlesByVideo[videoId] = subtitles
            return streams.mapIndexed { index, stream ->
                StremioStream(
                    url = stream.url,
                    name = stream.name?.resolve(uiLanguage) ?: streamLabel(index + 1),
                    description = listOfNotNull(stream.quality, stream.language?.uppercase()).joinToString(" · ").ifBlank { null },
                    behaviorHints = StreamBehaviorHints(requestHeaders = stream.headers),
                )
            }
        }

        val videos: List<StremioVideo> = if (item.isSeries) {
            store.getEpisodes(playlistId, itemId).map(TsiptvVodMapping::episode).map { episode ->
                StremioVideo(
                    id = episode.episodeId,
                    title = episode.name.resolve(uiLanguage),
                    released = episode.releaseDate,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    thumbnail = episode.thumbnail,
                    overview = episode.description?.resolve(uiLanguage),
                    streams = inline(episode.episodeId, episode.streams, episode.subtitles),
                )
            }
        } else {
            // One video whose id is the movie id: not series-like, a single Play button.
            listOf(
                StremioVideo(
                    id = itemId,
                    title = item.name.resolve(uiLanguage),
                    released = item.releaseDate,
                    thumbnail = item.backdrop ?: item.poster,
                    streams = inline(itemId, item.streams, item.subtitles),
                )
            )
        }
        val year = when {
            item.year != null && item.endYear != null && item.endYear != item.year -> "${item.year}–${item.endYear}"
            item.year != null && item.isSeries -> "${item.year}–"
            else -> item.year?.toString()
        }
        val meta = StremioMeta(
            id = itemId,
            type = if (item.isSeries) StremioType.SERIES else StremioType.MOVIE,
            name = item.name.resolve(uiLanguage),
            poster = item.poster,
            background = item.backdrop,
            logo = item.logo,
            description = item.description?.resolve(uiLanguage),
            releaseInfo = year,
            released = item.releaseDate,
            year = item.year?.toString(),
            runtime = item.runtimeMinutes?.let { "$it min" },
            genres = item.genres.takeIf { it.isNotEmpty() },
            director = item.directors.takeIf { it.isNotEmpty() },
            cast = item.cast.takeIf { it.isNotEmpty() },
            videos = videos,
        )
        return TsiptvDetail(playlistId, meta, streamsByVideo, subtitlesByVideo)
    }
}
