package tss.t.tsiptv.ui.screens.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.parser.tsiptv.TsiptvAppearance
import tss.t.tsiptv.core.parser.tsiptv.TsiptvAppearanceResolver
import tss.t.tsiptv.core.parser.tsiptv.TsiptvEffectiveAppearance
import tss.t.tsiptv.core.parser.tsiptv.TsiptvLayout
import tss.t.tsiptv.core.parser.tsiptv.TsiptvMeta
import tss.t.tsiptv.core.stremio.AddonRepository
import tss.t.tsiptv.core.stremio.CatalogRequest
import tss.t.tsiptv.core.stremio.MediaHistoryRepository
import tss.t.tsiptv.core.stremio.StremioMeta
import tss.t.tsiptv.core.tsiptv.BuiltSection
import tss.t.tsiptv.core.tsiptv.SourceCatalogRef
import tss.t.tsiptv.core.tsiptv.SourceHomeBuilder
import tss.t.tsiptv.core.tsiptv.SourceItem
import tss.t.tsiptv.core.tsiptv.SourcePools
import tss.t.tsiptv.core.tsiptv.TsiptvSourceRecord
import tss.t.tsiptv.core.tsiptv.TsiptvVodItemRecord
import tss.t.tsiptv.core.tsiptv.TsiptvVodMapping
import tss.t.tsiptv.core.tsiptv.TsiptvStorageJson

/** A lazily loaded `from: "catalog"` row. */
sealed interface CatalogRowState {
    data object Loading : CatalogRowState
    data class Loaded(val addonId: String, val items: List<StremioMeta>) : CatalogRowState
    data object Failed : CatalogRowState
}

data class SourceHomeUiState(
    val playlistId: String? = null,
    val loading: Boolean = true,
    val meta: TsiptvMeta? = null,
    val appearance: TsiptvAppearance? = null,
    val effective: TsiptvEffectiveAppearance = TsiptvAppearanceResolver.resolve(null).first,
    val sections: List<BuiltSection> = emptyList(),
    val hasChannels: Boolean = false,
    val catalogs: Map<String, CatalogRowState> = emptyMap(),
    val query: String = "",
    val results: List<SourceItem> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && sections.isEmpty()
}

/**
 * Source Home (PRD F3 §4): the layout of the active TS IPTV Source over its pools, with the
 * appearance and legibility rules applied. Texts stay localized objects; the UI resolves them
 * with the current UI language, so a language change needs no re-import.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceHomeViewModel(
    private val database: IPTVDatabase,
    private val addons: AddonRepository,
    private val history: MediaHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SourceHomeUiState())
    val state: StateFlow<SourceHomeUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var opened: Pair<String, String?>? = null
    private var pools: SourcePools? = null
    private var layout: TsiptvLayout? = null
    private var uiLanguage: String? = null

    /** Starts observing [playlistId]; the sections follow the database (refresh, history, favourites). */
    fun open(playlistId: String, uiLanguage: String?) {
        if (opened == playlistId to uiLanguage) return
        val samePlaylist = opened?.first == playlistId
        opened = playlistId to uiLanguage
        this.uiLanguage = uiLanguage
        if (!samePlaylist) _state.value = SourceHomeUiState(playlistId = playlistId)
        job?.cancel()
        val store = database.tsiptvStore
        job = viewModelScope.launch {
            val source = store.observeSource(playlistId)
            // Episodes only change with the source (import/refresh): read them per fetch, ids only.
            val episodeSeries = source.distinctUntilChangedBy { it?.fetchedAt }.flatMapLatest { record ->
                if (record == null) flowOf(emptyMap())
                else flowOf(store.getAllEpisodes(playlistId).associate { it.episodeId to it.seriesItemId })
            }
            val vod = store.observeVodItems(playlistId).map { rows -> rows.map { TsiptvVodMapping.item(it, withStreams = false) to it.kind } }
            val watchedChannels = database.getAllWatchedChannelsWithDetails(playlistId)
                .map { rows -> rows.associate { it.channelId to it.lastPlayedTimestamp } }
            val base = combine(source, store.observeIncludes(playlistId), vod, database.getAllChannelsByPlayListId(playlistId)) { s, inc, v, ch ->
                Quad(s, inc, v, ch)
            }
            combine(base, episodeSeries, history.continueWatchingForSource(playlistId), watchedChannels) { b, episodes, media, recent ->
                val record = b.source ?: return@combine null
                SourcePools(
                    playlistId = playlistId,
                    channels = b.channels,
                    movies = b.vod.filter { it.second == TsiptvVodItemRecord.KIND_MOVIE }.map { it.first },
                    series = b.vod.filter { it.second == TsiptvVodItemRecord.KIND_SERIES }.map { it.first },
                    includes = b.includes.associateBy { it.includePath },
                    continueMedia = media,
                    recentChannels = recent,
                    episodeSeries = episodes,
                ) to record
            }.flowOn(Dispatchers.Default).collect { value ->
                if (value == null) {
                    _state.update { it.copy(loading = false, sections = emptyList()) }
                    return@collect
                }
                val (pools, record) = value
                apply(pools, record)
            }
        }
    }

    private data class Quad(
        val source: TsiptvSourceRecord?,
        val includes: List<tss.t.tsiptv.core.tsiptv.TsiptvIncludeRecord>,
        val vod: List<Pair<tss.t.tsiptv.core.tsiptv.TsiptvVodItem, String>>,
        val channels: List<tss.t.tsiptv.core.model.Channel>,
    )

    private fun apply(pools: SourcePools, record: TsiptvSourceRecord) {
        this.pools = pools
        val json = TsiptvStorageJson
        val meta = runCatching { json.decodeFromString(TsiptvMeta.serializer(), record.metaJson) }.getOrNull()
        val appearance = record.appearanceJson?.let { runCatching { json.decodeFromString(TsiptvAppearance.serializer(), it) }.getOrNull() }
        val storedLayout = record.layoutJson?.let { runCatching { json.decodeFromString(TsiptvLayout.serializer(), it) }.getOrNull() }
        val layout = SourceHomeBuilder.layout(storedLayout, pools)
        this.layout = layout
        val sections = SourceHomeBuilder.build(layout, pools, uiLanguage)
        val (effective, _) = TsiptvAppearanceResolver.resolve(appearance)
        _state.update { s ->
            s.copy(
                loading = false,
                meta = meta,
                appearance = appearance,
                effective = effective,
                sections = sections,
                hasChannels = pools.channels.isNotEmpty(),
                results = if (s.query.isBlank()) emptyList() else SourceHomeBuilder.search(pools, s.query, uiLanguage),
            )
        }
    }

    /** Lazily loads a catalogue row (as it enters composition). Hidden when it loads empty. */
    fun loadCatalog(key: String, ref: SourceCatalogRef) {
        if (_state.value.catalogs[key] != null) return
        _state.update { it.copy(catalogs = it.catalogs + (key to CatalogRowState.Loading)) }
        viewModelScope.launch {
            val addon = addons.addonById(ref.addonId) ?: addons.current().firstOrNull { it.id == ref.addonId }
            val result = if (addon == null || !addon.isActive) null else addons.catalog(
                addon,
                CatalogRequest(ref.type, ref.catalogId, listOfNotNull(ref.genre?.let { "genre" to it })),
            )
            val items = result?.valueOrNull
            _state.update {
                it.copy(
                    catalogs = it.catalogs + (key to if (items != null) CatalogRowState.Loaded(ref.addonId, items) else CatalogRowState.Failed)
                )
            }
        }
    }

    /** "See all": the section's whole query (no limit). */
    fun seeAll(sectionKey: String): List<SourceItem> {
        val pools = pools ?: return emptyList()
        val section = _state.value.sections.firstOrNull { it.key == sectionKey }?.section ?: return emptyList()
        val query = section.query ?: return emptyList()
        return SourceHomeBuilder.run(query, pools, uiLanguage)
    }

    /** The channels of a section, for zapping in the player (AC-T19). */
    fun sectionChannels(sectionKey: String): List<tss.t.tsiptv.core.model.Channel> =
        seeAll(sectionKey).mapNotNull { (it as? SourceItem.ChannelItem)?.channel }

    /** "Now on" title of a channel for `list` cards (guide data only; no network). */
    @OptIn(kotlin.time.ExperimentalTime::class)
    suspend fun currentProgramme(channel: tss.t.tsiptv.core.model.Channel): String? = runCatching {
        val shiftMs = ((channel.epgShiftHours ?: 0.0) * 3_600_000).toLong()
        database.getCurrentProgramForChannel(channel.guideId, kotlin.time.Clock.System.now().toEpochMilliseconds() - shiftMs)?.title
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun setQuery(text: String) {
        val pools = pools
        _state.update {
            it.copy(query = text, results = if (pools == null || text.isBlank()) emptyList() else SourceHomeBuilder.search(pools, text, uiLanguage))
        }
    }
}
