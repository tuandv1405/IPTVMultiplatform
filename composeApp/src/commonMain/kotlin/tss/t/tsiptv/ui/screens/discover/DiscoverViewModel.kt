package tss.t.tsiptv.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.network.NetworkConnectivityChecker
import tss.t.tsiptv.core.stremio.AddonRepository
import tss.t.tsiptv.core.stremio.AddonResult
import tss.t.tsiptv.core.stremio.BoardRow
import tss.t.tsiptv.core.stremio.InstalledAddon
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.core.stremio.MediaHistoryRepository
import tss.t.tsiptv.core.stremio.ResourceMatcher
import tss.t.tsiptv.core.stremio.SearchState
import tss.t.tsiptv.core.stremio.StremioMeta

sealed interface RowState {
    data object Loading : RowState
    data class Loaded(val items: List<StremioMeta>) : RowState
    data object Failed : RowState
}

data class DiscoverUiState(
    val rows: List<BoardRow> = emptyList(),
    val rowStates: Map<String, RowState> = emptyMap(),
    /** Type tabs after "All" (movie, series, tv, channel, then others alphabetically). */
    val types: List<String> = emptyList(),
    /** null = All. */
    val selectedType: String? = null,
    val continueWatching: List<MediaHistoryRecord> = emptyList(),
    val isOffline: Boolean = false,
) {
    val visibleRows: List<BoardRow> get() = rows.filter { selectedType == null || it.catalog.type == selectedType }
    val failedAddons: Int
        get() = rows.filter { rowStates[it.key] == RowState.Failed }.map { it.addon.id }.distinct().size
}

/**
 * Discover home (PRD §5). App-scoped so rows survive tab switches; row contents also come from
 * the client's HTTP cache.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DiscoverViewModel(
    private val repository: AddonRepository,
    private val history: MediaHistoryRepository,
    connectivity: NetworkConnectivityChecker,
) : ViewModel() {

    private val rowStates = MutableStateFlow<Map<String, RowState>>(emptyMap())
    private val selectedType = MutableStateFlow<String?>(null)
    private val rowJobs = HashMap<String, Job>()

    /** null until known (see [AddonRepository.hasActiveAddons]). */
    val hasActiveAddons: StateFlow<Boolean?> = repository.hasActiveAddons
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val offline = connectivity.observeNetworkStatus()
        .map { !it }
        .onStart { emit(!connectivity.isNetworkAvailable()) }
        .catch { emit(false) }
        .distinctUntilChanged()

    val uiState: StateFlow<DiscoverUiState> = combine(
        repository.addons,
        rowStates,
        selectedType,
        history.continueWatching,
        offline,
    ) { addons, states, type, cw, isOffline ->
        val rows = repository.boardRows(addons)
        val types = ResourceMatcher.orderTypes(rows.map { it.catalog.type })
        DiscoverUiState(
            rows = rows,
            rowStates = states,
            types = types,
            selectedType = type?.takeIf { it in types },
            continueWatching = cw.filter { record -> addons.any { it.id == record.sourceId } },
            isOffline = isOffline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    /** Called when a row scrolls into view (first page only). */
    fun loadRow(row: BoardRow, force: Boolean = false) {
        val key = row.key
        val current = rowStates.value[key]
        if (!force && (current is RowState.Loaded || current == RowState.Loading)) return
        rowJobs[key]?.cancel()
        rowStates.update { it + (key to RowState.Loading) }
        rowJobs[key] = viewModelScope.launch {
            val result = repository.catalog(row.addon, row.request)
            rowStates.update {
                it + (key to when (result) {
                    is AddonResult.Success -> RowState.Loaded(result.value.take(ROW_LIMIT))
                    is AddonResult.Failure -> RowState.Failed
                })
            }
        }
    }

    /** `addon_partial_failure` → Retry. */
    fun retryFailed() {
        val rows = uiState.value.rows
        rows.filter { rowStates.value[it.key] == RowState.Failed }.forEach { loadRow(it, force = true) }
    }

    /** After addons change, drop states of rows that no longer exist. */
    fun onRowsChanged(rows: List<BoardRow>) {
        val keys = rows.map { it.key }.toSet()
        rowStates.update { states -> states.filterKeys { it in keys } }
    }

    fun selectType(type: String?) {
        selectedType.value = type
    }

    // --- Search (PRD §5): ≥ 2 chars, 400 ms debounce; typing again cancels in-flight requests ---

    /** One search field with its own query and results (debounced, cancels on new input). */
    inner class SearchSession {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        val results: StateFlow<SearchState?> = _query
            .debounce(SEARCH_DEBOUNCE_MS)
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.length < MIN_QUERY) flowOf(null)
                else repository.search(q, repository.addons.value).map<SearchState, SearchState?> { it }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun setQuery(value: String) {
            _query.value = value
        }
    }

    /** The phone Discover field. */
    val inlineSearch = SearchSession()
    /** The search screen (TV search button, `stremio:///search` links): separate from the field. */
    val screenSearch = SearchSession()

    val query: StateFlow<String> get() = inlineSearch.query
    val search: StateFlow<SearchState?> get() = inlineSearch.results
    fun setQuery(value: String) = inlineSearch.setQuery(value)

    fun addonOf(record: MediaHistoryRecord): InstalledAddon? = repository.addonById(record.sourceId)

    /** TV menu key / phone long-press on a Continue watching card. */
    fun removeFromHistory(record: MediaHistoryRecord) {
        viewModelScope.launch { history.remove(record) }
    }

    companion object {
        const val ROW_LIMIT = 20
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MIN_QUERY = 2
    }
}
