package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSpec

/*
 * Typed tree of a validated TS IPTV Source document (docs/tsiptv-source-format.md).
 *
 * Everything here has already passed validation: shorthands are expanded (`url` → one stream;
 * item-level `headers`, `mimeType` and `drm` are merged into every stream), defaults are filled
 * in, invalid optional fields are absent, and unknown enum values have been replaced by their
 * §11 fallback. Headers, DRM and catch-up reuse the F1 playback types so that a channel from a
 * source and the same channel from an M3U play identically (§1, principle 5).
 *
 * All classes are @Serializable so that step 3 can store meta, appearance, layout and streams as JSON.
 */

/** A validated document. Ids are the document's own (not namespaced); see [TsiptvIds.namespaced]. */
@Serializable
data class TsiptvSourceDocument(
    val id: String,
    val version: Int,
    val revision: Int = 0,
    val meta: TsiptvMeta,
    /** null: the defaults of §6. */
    val appearance: TsiptvAppearance? = null,
    /** null: the default layout of §7.5 (layout absent, invalid, or every section skipped). */
    val layout: TsiptvLayout? = null,
    val channels: List<TsiptvChannel> = emptyList(),
    val movies: List<TsiptvMovie> = emptyList(),
    val series: List<TsiptvSeries> = emptyList(),
    val epg: List<TsiptvEpgLink> = emptyList(),
    val includes: List<TsiptvInclude> = emptyList(),
) {
    val tvChannels: List<TsiptvChannel> get() = channels.filter { it.type == TsiptvChannelType.TV }
    val radioChannels: List<TsiptvChannel> get() = channels.filter { it.type == TsiptvChannelType.RADIO }
    val episodeCount: Int get() = series.sumOf { s -> s.seasons.sumOf { it.episodes.size } }

    /**
     * §9.1: every `epg` entry is equivalent to `{ "type": "xmltv", "id": "epg-<index>", "url": … }`
     * (index = position in the `epg` array as written).
     */
    fun epgIncludes(): List<TsiptvInclude> = epg.map {
        TsiptvInclude(
            id = "epg-${it.index}",
            type = TsiptvIncludeType.XMLTV,
            url = it.url,
            refreshHours = it.refreshHours,
        )
    }
}

// --- §5.1 Meta ------------------------------------------------------------------------------

@Serializable
data class TsiptvMeta(
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val author: TsiptvAuthor? = null,
    val logo: String? = null,
    val homepage: String? = null,
    val language: String = LocalizedTextResolver.FALLBACK_LANGUAGE,
    val languages: List<String> = emptyList(),
    val updatedAt: String? = null,
    val adult: Boolean = false,
    val license: String? = null,
)

@Serializable
data class TsiptvAuthor(
    val name: String,
    val url: String? = null,
    val email: String? = null,
)

// --- §6 Appearance --------------------------------------------------------------------------

/** As written by the creator (validated); [TsiptvAppearanceResolver] applies defaults and legibility rules. */
@Serializable
data class TsiptvAppearance(
    val accent: String? = null,
    val accentSecondary: String? = null,
    val background: TsiptvBackground? = null,
    val card: TsiptvCardStyle? = null,
)

@Serializable
data class TsiptvBackground(
    val color: String? = null,
    /** 2–3 stops, top to bottom; overrides [color]. */
    val gradient: List<String>? = null,
    val image: String? = null,
    /** 0.3–1.0; values below 0.3 were raised to 0.3. */
    val imageDim: Double = DEFAULT_IMAGE_DIM,
) {
    companion object {
        const val DEFAULT_IMAGE_DIM = 0.6
        const val MIN_IMAGE_DIM = 0.3
    }
}

/**
 * §6.1. Fields are null when the creator did not set them, so that a section's card can
 * override only some of them; [TsiptvCardResolver] fills the rest.
 */
@Serializable
data class TsiptvCardStyle(
    val style: TsiptvCardKind? = null,
    val corner: TsiptvCorner? = null,
    val showTitles: Boolean? = null,
)

@Serializable
enum class TsiptvCardKind {
    @SerialName("auto") AUTO,
    @SerialName("poster") POSTER,
    @SerialName("landscape") LANDSCAPE,
    @SerialName("square") SQUARE,
    @SerialName("logo") LOGO,
    @SerialName("list") LIST,
}

@Serializable
enum class TsiptvCorner(val dp: Int) {
    @SerialName("none") NONE(0),
    @SerialName("small") SMALL(4),
    @SerialName("medium") MEDIUM(12),
    @SerialName("large") LARGE(20),
}

// --- §7 Layout ------------------------------------------------------------------------------

@Serializable
data class TsiptvLayout(val home: List<TsiptvSection>)

@Serializable
enum class TsiptvSectionType {
    @SerialName("hero") HERO,
    @SerialName("row") ROW,
    @SerialName("grid") GRID,
}

/** Titles of the default layout (§7.5): the reader's own localized strings. */
@Serializable
enum class TsiptvBuiltInTitle { CONTINUE_WATCHING, MOVIES, SERIES, RADIO, CHANNELS }

/**
 * §7.1. A hero has exactly one of [query] or [items]; rows and grids always have a [query].
 *
 * @property seeAll Already defaulted: `true` for rows unless set to false; always false otherwise
 * @property groupChips Already restricted to grids over `channels`/`radio`
 * @property builtInTitle Set only on sections of the default layout, which have no [title]
 */
@Serializable
data class TsiptvSection(
    val type: TsiptvSectionType,
    val id: String? = null,
    val title: LocalizedText? = null,
    val subtitle: LocalizedText? = null,
    val query: TsiptvQuery? = null,
    val items: List<TsiptvHeroItem>? = null,
    val card: TsiptvCardStyle? = null,
    val seeAll: Boolean = false,
    val groupChips: Boolean = false,
    val builtInTitle: TsiptvBuiltInTitle? = null,
) {
    /**
     * The number of items to show: hero at most 10; row [TsiptvQuery.limit] or 20, at most 100;
     * grid [TsiptvQuery.limit] or null (all, paged by the reader).
     */
    val effectiveLimit: Int?
        get() {
            val limit = query?.limit
            return when (type) {
                TsiptvSectionType.HERO -> minOf(limit ?: TsiptvLimits.MAX_HERO_ITEMS, TsiptvLimits.MAX_HERO_ITEMS)
                TsiptvSectionType.ROW -> minOf(limit ?: TsiptvLimits.DEFAULT_ROW_LIMIT, TsiptvLimits.MAX_ROW_LIMIT)
                TsiptvSectionType.GRID -> limit
            }
        }
}

@Serializable
enum class TsiptvQuerySource {
    @SerialName("channels") CHANNELS,
    @SerialName("radio") RADIO,
    @SerialName("movies") MOVIES,
    @SerialName("series") SERIES,
    @SerialName("catalog") CATALOG,
    @SerialName("continueWatching") CONTINUE_WATCHING,
    @SerialName("favorites") FAVORITES,
}

@Serializable
enum class TsiptvQuerySort {
    @SerialName("source") SOURCE,
    @SerialName("name") NAME,
    @SerialName("number") NUMBER,
    @SerialName("year") YEAR,
    @SerialName("yearDesc") YEAR_DESC,
    @SerialName("recent") RECENT,
}

/**
 * §7.2. Filters combine with AND between fields and OR within a field.
 *
 * @property include Id of an include of this document (validated: exists, not `xmltv`; a
 *   `stremio` include for [TsiptvQuerySource.CATALOG])
 * @property ids Item references in display order (`id` or `includeId:itemId`); when set, [sort] is ignored
 * @property sort Already defaulted: [TsiptvQuerySort.RECENT] for `continueWatching`, else `source`
 */
@Serializable
data class TsiptvQuery(
    val from: TsiptvQuerySource,
    val include: String? = null,
    val catalog: TsiptvCatalogRef? = null,
    val ids: List<String>? = null,
    val groups: List<String>? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val sort: TsiptvQuerySort = TsiptvQuerySort.SOURCE,
    val limit: Int? = null,
)

/** A catalog of a `stremio` include. Whether the addon's manifest declares it is checked in step 2 (`W_QUERY_REF`). */
@Serializable
data class TsiptvCatalogRef(
    val type: String,
    val id: String,
    val genre: String? = null,
)

/** §7.3. A banner without [target] is decorative (not focusable on TV). */
@Serializable
data class TsiptvHeroItem(
    val image: String,
    val title: LocalizedText? = null,
    val subtitle: LocalizedText? = null,
    val target: String? = null,
    val autoplay: Boolean = false,
)

// --- §8 Content -----------------------------------------------------------------------------

/**
 * §8.4 with the item defaults of §8.0 already applied.
 *
 * @property headers Item `headers` merged under the stream's own (stream wins, per name, case-insensitively)
 * @property mimeType A recognised hint (the HLS spellings become [tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes.HLS]),
 *   from the stream or else the item; null when absent or not recognised
 * @property drm The stream's own `drm`, or else the item's. A [DrmSpec] with `isSupported == false`
 *   (unknown `system`) means: never play this stream (§11), try the next one.
 */
@Serializable
data class TsiptvStream(
    val url: String,
    val name: LocalizedText? = null,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val drm: DrmSpec? = null,
    val quality: String? = null,
    val language: String? = null,
)

@Serializable
enum class TsiptvChannelType {
    @SerialName("tv") TV,
    @SerialName("radio") RADIO,
}

/** §8.1. [streams] has 1–10 entries, best first. */
@Serializable
data class TsiptvChannel(
    val id: String,
    val name: LocalizedText,
    val type: TsiptvChannelType = TsiptvChannelType.TV,
    val number: Int? = null,
    val logo: String? = null,
    val groups: List<String> = emptyList(),
    val epgId: String? = null,
    val epgShiftHours: Double? = null,
    val description: LocalizedText? = null,
    val tags: List<String> = emptyList(),
    val catchup: CatchupSpec? = null,
    val streams: List<TsiptvStream>,
) {
    /**
     * The channel in the app's parser model, as the M3U parser would produce it: first stream,
     * its merged headers, MIME hint and DRM, and the name resolved for [uiLanguage].
     * Alternative streams are not representable in [IPTVChannel] and stay in [streams].
     *
     * @param id The id to store, normally namespaced by step 2/3 (`ts:{playlistId}:{itemId}`)
     */
    fun toIPTVChannel(id: String = this.id, uiLanguage: String? = null): IPTVChannel {
        val stream = streams.first()
        return IPTVChannel(
            id = id,
            name = name.resolve(uiLanguage),
            url = stream.url,
            logoUrl = logo,
            groupTitle = groups.firstOrNull(),
            epgId = epgId,
            groups = groups,
            number = number,
            isRadio = type == TsiptvChannelType.RADIO,
            headers = stream.headers,
            mimeType = stream.mimeType,
            drm = stream.drm,
            catchup = catchup,
            epgShiftHours = epgShiftHours,
        )
    }
}

@Serializable
enum class TsiptvSubtitleFormat {
    @SerialName("vtt") VTT,
    @SerialName("srt") SRT,
}

/** §8.7. [format] is already defaulted from the URL extension, else `vtt`. */
@Serializable
data class TsiptvSubtitle(
    val url: String,
    val language: String,
    val label: LocalizedText? = null,
    val format: TsiptvSubtitleFormat = TsiptvSubtitleFormat.VTT,
)

/** §8.2. */
@Serializable
data class TsiptvMovie(
    val id: String,
    val name: LocalizedText,
    val originalName: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val description: LocalizedText? = null,
    val year: Int? = null,
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val ageRating: String? = null,
    val tags: List<String> = emptyList(),
    val subtitles: List<TsiptvSubtitle> = emptyList(),
    val streams: List<TsiptvStream>,
)

/** §8.3. [seasons] are in document order; use [orderedSeasons] for display. */
@Serializable
data class TsiptvSeries(
    val id: String,
    val name: LocalizedText,
    val originalName: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val description: LocalizedText? = null,
    val year: Int? = null,
    val endYear: Int? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val ageRating: String? = null,
    val tags: List<String> = emptyList(),
    val seasons: List<TsiptvSeason>,
) {
    /** Ascending, with season 0 (Specials) last (§8.3). */
    fun orderedSeasons(): List<TsiptvSeason> =
        seasons.sortedWith(compareBy<TsiptvSeason> { it.number == 0 }.thenBy { it.number })
}

@Serializable
data class TsiptvSeason(
    val number: Int,
    val name: LocalizedText? = null,
    val poster: String? = null,
    val description: LocalizedText? = null,
    val episodes: List<TsiptvEpisode>,
) {
    fun orderedEpisodes(): List<TsiptvEpisode> = episodes.sortedBy { it.number }
}

/** §8.3. An episode whose [releaseDate] is in the future is "upcoming": listed, not playable. */
@Serializable
data class TsiptvEpisode(
    val id: String,
    val number: Int,
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val thumbnail: String? = null,
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val subtitles: List<TsiptvSubtitle> = emptyList(),
    val streams: List<TsiptvStream>,
) {
    /** @param today `YYYY-MM-DD` */
    fun isUpcoming(today: String): Boolean = releaseDate != null && releaseDate > today
}

// --- §9 EPG links and includes --------------------------------------------------------------

/** §9.1. [index] is the entry's position in `epg` as written, used for its implicit id `epg-<index>`. */
@Serializable
data class TsiptvEpgLink(
    val url: String,
    val refreshHours: Int = TsiptvLimits.DEFAULT_REFRESH_HOURS,
    val index: Int = 0,
)

@Serializable
enum class TsiptvIncludeType(val wireName: String) {
    @SerialName("m3u") M3U("m3u"),
    @SerialName("xmltv") XMLTV("xmltv"),
    @SerialName("stremio") STREMIO("stremio"),
    @SerialName("tsiptv-source") TSIPTV_SOURCE("tsiptv-source"),
}

/**
 * §9.2.
 *
 * @property headers Sent when fetching the include document (`m3u`, `xmltv`, `tsiptv-source`);
 *   always empty for `stremio`
 */
@Serializable
data class TsiptvInclude(
    val id: String,
    val type: TsiptvIncludeType,
    val url: String,
    val name: LocalizedText? = null,
    val refreshHours: Int = TsiptvLimits.DEFAULT_REFRESH_HOURS,
    val headers: Map<String, String> = emptyMap(),
)
