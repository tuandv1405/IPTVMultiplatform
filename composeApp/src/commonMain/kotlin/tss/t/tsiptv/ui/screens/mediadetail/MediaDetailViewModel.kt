package tss.t.tsiptv.ui.screens.mediadetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.stremio.AddonRepository
import tss.t.tsiptv.core.stremio.ClassifiedStream
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.core.stremio.MediaHistoryRepository
import tss.t.tsiptv.core.stremio.MediaPlaybackContext
import tss.t.tsiptv.core.stremio.MediaRules
import tss.t.tsiptv.core.stremio.NextEpisodeInfo
import tss.t.tsiptv.core.stremio.SeasonGroup
import tss.t.tsiptv.core.stremio.StreamGroup
import tss.t.tsiptv.core.stremio.StreamsState
import tss.t.tsiptv.core.stremio.StremioJson
import tss.t.tsiptv.core.stremio.StremioMeta
import tss.t.tsiptv.core.stremio.StremioType
import tss.t.tsiptv.core.stremio.StremioVideo
import tss.t.tsiptv.core.stremio.toMediaItem
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.ui.screens.player.ADDON_MEDIA_ID_PREFIX
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class StreamPickerState(
    val video: StremioVideo,
    val streams: StreamsState = StreamsState(emptyList()),
    /** Off by default and not remembered (PRD §7). */
    val showUnsupported: Boolean = false,
    /** Flattened index (over visible rows) of the preferred stream: same addon + bingeGroup, else first playable. */
    val preferredIndex: Int = -1,
    /** The first result (inline or from the addons) has arrived. */
    val loaded: Boolean = false,
)

data class MediaDetailUiState(
    val loading: Boolean = true,
    val meta: StremioMeta? = null,
    val notFound: Boolean = false,
    val failedAddons: Int = 0,
    val seasons: List<SeasonGroup> = emptyList(),
    val selectedSeason: Int = 0,
    val isSeries: Boolean = false,
    val lastWatched: MediaHistoryRecord? = null,
    val picker: StreamPickerState? = null,
    val nowMs: Long = 0,
) {
    val isLive: Boolean get() = meta?.isLive == true
    /** Movies, tv and metas without episodes: one Play button for this video. */
    val playVideo: StremioVideo? get() = meta?.takeIf { !isSeries }?.effectiveVideos?.firstOrNull()
}

/** Meta detail and stream picker (PRD §6, §7). */
@OptIn(ExperimentalTime::class)
class MediaDetailViewModel(
    private val repository: AddonRepository,
    private val history: MediaHistoryRepository,
    private val tracker: tss.t.tsiptv.core.stremio.MediaProgressTracker,
    private val sourceDetails: tss.t.tsiptv.core.tsiptv.TsiptvDetailProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(MediaDetailUiState())
    val state: StateFlow<MediaDetailUiState> = _state.asStateFlow()

    private var openedKey: String? = null
    private var routeAddonId: String? = null
    private var sourceAddonId: String? = null
    private var type: String = ""
    private var streamsJob: Job? = null
    private var installed: List<tss.t.tsiptv.core.stremio.InstalledAddon> = emptyList()

    /** F3: set when the detail shows a TS IPTV Source movie or series (no addon involved). */
    private var sourceDetail: tss.t.tsiptv.core.tsiptv.TsiptvDetail? = null
    private var uiLanguage: String? = null
    private val historyKind: String
        get() = if (sourceDetail != null) tss.t.tsiptv.core.stremio.MediaSourceKind.TSIPTV else tss.t.tsiptv.core.stremio.MediaSourceKind.STREMIO

    private fun now() = Clock.System.now().toEpochMilliseconds()

    /**
     * F3 (PRD §4 "Detail pages"): a TS IPTV Source movie or series. Streams are the item's own, in
     * order; the first playable one is preselected; nothing is requested from addons.
     *
     * @param streamLabels Labels for unnamed streams, "Source 1" … "Source 10"
     */
    fun openSource(playlistId: String, itemId: String, videoId: String?, openStreams: Boolean, uiLanguage: String?, streamLabels: List<String>) {
        val key = "source|$playlistId|$itemId|$videoId|$openStreams|$uiLanguage"
        if (key == openedKey) return
        openedKey = key
        this.uiLanguage = uiLanguage
        _state.value = MediaDetailUiState(loading = true, nowMs = now())
        viewModelScope.launch {
            val detail = sourceDetails.load(playlistId, itemId, uiLanguage) { n -> streamLabels.getOrNull(n - 1) ?: "#$n" }
            sourceDetail = detail
            sourceAddonId = playlistId
            if (detail == null) {
                _state.value = MediaDetailUiState(loading = false, notFound = true, nowMs = now())
                return@launch
            }
            val meta = detail.meta
            type = meta.type.orEmpty()
            val last = history.latestForItem(itemId, historyKind)?.takeIf { it.sourceId == playlistId }
            val isSeries = type == StremioType.SERIES
            val seasons = if (isSeries) MediaRules.groupSeasons(meta.videos.orEmpty()) else emptyList()
            val focusVideoId = videoId ?: last?.videoId
            val seasonIndex = seasons.indexOfFirst { g -> g.episodes.any { it.id == focusVideoId } }.coerceAtLeast(0)
            _state.value = MediaDetailUiState(
                loading = false,
                meta = meta,
                seasons = seasons,
                selectedSeason = seasonIndex,
                isSeries = isSeries,
                lastWatched = last,
                nowMs = now(),
            )
            if (openStreams && videoId != null) {
                meta.effectiveVideos.firstOrNull { it.id == videoId }?.let { openStreams(it) }
            }
        }
    }

    fun open(type: String, id: String, addonId: String?, videoId: String?, openStreams: Boolean, previewJson: String) {
        val key = "$type|$id|$addonId|$videoId|$openStreams"
        if (key == openedKey) return
        openedKey = key
        sourceDetail = null
        this.type = type
        routeAddonId = addonId
        val preview = previewJson.takeIf { it.isNotBlank() }?.let {
            runCatching { StremioJson.decodeFromString(StremioMeta.serializer(), it) }.getOrNull()
        } ?: StremioMeta(id = id, type = type)
        // A series preview has no videos yet: never offer Play for it while the meta loads.
        _state.value = MediaDetailUiState(
            loading = true,
            meta = preview.takeIf { it.name != null },
            isSeries = type == StremioType.SERIES,
            nowMs = now(),
        )
        viewModelScope.launch {
            // From the database, not the in-memory list (empty right after process death).
            installed = repository.current()
            val aggregate = repository.meta(type, id, preview.takeIf { it.name != null }, installed)
            sourceAddonId = aggregate.sourceAddonId ?: addonId
            val meta = aggregate.meta
            val last = history.latestForItem(id)
            if (meta == null || !meta.isValid) {
                _state.value = MediaDetailUiState(loading = false, notFound = true, failedAddons = aggregate.failedAddons, nowMs = now())
                return@launch
            }
            val isSeries = MediaRules.isSeriesLike(meta)
            val seasons = if (isSeries) MediaRules.groupSeasons(meta.videos.orEmpty()) else emptyList()
            val focusVideoId = videoId ?: last?.videoId
            val seasonIndex = seasons.indexOfFirst { g -> g.episodes.any { it.id == focusVideoId } }.coerceAtLeast(0)
            _state.value = MediaDetailUiState(
                loading = false,
                meta = meta,
                failedAddons = aggregate.failedAddons,
                seasons = seasons,
                selectedSeason = seasonIndex,
                isSeries = isSeries,
                lastWatched = last,
                nowMs = now(),
            )
            // Continue watching / links with a video id, or behaviorHints.defaultVideoId: straight to the streams.
            val target = when {
                openStreams && videoId != null -> meta.effectiveVideos.firstOrNull { it.id == videoId }
                    ?: StremioVideo(id = videoId, title = meta.name)
                videoId == null && !meta.behaviorHints?.defaultVideoId.isNullOrBlank() ->
                    meta.effectiveVideos.firstOrNull { it.id == meta.behaviorHints?.defaultVideoId }
                else -> null
            }
            if (target != null) openStreams(target)
        }
    }

    /**
     * Re-reads the last watched video (e.g. on return from the player, where a new episode may
     * have been watched) and selects its season, so the TV focus lands on that episode.
     */
    fun refreshLastWatched() {
        val itemId = _state.value.meta?.id ?: return
        viewModelScope.launch {
            val saved = history.latestForItem(itemId, historyKind)
                ?.takeIf { sourceDetail == null || it.sourceId == sourceDetail?.playlistId }
            // The tracker knows what is playing right now; its first save may be < 10 s away, so on
            // Back from the player the just-watched video comes from it, not from history.
            val playing = tracker.currentContext?.takeIf { it.itemId == itemId }
            val last = when {
                playing == null -> saved
                saved != null && saved.videoId == playing.videoId -> saved
                else -> tss.t.tsiptv.core.stremio.MediaHistoryRecord(
                    sourceKind = playing.sourceKind, sourceId = playing.sourceAddonId,
                    itemType = playing.itemType, itemId = playing.itemId, videoId = playing.videoId, title = playing.title,
                    subtitle = playing.subtitle, posterUrl = playing.posterUrl, season = playing.season,
                    episode = playing.episode, lastAddonId = playing.streamAddonId, lastBingeGroup = playing.bingeGroup,
                    positionMs = 0, durationMs = 0, finished = false, updatedAt = 0,
                )
            }
            _state.update { s ->
                val index = s.seasons.indexOfFirst { g -> g.episodes.any { it.id == last?.videoId } }
                s.copy(lastWatched = last, selectedSeason = if (index >= 0) index else s.selectedSeason)
            }
        }
    }

    fun selectSeason(index: Int) = _state.update { it.copy(selectedSeason = index.coerceIn(0, (it.seasons.size - 1).coerceAtLeast(0))) }

    fun openStreams(video: StremioVideo) {
        if (MediaRules.isUpcoming(video, now())) return
        val videoId = video.id ?: return
        streamsJob?.cancel()
        _state.update { it.copy(picker = StreamPickerState(video)) }
        streamsJob = viewModelScope.launch {
            val last = history.latestForItem(_state.value.meta?.id ?: return@launch, historyKind)
            val list = installed.ifEmpty { repository.current().also { installed = it } }
            repository.streams(type, videoId, inline = video.streams, list = list).collect { streams ->
                _state.update { s ->
                    val picker = s.picker ?: return@update s
                    s.copy(picker = picker.copy(streams = streams, preferredIndex = preferred(streams, picker.showUnsupported, last), loaded = true))
                }
            }
        }
    }

    private fun preferred(streams: StreamsState, showUnsupported: Boolean, last: MediaHistoryRecord?): Int {
        val groups = streams.groups.map { g -> g.addonId to visible(g, showUnsupported) }
        return MediaRules.preferredStreamIndex(groups, last?.lastAddonId, last?.lastBingeGroup)
    }

    /**
     * Rows to show. A `stremio:///` link the app cannot route (e.g. a catalogue of an addon that is
     * not installed) is shown like any unsupported stream: greyed, not selectable.
     */
    fun visible(group: StreamGroup, showUnsupported: Boolean): List<ClassifiedStream> {
        val rows = group.streams.map { row ->
            val kind = row.kind
            if (kind is tss.t.tsiptv.core.stremio.StreamKind.Internal && routeFor(kind.url) == null) {
                ClassifiedStream(row.stream, tss.t.tsiptv.core.stremio.StreamKind.Unsupported("internal:unrouted"))
            } else row
        }
        return if (showUnsupported) rows else rows.filter { it.isSelectable }
    }

    fun toggleUnsupported() = _state.update { s ->
        val picker = s.picker ?: return@update s
        s.copy(picker = picker.copy(showUnsupported = !picker.showUnsupported))
    }

    fun closeStreams() {
        streamsJob?.cancel()
        _state.update { it.copy(picker = null) }
    }

    /**
     * The player item and history context for a chosen `url` stream (PRD §7 "Playback mapping"),
     * resuming from the saved position when it is between 60 s and 95 %.
     */
    suspend fun playbackFor(group: StreamGroup, row: ClassifiedStream): Pair<MediaItem, MediaPlaybackContext>? {
        sourceDetail?.let { return sourcePlaybackFor(it, row) }
        val s = _state.value
        val meta = s.meta ?: return null
        val video = s.picker?.video ?: return null
        val videoId = video.id ?: return null
        val streamAddonId = group.addon?.id
        val source = sourceAddonId ?: streamAddonId ?: routeAddonId ?: return null
        val episode = s.isSeries && (video.season != null || video.episodeNumber != null)
        val label = if (episode) MediaRules.episodeLabel(video) else null
        val shortEpisode = if (episode) {
            listOfNotNull(video.season?.let { "S$it" }, video.episodeNumber?.let { "E$it" }).joinToString("")
        } else null
        val title = listOfNotNull(meta.displayName, shortEpisode?.takeIf { it.isNotEmpty() }).joinToString(" · ")
        val item = row.toMediaItem(
            id = "$ADDON_MEDIA_ID_PREFIX${streamAddonId ?: StreamGroup.INLINE}:$videoId",
            title = title,
            subtitle = label ?: "",
            artworkUri = video.thumbnail?.takeIf { episode } ?: meta.poster,
        ) ?: return null
        val next = if (s.isSeries) MediaRules.nextEpisode(meta.videos.orEmpty(), videoId, now())?.let {
            NextEpisodeInfo(it.id!!, MediaRules.episodeLabel(it), it.season, it.episodeNumber)
        } else null
        val base = MediaPlaybackContext(
            sourceAddonId = source,
            itemType = meta.type ?: type,
            itemId = meta.id!!,
            videoId = videoId,
            title = meta.displayName,
            subtitle = label,
            posterUrl = meta.poster,
            season = video.season,
            episode = video.episodeNumber,
            streamAddonId = streamAddonId,
            bingeGroup = row.bingeGroup,
            isLive = s.isLive || meta.type == StremioType.TV,
            next = next,
        )
        val saved = history.find(base)
        val start = saved?.let { MediaRules.resumePosition(it.positionMs, it.durationMs) } ?: 0L
        return item to base.copy(startPositionMs = if (base.isLive) 0 else start)
    }

    /**
     * F3: the player item for a source stream, with its own headers, DRM, MIME hint and subtitles
     * (the picker row only carries the URL and headers), and history in this source.
     */
    private suspend fun sourcePlaybackFor(detail: tss.t.tsiptv.core.tsiptv.TsiptvDetail, row: ClassifiedStream): Pair<MediaItem, MediaPlaybackContext>? {
        val s = _state.value
        val meta = s.meta ?: return null
        val video = s.picker?.video ?: return null
        val videoId = video.id ?: return null
        val index = video.streams.orEmpty().indexOf(row.stream).takeIf { it >= 0 } ?: return null
        val stream = detail.streamFor(videoId, index) ?: return null
        val episode = s.isSeries
        val label = if (episode) MediaRules.episodeLabel(video) else null
        val shortEpisode = if (episode) {
            listOfNotNull(video.season?.let { "S$it" }, video.episodeNumber?.let { "E$it" }).joinToString("")
        } else null
        val title = listOfNotNull(meta.displayName, shortEpisode?.takeIf { it.isNotEmpty() }).joinToString(" · ")
        val item = detail.mediaItem(
            videoId = videoId,
            stream = stream,
            title = title,
            subtitle = label ?: "",
            artwork = video.thumbnail?.takeIf { episode } ?: meta.poster,
            uiLanguage = uiLanguage,
        )
        val next = if (s.isSeries) MediaRules.nextEpisode(meta.videos.orEmpty(), videoId, now())?.let {
            NextEpisodeInfo(it.id!!, MediaRules.episodeLabel(it), it.season, it.episodeNumber)
        } else null
        val base = MediaPlaybackContext(
            sourceAddonId = detail.playlistId,
            itemType = meta.type ?: type,
            itemId = meta.id!!,
            videoId = videoId,
            title = meta.displayName,
            subtitle = label,
            posterUrl = meta.poster,
            season = video.season,
            episode = video.episodeNumber,
            streamAddonId = null,
            bingeGroup = null,
            isLive = false,
            next = next,
            sourceKind = tss.t.tsiptv.core.stremio.MediaSourceKind.TSIPTV,
        )
        val saved = history.find(base)
        val start = saved?.let { MediaRules.resumePosition(it.positionMs, it.durationMs) } ?: 0L
        return item to base.copy(startPositionMs = start)
    }

    fun addonName(addonId: String?): String? = repository.addonById(addonId)?.name

    /** Route for an internal `stremio:///` link (meta links, `externalUrl` streams). */
    fun routeFor(url: String?): tss.t.tsiptv.navigation.NavRoutes.RootRoutes? =
        tss.t.tsiptv.ui.screens.discover.DeepLinkRouter.route(url, repository.addons.value, sourceAddonId ?: routeAddonId)
}
