package tss.t.tsiptv.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tss.t.tsiptv.core.stremio.BoardRow
import tss.t.tsiptv.core.stremio.InstalledAddon
import tss.t.tsiptv.core.stremio.ManifestCatalog
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.core.stremio.SearchState
import tss.t.tsiptv.core.stremio.StremioJson
import tss.t.tsiptv.core.stremio.StremioMeta
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.addons.AddonChip
import tss.t.tsiptv.ui.screens.addons.AddonSectionTitle
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.screens.addons.PosterCard
import tss.t.tsiptv.ui.screens.addons.addonTypeLabel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvDefaults
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.ui.widgets.TSTextField
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.addon_partial_failure
import tsiptv.composeapp.generated.resources.addon_retry
import tsiptv.composeapp.generated.resources.continue_watching_media
import tsiptv.composeapp.generated.resources.discover_empty_catalog
import tsiptv.composeapp.generated.resources.discover_no_results
import tsiptv.composeapp.generated.resources.discover_offline
import tsiptv.composeapp.generated.resources.discover_search_hint
import tsiptv.composeapp.generated.resources.discover_see_all
import tsiptv.composeapp.generated.resources.discover_title
import tsiptv.composeapp.generated.resources.discover_type_all

/** Navigation targets of Discover cards (shared by phone, TV and search). */
object DiscoverNav {
    private const val PREVIEW_DESCRIPTION_CHARS = 300

    fun detail(addon: InstalledAddon?, catalogType: String, meta: StremioMeta) = NavRoutes.MediaDetail(
        type = meta.type ?: catalogType,
        id = meta.id.orEmpty(),
        addonId = addon?.id,
        // Kept small (it travels in the navigation Bundle): no videos or links, a short description.
        previewJson = StremioJson.encodeToString(
            StremioMeta.serializer(),
            meta.copy(videos = null, links = null, cast = null, director = null, description = meta.description?.take(PREVIEW_DESCRIPTION_CHARS)),
        ),
    )

    fun continueWatching(record: MediaHistoryRecord) =
        if (record.sourceKind == tss.t.tsiptv.core.stremio.MediaSourceKind.TSIPTV) sourceContinueWatching(record)
        else addonContinueWatching(record)

    /** F3: a TS IPTV Source title, straight to the streams of its video (resume). */
    fun sourceContinueWatching(record: MediaHistoryRecord) = NavRoutes.MediaDetail(
        type = record.itemType,
        id = record.itemId,
        videoId = record.videoId,
        openStreams = true,
        sourcePlaylistId = record.sourceId,
    )

    private fun addonContinueWatching(record: MediaHistoryRecord) = NavRoutes.MediaDetail(
        type = record.itemType,
        id = record.itemId,
        addonId = record.sourceId,
        videoId = record.videoId,
        openStreams = true,
        previewJson = StremioJson.encodeToString(
            StremioMeta.serializer(),
            StremioMeta(id = record.itemId, type = record.itemType, name = record.title, poster = record.posterUrl),
        ),
    )

    fun seeAll(row: BoardRow) = NavRoutes.AddonCatalog(
        addonId = row.addon.id,
        type = row.catalog.type,
        catalogId = row.catalog.id,
        extraJson = CatalogViewModel.extraJson(row.request.extra),
    )
}

/**
 * Discover home (PRD §5): Continue watching, type tabs, one lazily loaded row per board catalogue.
 * On TV it fills the home content area; [initialFocus] then focuses the first card.
 */
@Composable
fun DiscoverContent(
    viewModel: DiscoverViewModel,
    onNavigate: (NavRoutes.RootRoutes) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    initialFocus: FocusRequester? = null,
    /** TV: bump to move focus into the content (restores the last focused card, else the first one). */
    focusToken: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    // TV: the last focused card, so Back from a detail page lands on the card that opened it.
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val cardFocus: (String) -> Modifier = { key ->
        Modifier.onFocusChanged { if (it.isFocused) focusedKey = key }
            .then(if (key == focusedKey) Modifier.focusRequester(restoreFocus) else Modifier)
    }
    val query by viewModel.query.collectAsState()
    val search by viewModel.search.collectAsState()
    val isTv = LocalIsTvMode.current
    val horizontal = if (isTv) 32.dp else 16.dp

    LaunchedEffect(state.rows.map { it.key }) { viewModel.onRowsChanged(state.rows) }
    LaunchedEffect(focusToken) {
        if (!isTv || (focusToken == 0 && focusedKey == null)) return@LaunchedEffect
        // Rows load asynchronously: retry until a card exists, then fall back to the search button.
        repeat(40) {
            val restored = focusedKey != null && restoreFocus.requestFocusAfterLayout()
            if (restored || initialFocus?.requestFocusAfterLayout() == true) return@LaunchedEffect
            delay(50)
        }
        searchFocus.requestFocusAfterLayout()
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + if (isTv) TvDefaults.overscanVertical else 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 16.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = horizontal), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.discover_title),
                    color = TSColors.TextPrimary,
                    fontSize = if (isTv) 26.sp else 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isTv) {
                    // TV: a focusable search button that opens the search screen with the keyboard.
                    PillButton(
                        text = stringResource(Res.string.discover_search_hint),
                        onClick = { onNavigate(NavRoutes.DiscoverSearch()) },
                        primary = false,
                        icon = Icons.Rounded.Search,
                        modifier = Modifier.focusRequester(searchFocus).then(cardFocus("search")),
                    )
                } else {
                    TSTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(Res.string.discover_search_hint), color = TSColors.TextSecondary) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TSColors.TextSecondary) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
            }
        }
        if (!isTv && query.trim().length >= DiscoverViewModel.MIN_QUERY) {
            searchResults(search, horizontal, onNavigate)
            return@LazyColumn
        }
        if (state.isOffline) {
            item(key = "offline") {
                Row(Modifier.padding(horizontal = horizontal), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = TSColors.LoadingYellow)
                    Text(stringResource(Res.string.discover_offline), color = TSColors.LoadingYellow)
                }
            }
        }
        if (state.failedAddons > 0 && !state.isOffline) {
            item(key = "partial") {
                PartialFailure(state.failedAddons, Modifier.padding(horizontal = horizontal), onRetry = viewModel::retryFailed)
            }
        }
        if (state.continueWatching.isNotEmpty()) {
            item(key = "cw") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddonSectionTitle(stringResource(Res.string.continue_watching_media), Modifier.padding(horizontal = horizontal))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                    ) {
                        itemsIndexed(state.continueWatching, key = { _, r -> "cw" + r.id }) { index, record ->
                            PosterCard(
                                meta = StremioMeta(id = record.itemId, type = record.itemType, name = record.title ?: record.itemId, poster = record.posterUrl),
                                subtitle = record.subtitle,
                                progress = if (record.durationMs > 0) record.positionMs.toFloat() / record.durationMs else null,
                                width = if (isTv) 140.dp else 110.dp,
                                modifier = (if (index == 0 && initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier)
                                    .then(cardFocus("cw|" + record.id))
                                    .onPreviewKeyEvent { event ->
                                        // TV: the menu key removes the title from Continue watching (PRD §9).
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                                            viewModel.removeFromHistory(record); true
                                        } else false
                                    },
                                onClick = { onNavigate(DiscoverNav.continueWatching(record)) },
                            )
                        }
                    }
                }
            }
        }
        if (state.isOffline && state.rows.isNotEmpty() && state.rowStates.values.none { it is RowState.Loaded }) {
            return@LazyColumn
        }
        if (state.types.size > 1) {
            item(key = "types") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontal),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { AddonChip(stringResource(Res.string.discover_type_all), state.selectedType == null, { viewModel.selectType(null) }) }
                    items(state.types) { type ->
                        AddonChip(addonTypeLabel(type), state.selectedType == type, { viewModel.selectType(type) })
                    }
                }
            }
        }
        val rows = state.visibleRows
        if (rows.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(Res.string.discover_empty_catalog), Modifier.padding(horizontal = horizontal), color = TSColors.TextSecondary)
            }
        }
        rows.forEachIndexed { rowIndex, row ->
            item(key = row.key) {
                BoardRowView(
                    row = row,
                    state = state.rowStates[row.key],
                    horizontal = horizontal,
                    onLoad = { viewModel.loadRow(row) },
                    onSeeAll = { onNavigate(DiscoverNav.seeAll(row)) },
                    onOpen = { meta -> onNavigate(DiscoverNav.detail(row.addon, row.catalog.type, meta)) },
                    firstCardFocus = if (rowIndex == 0 && state.continueWatching.isEmpty()) initialFocus else null,
                    cardFocus = cardFocus,
                )
            }
        }
    }
}

@Composable
private fun PartialFailure(count: Int, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(TSColors.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.addon_partial_failure, count), Modifier.weight(1f).padding(start = 4.dp), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
        PillButton(stringResource(Res.string.addon_retry), onClick = onRetry, primary = false)
    }
}

/** Row title "{catalog name} · {addon name}". */
@Composable
fun catalogTitle(catalog: ManifestCatalog, addon: InstalledAddon): String = "${catalog.displayName} · ${addon.name}"

@Composable
private fun BoardRowView(
    row: BoardRow,
    state: RowState?,
    horizontal: androidx.compose.ui.unit.Dp,
    onLoad: () -> Unit,
    onSeeAll: () -> Unit,
    onOpen: (StremioMeta) -> Unit,
    firstCardFocus: FocusRequester?,
    cardFocus: (String) -> Modifier = { Modifier },
) {
    val isTv = LocalIsTvMode.current
    // Rows load lazily as they enter composition (LazyColumn composes only visible rows).
    LaunchedEffect(row.key) { onLoad() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AddonSectionTitle(catalogTitle(row.catalog, row.addon), Modifier.padding(horizontal = horizontal)) {
            PillButton(stringResource(Res.string.discover_see_all), onClick = onSeeAll, primary = false)
        }
        when (state) {
            is RowState.Loaded -> if (state.items.isEmpty()) {
                Text(stringResource(Res.string.discover_empty_catalog), Modifier.padding(horizontal = horizontal), color = TSColors.TextSecondary, fontSize = 14.sp)
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                ) {
                    itemsIndexed(state.items, key = { _, m -> m.id.orEmpty() }) { index, meta ->
                        PosterCard(
                            meta = meta,
                            width = if (isTv) 140.dp else 110.dp,
                            modifier = (if (index == 0 && firstCardFocus != null) Modifier.focusRequester(firstCardFocus) else Modifier)
                                .then(cardFocus(row.key + "|" + meta.id)),
                            onClick = { onOpen(meta) },
                        )
                    }
                }
            }
            RowState.Failed -> Text(stringResource(Res.string.discover_empty_catalog), Modifier.padding(horizontal = horizontal), color = TSColors.TextSecondary, fontSize = 14.sp)
            else -> Box(Modifier.fillMaxWidth().height(if (isTv) 220.dp else 180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = TSColors.AccentCyan)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(
    search: SearchState?,
    horizontal: androidx.compose.ui.unit.Dp,
    onNavigate: (NavRoutes.RootRoutes) -> Unit,
    /** Attached to the first result card (search screen: ▼ / Search key from the field). */
    firstResultFocus: FocusRequester? = null,
    /** ▲ from the first result row (search screen: back to the field). */
    onUpFromFirstRow: (() -> Boolean)? = null,
) {
    if (search == null) {
        item(key = "search_wait") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = TSColors.AccentCyan)
            }
        }
        return
    }
    if (search.failedAddons > 0) {
        item(key = "search_partial") {
            Text(stringResource(Res.string.addon_partial_failure, search.failedAddons), Modifier.padding(horizontal = horizontal), color = TSColors.TextSecondary, fontSize = 14.sp)
        }
    }
    search.groups.forEachIndexed { groupIndex, group ->
        item(key = "s" + group.key) {
            val isTv = LocalIsTvMode.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AddonSectionTitle(catalogTitle(group.catalog, group.addon), Modifier.padding(horizontal = horizontal))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                ) {
                    itemsIndexed(group.items, key = { _, m -> m.id.orEmpty() }) { index, meta ->
                        val firstRow = groupIndex == 0
                        PosterCard(
                            meta,
                            width = if (isTv) 140.dp else 110.dp,
                            modifier = (if (firstRow && index == 0 && firstResultFocus != null) Modifier.focusRequester(firstResultFocus) else Modifier)
                                .then(
                                    if (firstRow && onUpFromFirstRow != null) Modifier.onPreviewKeyEvent { event ->
                                        // Consumed only if focus moved back to the field.
                                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && onUpFromFirstRow()
                                    } else Modifier
                                ),
                            onClick = { onNavigate(DiscoverNav.detail(group.addon, group.catalog.type, meta)) },
                        )
                    }
                }
            }
        }
    }
    if (!search.isDone) {
        item(key = "search_more") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = TSColors.AccentCyan)
            }
        }
    } else if (search.groups.isEmpty()) {
        item(key = "search_none") {
            Text(stringResource(Res.string.discover_no_results), Modifier.padding(horizontal = horizontal), color = TSColors.TextSecondary)
        }
    }
}

/** Search screen: `stremio:///search` links and the TV search button. The field gets focus (keyboard). */
@Composable
fun DiscoverSearchScreen(
    viewModel: DiscoverViewModel,
    initialQuery: String,
    onNavigate: (NavRoutes.RootRoutes) -> Unit,
    onBack: () -> Unit,
) {
    val session = viewModel.screenSearch
    val query by session.query.collectAsState()
    val search by session.results.collectAsState()
    val isTv = LocalIsTvMode.current
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val horizontal = if (isTv) TvDefaults.overscanHorizontal else 16.dp
    /**
     * The Boolean overload tells whether focus moved (the first card may not exist yet, e.g. Search
     * pressed before the debounce produced results, or it is scrolled out of its row).
     */
    fun focusFirstResult(): Boolean =
        firstResult.requestFocus(FocusDirection.Enter) || focusManager.moveFocus(FocusDirection.Down)
    LaunchedEffect(initialQuery) {
        session.setQuery(initialQuery)
        field.requestFocusAfterLayout()
    }
    Box(Modifier.fillMaxSize().background(TSColors.backgroundGradientMain)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(vertical = if (isTv) TvDefaults.overscanVertical else 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "field") {
                Row(Modifier.padding(horizontal = horizontal), verticalAlignment = Alignment.CenterVertically) {
                    if (!isTv) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TSColors.TextPrimary)
                        }
                    }
                    TSTextField(
                        modifier = Modifier.weight(1f).focusRequester(field).onPreviewKeyEvent { event ->
                            // A text field keeps ▲/▼ for its cursor: without this, ▼ never leaves it (TV).
                            // Consume ▼ only when focus actually moved; otherwise the field keeps it.
                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && focusFirstResult()
                        },
                        value = query,
                        onValueChange = session::setQuery,
                        placeholder = { Text(stringResource(Res.string.discover_search_hint), color = TSColors.TextSecondary) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TSColors.TextSecondary) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusFirstResult() }),
                    )
                }
            }
            if (query.trim().length >= DiscoverViewModel.MIN_QUERY) {
                searchResults(
                    search, horizontal, onNavigate,
                    firstResultFocus = firstResult,
                    onUpFromFirstRow = { field.requestFocus(FocusDirection.Enter) },
                )
            }
        }
    }
}
