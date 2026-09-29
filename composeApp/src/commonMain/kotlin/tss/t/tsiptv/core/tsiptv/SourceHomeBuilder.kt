package tss.t.tsiptv.core.tsiptv

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.parser.tsiptv.LocalizedText
import tss.t.tsiptv.core.parser.tsiptv.TsiptvLayout
import tss.t.tsiptv.core.parser.tsiptv.TsiptvPoolSummary
import tss.t.tsiptv.core.parser.tsiptv.TsiptvDefaultLayout
import tss.t.tsiptv.core.parser.tsiptv.TsiptvQuery
import tss.t.tsiptv.core.parser.tsiptv.TsiptvQuerySort
import tss.t.tsiptv.core.parser.tsiptv.TsiptvQuerySource
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSection
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSectionType
import tss.t.tsiptv.core.stremio.MediaHistoryRecord

/** One card of a Source Home section. [key] is unique within its section. */
sealed interface SourceItem {
    val key: String

    /** A TV or radio channel of this source. */
    data class ChannelItem(val channel: Channel, val name: LocalizedText?) : SourceItem {
        override val key: String get() = "c|" + channel.id
    }

    /** A movie or series. */
    data class Vod(val item: TsiptvVodItem) : SourceItem {
        override val key: String get() = "v|" + item.itemId
    }

    /** An episode (hero targets only): opens its series with the episode selected. */
    data class Episode(val series: TsiptvVodItem, val episodeId: String) : SourceItem {
        override val key: String get() = "e|" + episodeId
    }

    /** Continue watching: a partly watched movie or episode ([item] null if it left the source). */
    data class Continue(val record: MediaHistoryRecord, val item: TsiptvVodItem?) : SourceItem {
        override val key: String get() = "m|" + record.itemId
    }
}

/** A hero banner (spec §7.3), or a hero item made from a query result. */
data class SourceBanner(
    val image: String,
    val title: LocalizedText?,
    val subtitle: LocalizedText?,
    /** null: decorative (not focusable on TV). */
    val target: SourceItem?,
    val autoplay: Boolean,
)

/** A `from: "catalog"` section: loaded lazily from the Stremio include's addon. */
data class SourceCatalogRef(val addonId: String, val type: String, val catalogId: String, val genre: String?)

/** A section ready to render. Sections without items are left out, except catalogs (loaded later). */
data class BuiltSection(
    val key: String,
    val section: TsiptvSection,
    val items: List<SourceItem>,
    val banners: List<SourceBanner> = emptyList(),
    val catalog: SourceCatalogRef? = null,
    /** "See all" opens the same query without the section limit. */
    val totalCount: Int = items.size,
)

/**
 * Everything the home queries select from (spec §7.2 "pools"): the source's own items plus the
 * content of its includes, already merged and namespaced.
 */
class SourcePools(
    val playlistId: String,
    val channels: List<Channel>,
    val movies: List<TsiptvVodItem>,
    val series: List<TsiptvVodItem>,
    /** Include rows by include path. */
    val includes: Map<String, TsiptvIncludeRecord>,
    /** `media_history` rows of this source, newest first (unfinished, resumable). */
    val continueMedia: List<MediaHistoryRecord> = emptyList(),
    /** Recently played channels of this source: channel id → last played time. */
    val recentChannels: Map<String, Long> = emptyMap(),
    /** Episode id → series item id (hero targets that name an episode). */
    val episodeSeries: Map<String, String> = emptyMap(),
) {
    val tv: List<Channel> by lazy { channels.filter { !it.isRadio } }
    val radio: List<Channel> by lazy { channels.filter { it.isRadio } }
    private val channelsByPoolId: Map<String, Channel> by lazy {
        channels.associateBy { TsiptvSourceIds.poolIdOf(playlistId, it.id) ?: it.id }
    }
    private val vodById: Map<String, TsiptvVodItem> by lazy { (movies + series).associateBy { it.itemId } }

    fun channelByPoolId(poolId: String): Channel? = channelsByPoolId[poolId]
    fun vod(itemId: String): TsiptvVodItem? = vodById[itemId]

    fun summary() = TsiptvPoolSummary(
        hasMovies = movies.isNotEmpty(),
        hasMovieWithBackdrop = movies.any { it.backdrop != null },
        hasSeries = series.isNotEmpty(),
        hasRadio = radio.isNotEmpty(),
        hasChannels = tv.isNotEmpty(),
    )
}

/**
 * Source Home query engine (spec §7): resolves the layout (or the default one), runs each query
 * over the pools (include restriction, `ids`, filters, sort, limit) and builds hero banners.
 * Pure: the view model supplies the pools and the UI language.
 */
object SourceHomeBuilder {

    fun layout(stored: TsiptvLayout?, pools: SourcePools): TsiptvLayout =
        stored?.takeIf { it.home.isNotEmpty() } ?: TsiptvDefaultLayout.build(pools.summary())

    fun build(layout: TsiptvLayout, pools: SourcePools, uiLanguage: String?): List<BuiltSection> =
        layout.home.mapIndexedNotNull { index, section ->
            val key = section.id?.let { "s:$it" } ?: "i:$index"
            buildSection(key, section, pools, uiLanguage)
        }

    private fun buildSection(key: String, section: TsiptvSection, pools: SourcePools, uiLanguage: String?): BuiltSection? {
        if (section.type == TsiptvSectionType.HERO && section.items != null) {
            val banners = section.items.map { item ->
                SourceBanner(item.image, item.title, item.subtitle, item.target?.let { resolveRef(it, pools) }, item.autoplay)
            }
            return BuiltSection(key, section, emptyList(), banners).takeIf { banners.isNotEmpty() }
        }
        val query = section.query ?: return null
        if (query.from == TsiptvQuerySource.CATALOG) {
            val ref = catalogRef(query, pools) ?: return null
            return BuiltSection(key, section, emptyList(), catalog = ref)
        }
        val all = run(query, pools, uiLanguage)
        if (all.isEmpty()) return null
        val limited = section.effectiveLimit?.let { all.take(it) } ?: all
        if (section.type == TsiptvSectionType.HERO) {
            // Backdrop, else poster, else logo; items without an image are skipped (spec §7.1).
            val banners = all.mapNotNull { item -> bannerFor(item) }.take(section.effectiveLimit ?: 10)
            return BuiltSection(key, section, emptyList(), banners).takeIf { banners.isNotEmpty() }
        }
        return BuiltSection(key, section, limited, totalCount = all.size)
    }

    private fun bannerFor(item: SourceItem): SourceBanner? = when (item) {
        is SourceItem.Vod -> (item.item.backdrop ?: item.item.poster ?: item.item.logo)?.let {
            SourceBanner(it, item.item.name, null, item, autoplay = false)
        }
        is SourceItem.ChannelItem -> item.channel.logoUrl?.let {
            SourceBanner(it, item.name ?: LocalizedText.plain(item.channel.name), null, item, autoplay = false)
        }
        is SourceItem.Continue -> item.item?.let { vod ->
            (vod.backdrop ?: vod.poster)?.let { SourceBanner(it, vod.name, null, item, autoplay = false) }
        }
        is SourceItem.Episode -> null
    }

    /** All items of a query, sorted and filtered, without the section limit ("See all"). */
    fun run(query: TsiptvQuery, pools: SourcePools, uiLanguage: String?): List<SourceItem> {
        if (query.from == TsiptvQuerySource.CONTINUE_WATCHING) return continueWatching(pools)
        if (query.from == TsiptvQuerySource.CATALOG) return emptyList()
        val channels: List<Channel>? = when (query.from) {
            TsiptvQuerySource.CHANNELS -> pools.tv
            TsiptvQuerySource.RADIO -> pools.radio
            TsiptvQuerySource.FAVORITES -> pools.channels.filter { it.isFavorite }
            else -> null
        }
        val items: List<SourceItem> = if (channels != null) {
            channels.asSequence()
                .filter { inInclude(it.originIncludePath, query.include) }
                .filter { query.groups == null || it.groups.any { g -> query.groups.any { q -> q.equals(g, ignoreCase = true) } } }
                .filter { query.tags == null || channelTags(it).any { t -> query.tags.any { q -> q.equals(t, ignoreCase = true) } } }
                .map { SourceItem.ChannelItem(it, TsiptvVodMapping.decodeText(it.nameJson)) }
                .toList()
        } else {
            val vod = if (query.from == TsiptvQuerySource.MOVIES) pools.movies else pools.series
            vod.asSequence()
                .filter { inInclude(it.originIncludePath, query.include) }
                .filter { query.genres == null || it.genres.any { g -> query.genres.any { q -> q.equals(g, ignoreCase = true) } } }
                .filter { query.tags == null || it.tags.any { t -> query.tags.any { q -> q.equals(t, ignoreCase = true) } } }
                .map { SourceItem.Vod(it) }
                .toList()
        }
        query.ids?.let { ids ->
            // `ids`: only these items, in this order (sort ignored); unknown ids are skipped.
            val byRef = items.associateBy { refOf(it, pools.playlistId) }
            return ids.mapNotNull { byRef[it] }.distinctBy { it.key }
        }
        return sort(items, query.sort, uiLanguage)
    }

    private fun refOf(item: SourceItem, playlistId: String): String = when (item) {
        is SourceItem.ChannelItem -> TsiptvSourceIds.poolIdOf(playlistId, item.channel.id) ?: item.channel.id
        is SourceItem.Vod -> item.item.itemId
        is SourceItem.Episode -> item.episodeId
        is SourceItem.Continue -> item.record.itemId
    }

    private fun inInclude(origin: String?, include: String?): Boolean =
        include == null || origin == include || origin?.startsWith("$include:") == true

    private fun channelTags(channel: Channel): List<String> =
        channel.tagsJson?.let {
            runCatching { (TsiptvStorageJson.parseToJsonElement(it) as JsonArray).mapNotNull { e -> e.jsonPrimitive.contentOrNull } }.getOrNull()
        }.orEmpty()

    fun displayName(item: SourceItem, uiLanguage: String?): String = when (item) {
        is SourceItem.ChannelItem -> item.name?.resolve(uiLanguage) ?: item.channel.name
        is SourceItem.Vod -> item.item.name.resolve(uiLanguage)
        is SourceItem.Episode -> item.series.name.resolve(uiLanguage)
        is SourceItem.Continue -> item.item?.name?.resolve(uiLanguage) ?: item.record.title.orEmpty()
    }

    private fun sort(items: List<SourceItem>, sort: TsiptvQuerySort, uiLanguage: String?): List<SourceItem> = when (sort) {
        TsiptvQuerySort.SOURCE, TsiptvQuerySort.RECENT -> items
        TsiptvQuerySort.NAME -> items.sortedBy { TextFold.fold(displayName(it, uiLanguage)) }
        TsiptvQuerySort.NUMBER -> items.sortedWith(compareBy<SourceItem>({ numberOf(it) == null }, { numberOf(it) ?: 0 }))
        TsiptvQuerySort.YEAR -> items.sortedWith(compareBy<SourceItem>({ yearOf(it) == null }, { yearOf(it) ?: 0 }))
        TsiptvQuerySort.YEAR_DESC -> items.sortedWith(compareBy<SourceItem>({ yearOf(it) == null }, { -(yearOf(it) ?: 0) }))
    }

    private fun numberOf(item: SourceItem): Int? = (item as? SourceItem.ChannelItem)?.channel?.number
    private fun yearOf(item: SourceItem): Int? = (item as? SourceItem.Vod)?.item?.year

    /**
     * Partly watched movies and episodes of this source, and its recently played channels, most
     * recent first (spec §7.2 `continueWatching`).
     */
    private fun continueWatching(pools: SourcePools): List<SourceItem> {
        val media = pools.continueMedia.map { it.updatedAt to SourceItem.Continue(it, pools.vod(it.itemId)) as SourceItem }
        val channels = pools.recentChannels.mapNotNull { (id, time) ->
            pools.channels.firstOrNull { it.id == id }?.let { time to SourceItem.ChannelItem(it, TsiptvVodMapping.decodeText(it.nameJson)) }
        }
        return (media + channels).sortedByDescending { it.first }.map { it.second }
    }

    /** A hero `target` or query id: a channel, movie, series or episode of the pools. */
    fun resolveRef(ref: String, pools: SourcePools): SourceItem? {
        pools.vod(ref)?.let { return SourceItem.Vod(it) }
        pools.channelByPoolId(ref)?.let { return SourceItem.ChannelItem(it, TsiptvVodMapping.decodeText(it.nameJson)) }
        pools.episodeSeries[ref]?.let { seriesId -> pools.vod(seriesId)?.let { return SourceItem.Episode(it, ref) } }
        return null
    }

    /**
     * The addon catalogue of a `from: "catalog"` query, or null when the include failed or the
     * catalogue is not declared in the addon's manifest (`W_QUERY_REF`, section skipped).
     */
    fun catalogRef(query: TsiptvQuery, pools: SourcePools): SourceCatalogRef? {
        val include = pools.includes[query.include ?: return null] ?: return null
        val catalog = query.catalog ?: return null
        val manifest = include.documentJson?.let {
            runCatching { TsiptvStorageJson.parseToJsonElement(it) as JsonObject }.getOrNull()
        } ?: return null
        val addonId = manifest["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val declared = (manifest["catalogs"] as? JsonArray).orEmpty().any { entry ->
            val obj = entry as? JsonObject ?: return@any false
            obj["type"]?.jsonPrimitive?.contentOrNull == catalog.type && obj["id"]?.jsonPrimitive?.contentOrNull == catalog.id
        }
        if (!declared) return null
        return SourceCatalogRef(addonId, catalog.type, catalog.id, catalog.genre)
    }

    /** Local search (PRD §4 "Search"): names of channels, radio, movies and series; case- and accent-insensitive. */
    fun search(pools: SourcePools, text: String, uiLanguage: String?): List<SourceItem> {
        val needle = TextFold.fold(text.trim())
        if (needle.isEmpty()) return emptyList()
        val channels = pools.channels.map { SourceItem.ChannelItem(it, TsiptvVodMapping.decodeText(it.nameJson)) }
        val vod = (pools.movies + pools.series).map { SourceItem.Vod(it) }
        return (vod + channels).filter { item ->
            val names = when (item) {
                is SourceItem.ChannelItem -> listOfNotNull(item.channel.name) + item.name?.values?.values.orEmpty()
                is SourceItem.Vod -> item.item.name.values.values.toList() + listOfNotNull(item.item.originalName)
                else -> emptyList()
            }
            names.any { TextFold.fold(it).contains(needle) }
        }.take(SEARCH_LIMIT)
    }

    private const val SEARCH_LIMIT = 200
}

/** Case- and accent-insensitive comparison key (Latin, Vietnamese). */
object TextFold {
    private val map: Map<Char, Char> = buildMap {
        fun add(target: Char, chars: String) = chars.forEach { put(it, target) }
        add('a', "àáâãäåāăąạảấầẩẫậắằẳẵặǎ")
        add('c', "çćĉċč")
        add('d', "đďð")
        add('e', "èéêëēĕėęěẹẻẽếềểễệ")
        add('g', "ĝğġģ")
        add('i', "ìíîïĩīĭįıịỉ")
        add('n', "ñńņňŉ")
        add('o', "òóôõöøōŏőơọỏốồổỗộớờởỡợ")
        add('s', "śŝşšß")
        add('u', "ùúûüũūŭůűųưụủứừửữự")
        add('y', "ýÿŷỳỵỷỹ")
        add('z', "źżž")
    }

    fun fold(text: String): String = buildString(text.length) {
        for (c in text.lowercase()) append(map[c] ?: c)
    }
}
