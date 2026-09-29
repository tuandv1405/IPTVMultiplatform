package tss.t.tsiptv.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import tss.t.tsiptv.core.stremio.AddonRepository
import tss.t.tsiptv.core.stremio.AddonResult
import tss.t.tsiptv.core.stremio.CatalogPager
import tss.t.tsiptv.core.stremio.InstalledAddon
import tss.t.tsiptv.core.stremio.ManifestCatalog
import tss.t.tsiptv.core.stremio.ResourceMatcher
import tss.t.tsiptv.core.stremio.StremioExtra
import tss.t.tsiptv.core.stremio.StremioJson
import tss.t.tsiptv.core.stremio.StremioMeta

data class CatalogUiState(
    val addon: InstalledAddon? = null,
    val catalog: ManifestCatalog? = null,
    val genreOptions: List<String> = emptyList(),
    val genreLimit: Int = 1,
    val genreRequired: Boolean = false,
    val selectedGenres: List<String> = emptyList(),
    val items: List<StremioMeta> = emptyList(),
    val loading: Boolean = false,
    /** The last page request failed (not the silent post-short-page probe): show Retry. */
    val failed: Boolean = false,
    val endReached: Boolean = false,
    /** The addon or catalogue is not installed/enabled. */
    val notFound: Boolean = false,
)

/** Catalogue screen: genre chips and paging (PRD §5; lenient paging rule with `onFailure`). */
class CatalogViewModel(private val repository: AddonRepository) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var pager: CatalogPager? = null
    private var pageJob: Job? = null
    private var openedKey: String? = null
    private var extraSelections: Map<String, List<String>> = emptyMap()

    fun open(addonId: String, type: String, catalogId: String, extraJson: String) {
        val key = "$addonId|$type|$catalogId|$extraJson"
        if (key == openedKey) return
        openedKey = key
        // Read the installed addons from the database: after process death the in-memory list is
        // still empty when this screen is restored.
        viewModelScope.launch { openLoaded(addonId, type, catalogId, extraJson) }
    }

    private suspend fun openLoaded(addonId: String, type: String, catalogId: String, extraJson: String) {
        val addon = repository.addonById(addonId, repository.current())?.takeIf { it.isActive }
        val catalog = addon?.let { ResourceMatcher.findCatalog(it.manifest, type, catalogId) }
        if (addon == null || catalog == null) {
            _state.value = CatalogUiState(notFound = true)
            return
        }
        val initial = parseExtra(extraJson).filterKeys { name -> catalog.extra(name) != null && name != StremioExtra.SKIP }
        val genre = catalog.extra(StremioExtra.GENRE)
        val defaults = ResourceMatcher.defaultRequiredSelections(catalog).orEmpty()
        extraSelections = defaults + initial.filterKeys { it != StremioExtra.GENRE }
        val selectedGenres = (initial[StremioExtra.GENRE] ?: defaults[StremioExtra.GENRE]).orEmpty()
            .take(genre?.optionsLimit ?: 1)
        _state.value = CatalogUiState(
            addon = addon,
            catalog = catalog,
            genreOptions = genre?.options.orEmpty(),
            genreLimit = genre?.optionsLimit ?: 1,
            genreRequired = genre?.isRequired == true,
            selectedGenres = selectedGenres,
        )
        restart()
    }

    /** Single select, or multi-select up to `optionsLimit`; a required genre cannot be cleared. */
    fun toggleGenre(option: String) {
        val s = _state.value
        val selected = s.selectedGenres
        val next = when {
            option in selected -> if (s.genreRequired && selected.size == 1) selected else selected - option
            s.genreLimit <= 1 -> listOf(option)
            selected.size >= s.genreLimit -> selected.drop(1) + option
            else -> selected + option
        }
        if (next == selected) return
        _state.update { it.copy(selectedGenres = next) }
        restart()
    }

    private fun restart() {
        val catalog = _state.value.catalog ?: return
        pageJob?.cancel()
        val selections = extraSelections + (if (_state.value.selectedGenres.isEmpty()) emptyMap()
        else mapOf(StremioExtra.GENRE to _state.value.selectedGenres))
        pager = CatalogPager(catalog, selections)
        _state.update { it.copy(items = emptyList(), endReached = false, failed = false, loading = false) }
        loadMore()
    }

    /** Next page (called when the grid nears its end, and by Retry). */
    fun loadMore() {
        val p = pager ?: return
        val addon = _state.value.addon ?: return
        if (_state.value.loading || p.isEnd) return
        val request = p.nextRequest() ?: run {
            _state.update { it.copy(endReached = true) }
            return
        }
        _state.update { it.copy(loading = true, failed = false) }
        pageJob = viewModelScope.launch {
            when (val r = repository.catalog(addon, request)) {
                is AddonResult.Success -> {
                    val fresh = p.accept(r.value)
                    _state.update { it.copy(items = it.items + fresh, loading = false, endReached = p.isEnd) }
                }
                is AddonResult.Failure -> when (p.onFailure(r.error)) {
                    // Static hosts answer the lenient probe after a short page with a 404: not a failure.
                    CatalogPager.PageFailure.END_OF_CATALOG ->
                        _state.update { it.copy(loading = false, endReached = true) }
                    CatalogPager.PageFailure.FAILED ->
                        _state.update { it.copy(loading = false, failed = true) }
                }
            }
        }
    }

    private fun parseExtra(json: String): Map<String, List<String>> =
        if (json.isBlank()) emptyMap()
        else runCatching {
            StremioJson.decodeFromString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), json)
        }.getOrDefault(emptyMap())

    companion object {
        fun extraJson(extra: List<Pair<String, String>>): String {
            if (extra.isEmpty()) return ""
            val map = extra.filter { it.first != StremioExtra.SKIP }.groupBy({ it.first }, { it.second })
            return StremioJson.encodeToString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), map)
        }
    }
}
