package tss.t.tsiptv.ui.screens.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.discover_see_all
import tsiptv.composeapp.generated.resources.source_about
import tsiptv.composeapp.generated.resources.settings_title
import tsiptv.composeapp.generated.resources.source_channels_tab
import tsiptv.composeapp.generated.resources.source_home_tab
import tsiptv.composeapp.generated.resources.source_no_content
import tsiptv.composeapp.generated.resources.source_refresh
import tsiptv.composeapp.generated.resources.source_search_empty
import tsiptv.composeapp.generated.resources.source_search_hint
import tsiptv.composeapp.generated.resources.source_section_channels
import tsiptv.composeapp.generated.resources.source_section_continue
import tsiptv.composeapp.generated.resources.source_section_movies
import tsiptv.composeapp.generated.resources.source_section_radio
import tsiptv.composeapp.generated.resources.source_section_series
import tss.t.tsiptv.core.language.LocalAppLocale
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvBuiltInTitle
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardKind
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardResolver
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardStyle
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSection
import tss.t.tsiptv.core.parser.tsiptv.TsiptvSectionType
import tss.t.tsiptv.core.stremio.StremioMeta
import tss.t.tsiptv.core.tsiptv.BuiltSection
import tss.t.tsiptv.core.tsiptv.SourceBanner
import tss.t.tsiptv.core.tsiptv.SourceItem
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.addons.AddonChip
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.screens.addons.PosterCard
import tss.t.tsiptv.ui.screens.discover.DiscoverNav
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvDefaults
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.ui.widgets.TSTextField

/** What Source Home can ask of its host (the phone Home tab or the TV home). */
class SourceHomeActions(
    /** Plays a channel; [zapList] = the section's channels (TV zapping, AC-T19). */
    val onPlayChannel: (channel: Channel, zapList: List<Channel>) -> Unit,
    val onNavigate: (NavRoutes.RootRoutes) -> Unit,
    val onOpenAbout: () -> Unit,
    val onRefresh: () -> Unit,
    /** Phone: the Home settings sheet (switch playlist, import, ...), in the overflow menu. */
    val onOpenSettings: (() -> Unit)? = null,
)

/** Group filter and loaded pages of a grid (kept outside the lazy items). */
@Stable
class GridUiState {
    var group by mutableStateOf<String?>(null)
    var pages by mutableIntStateOf(1)
}

/**
 * Source Home (PRD F3 §4): the source's layout (or the default one), with its appearance.
 * Phone, TV and desktop share it; TV adds D-pad focus rules (initial focus on the hero, focus
 * restore per row and after Back).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SourceHomeContent(
    playlistId: String,
    viewModel: SourceHomeViewModel,
    actions: SourceHomeActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Phone: the Home/Channels switch, drawn above the layout. */
    header: (@Composable () -> Unit)? = null,
    /** TV: bump to move focus into the content (restores the last focused card, else the first one). */
    focusToken: Int = 0,
) {
    val uiLanguage = LocalAppLocale.current
    LaunchedEffect(playlistId, uiLanguage) { viewModel.open(playlistId, uiLanguage) }
    val state by viewModel.state.collectAsState()
    val isTv = LocalIsTvMode.current
    val horizontal = if (isTv) TvDefaults.overscanHorizontal else 16.dp
    val grids = remember(playlistId) { HashMap<String, GridUiState>() }
    val appearanceCard = state.effective.card

    // TV: the last focused card, so Back from a detail page lands on the card that opened it.
    var focusedKey by rememberSaveable(playlistId) { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val cardFocus: (String) -> Modifier = { key ->
        Modifier.onFocusChanged { if (it.isFocused) focusedKey = key }
            .then(if (key == focusedKey) Modifier.focusRequester(restoreFocus) else Modifier)
    }
    LaunchedEffect(focusToken, state.loading) {
        if (!isTv || state.loading || focusToken == 0 && focusedKey == null && header != null) return@LaunchedEffect
        repeat(30) {
            if (focusedKey != null && restoreFocus.requestFocusAfterLayout()) return@LaunchedEffect
            if (firstFocus.requestFocusAfterLayout()) return@LaunchedEffect
            delay(50)
        }
    }

    SourceBackground(state.effective, modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = maxWidth - horizontal * 2
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + if (isTv) TvDefaults.overscanVertical else 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 16.dp),
            ) {
                header?.let { item(key = "switch") { it() } }
                item(key = "header") {
                    SourceHeader(
                        name = state.meta?.name?.resolve(uiLanguage).orEmpty(),
                        query = state.query,
                        onQuery = viewModel::setQuery,
                        actions = actions,
                        horizontal = horizontal,
                        searchFocus = if (state.sections.isEmpty()) firstFocus else null,
                    )
                }
                if (state.query.isNotBlank()) {
                    searchResults(state.results, uiLanguage, horizontal, actions, playlistId, cardFocus)
                    return@LazyColumn
                }
                if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LocalSourceColors.current.accent)
                        }
                    }
                    return@LazyColumn
                }
                if (state.sections.isEmpty()) {
                    item(key = "empty") {
                        Text(stringResource(Res.string.source_no_content), Modifier.padding(horizontal = horizontal), color = TSColors.TextSecondary)
                    }
                }
                state.sections.forEachIndexed { index, built ->
                    val first = if (index == 0) firstFocus else null
                    when {
                        built.section.type == TsiptvSectionType.HERO -> item(key = built.key) {
                            HeroSection(built, uiLanguage, horizontal, first, cardFocus) {
                                open(it.target, it.autoplay, built, viewModel, actions, playlistId)
                            }
                        }

                        built.catalog != null -> item(key = built.key) {
                            CatalogRow(built, viewModel, state.catalogs[built.key], uiLanguage, horizontal, first, cardFocus, actions)
                        }

                        built.section.type == TsiptvSectionType.GRID -> gridSection(
                            built = built,
                            grid = grids.getOrPut(built.key) { GridUiState() },
                            appearanceCard = appearanceCard,
                            uiLanguage = uiLanguage,
                            horizontal = horizontal,
                            contentWidth = contentWidth,
                            isTv = isTv,
                            firstFocus = first,
                            cardFocus = cardFocus,
                        ) { open(it, false, built, viewModel, actions, playlistId) }

                        else -> item(key = built.key) {
                            RowSection(
                                built, uiLanguage, horizontal, first, cardFocus, viewModel,
                                onSeeAll = {
                                    actions.onNavigate(NavRoutes.SourceSeeAll(playlistId, built.key, built.section.title?.resolve(uiLanguage).orEmpty()))
                                },
                            ) { open(it, false, built, viewModel, actions, playlistId) }
                        }
                    }
                }
            }
        }
    }
}

/** Opens an item: channel → player; movie/series → detail; episode → series detail; history → resume. */
internal fun open(
    item: SourceItem?,
    autoplay: Boolean,
    built: BuiltSection?,
    viewModel: SourceHomeViewModel,
    actions: SourceHomeActions,
    playlistId: String,
) {
    when (item) {
        null -> Unit
        is SourceItem.ChannelItem -> actions.onPlayChannel(
            item.channel,
            built?.let { viewModel.sectionChannels(it.key) }.orEmpty().ifEmpty { listOf(item.channel) },
        )

        is SourceItem.Vod -> actions.onNavigate(
            NavRoutes.MediaDetail(
                type = if (item.item.isSeries) "series" else "movie",
                id = item.item.itemId,
                videoId = if (autoplay && !item.item.isSeries) item.item.itemId else null,
                openStreams = autoplay && !item.item.isSeries,
                sourcePlaylistId = playlistId,
                autoplay = autoplay && !item.item.isSeries,
            )
        )

        is SourceItem.Episode -> actions.onNavigate(
            NavRoutes.MediaDetail(
                type = "series",
                id = item.series.itemId,
                videoId = item.episodeId,
                openStreams = autoplay,
                sourcePlaylistId = playlistId,
                autoplay = autoplay,
            )
        )

        is SourceItem.Continue -> actions.onNavigate(DiscoverNav.sourceContinueWatching(item.record))
    }
}

@Composable
internal fun sectionTitle(section: TsiptvSection, uiLanguage: String?): String {
    section.title?.let { return it.resolve(uiLanguage) }
    return when (section.builtInTitle) {
        TsiptvBuiltInTitle.CONTINUE_WATCHING -> stringResource(Res.string.source_section_continue)
        TsiptvBuiltInTitle.MOVIES -> stringResource(Res.string.source_section_movies)
        TsiptvBuiltInTitle.SERIES -> stringResource(Res.string.source_section_series)
        TsiptvBuiltInTitle.RADIO -> stringResource(Res.string.source_section_radio)
        TsiptvBuiltInTitle.CHANNELS -> stringResource(Res.string.source_section_channels)
        null -> ""
    }
}

@Composable
private fun SourceHeader(
    name: String,
    query: String,
    onQuery: (String) -> Unit,
    actions: SourceHomeActions,
    horizontal: Dp,
    searchFocus: FocusRequester?,
) {
    val isTv = LocalIsTvMode.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = horizontal), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                Modifier.weight(1f),
                color = TSColors.TextPrimary,
                fontSize = if (isTv) 26.sp else 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isTv) {
                IconButton(onClick = { searching = !searching; if (!searching) onQuery("") }) {
                    Icon(
                        if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = stringResource(Res.string.source_search_hint),
                        tint = TSColors.TextPrimary,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.source_about), tint = TSColors.TextPrimary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.source_about)) }, onClick = { menu = false; actions.onOpenAbout() })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.source_refresh)) }, onClick = { menu = false; actions.onRefresh() })
                        actions.onOpenSettings?.let { open ->
                            DropdownMenuItem(text = { Text(stringResource(Res.string.settings_title)) }, onClick = { menu = false; open() })
                        }
                    }
                }
            }
        }
        if (isTv && !searching) {
            PillButton(
                stringResource(Res.string.source_search_hint),
                onClick = { searching = true },
                primary = false,
                icon = Icons.Rounded.Search,
                modifier = if (searchFocus != null) Modifier.focusRequester(searchFocus) else Modifier,
            )
        }
        if (searching) {
            val searchFieldFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { searchFieldFocus.requestFocusAfterLayout() }
            TSTextField(
                modifier = Modifier.focusRequester(searchFieldFocus),
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(Res.string.source_search_hint), color = TSColors.TextSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TSColors.TextSecondary) },
            )
        }
    }
}

private fun LazyListScope.searchResults(
    results: List<SourceItem>,
    uiLanguage: String?,
    horizontal: Dp,
    actions: SourceHomeActions,
    playlistId: String,
    cardFocus: (String) -> Modifier,
) {
    if (results.isEmpty()) {
        item(key = "no_results") {
            Text(stringResource(Res.string.source_search_empty), Modifier.padding(horizontal = horizontal), color = TSColors.TextSecondary)
        }
        return
    }
    item(key = "results") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(results, key = { _, it -> it.key }) { _, item ->
                SourceCard(
                    item, resolveCard(null, item), uiLanguage,
                    modifier = cardFocus("search|" + item.key),
                    onClick = {
                        when (item) {
                            is SourceItem.ChannelItem -> actions.onPlayChannel(item.channel, listOf(item.channel))
                            is SourceItem.Vod -> actions.onNavigate(
                                NavRoutes.MediaDetail(if (item.item.isSeries) "series" else "movie", item.item.itemId, sourcePlaylistId = playlistId)
                            )
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(section: TsiptvSection, uiLanguage: String?, horizontal: Dp, trailing: (@Composable () -> Unit)? = null) {
    val title = sectionTitle(section, uiLanguage)
    val subtitle = section.subtitle?.resolve(uiLanguage)
    if (title.isEmpty() && subtitle == null && trailing == null) return
    Row(Modifier.fillMaxWidth().padding(horizontal = horizontal), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (title.isNotEmpty()) {
                Text(
                    title, color = TSColors.TextPrimary, fontSize = if (LocalIsTvMode.current) 20.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            subtitle?.let { Text(it, color = TSColors.TextSecondaryLight, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
    }
}

/** A `row` (spec §7.1): title, optional subtitle, See all, one horizontal line of cards. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowSection(
    built: BuiltSection,
    uiLanguage: String?,
    horizontal: Dp,
    firstFocus: FocusRequester?,
    cardFocus: (String) -> Modifier,
    viewModel: SourceHomeViewModel,
    onSeeAll: () -> Unit,
    onOpen: (SourceItem) -> Unit,
) {
    val isTv = LocalIsTvMode.current
    val showSeeAll = built.section.seeAll && built.section.type == TsiptvSectionType.ROW
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(built.section, uiLanguage, horizontal) {
            if (showSeeAll && !isTv) PillButton(stringResource(Res.string.discover_see_all), onClick = onSeeAll, primary = false)
        }
        LazyRow(
            // TV: ▼ then ▲ comes back to the card that had focus in this row.
            modifier = Modifier.focusRestorer(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(built.items, key = { _, it -> it.key }) { index, item ->
                val card = resolveCard(built.section.card, item)
                val programme = if (card.style == TsiptvCardKind.LIST) currentProgramme(item, viewModel) else null
                SourceCard(
                    item, card, uiLanguage,
                    onClick = { onOpen(item) },
                    modifier = (if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                        .then(cardFocus(built.key + "|" + item.key)),
                    programme = programme,
                )
            }
            // TV: See all is the last focusable card of the row.
            if (showSeeAll && isTv) {
                item(key = "see_all") {
                    PillButton(
                        stringResource(Res.string.discover_see_all), onClick = onSeeAll, primary = false,
                        modifier = cardFocus(built.key + "|see_all"),
                    )
                }
            }
        }
    }
}

@Composable
private fun currentProgramme(item: SourceItem, viewModel: SourceHomeViewModel): String? {
    val channel = (item as? SourceItem.ChannelItem)?.channel ?: return null
    val title by produceState<String?>(null, channel.id) { value = viewModel.currentProgramme(channel) }
    return title
}

/**
 * A `grid` (spec §7.1): all matching items in pages of 60 rows of cards (one lazy item per row, so
 * thousands of channels stay cheap), with optional group chips. Columns: phone adaptive (poster
 * ≥ 110 dp, landscape ≥ 170 dp, square/logo ≥ 96 dp, list one column); TV fixed (poster 6,
 * landscape 4, square/logo 6, list 2).
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun LazyListScope.gridSection(
    built: BuiltSection,
    grid: GridUiState,
    appearanceCard: TsiptvCardStyle?,
    uiLanguage: String?,
    horizontal: Dp,
    contentWidth: Dp,
    isTv: Boolean,
    firstFocus: FocusRequester?,
    cardFocus: (String) -> Modifier,
    onOpen: (SourceItem) -> Unit,
) {
    val groups = if (!built.section.groupChips) emptyList()
    else built.items.flatMap { (it as? SourceItem.ChannelItem)?.channel?.groups.orEmpty() }.distinctBy { it.lowercase() }
    val selected = grid.group
    val filtered = if (selected == null) built.items
    else built.items.filter { (it as? SourceItem.ChannelItem)?.channel?.groups?.any { g -> g.equals(selected, ignoreCase = true) } == true }
    val shown = filtered.take(PAGE * grid.pages)
    val style = shown.firstOrNull()?.let { TsiptvCardResolver.resolve(built.section.card, appearanceCard, cardKindOf(it)).style } ?: TsiptvCardKind.POSTER
    val columns = if (isTv) when (style) {
        TsiptvCardKind.LANDSCAPE -> 4
        TsiptvCardKind.LIST -> 2
        else -> 6
    } else when (style) {
        TsiptvCardKind.LIST -> 1
        TsiptvCardKind.LANDSCAPE -> (contentWidth / 170.dp).toInt().coerceAtLeast(1)
        TsiptvCardKind.SQUARE, TsiptvCardKind.LOGO -> (contentWidth / 96.dp).toInt().coerceAtLeast(1)
        else -> (contentWidth / 110.dp).toInt().coerceAtLeast(1)
    }
    val spacing = if (isTv) 20.dp else 10.dp
    val cellWidth = (contentWidth - spacing * (columns - 1)) / columns
    val rows = shown.chunked(columns)

    item(key = built.key + "|head") { SectionHeading(built.section, uiLanguage, horizontal) }
    if (groups.isNotEmpty()) {
        item(key = built.key + "|chips") {
            LazyRow(
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = horizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(groups) { index, name ->
                    AddonChip(
                        name,
                        selected = selected?.equals(name, ignoreCase = true) == true,
                        onClick = {
                            grid.group = if (selected?.equals(name, ignoreCase = true) == true) null else name
                            grid.pages = 1
                        },
                        modifier = (if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(cardFocus(built.key + "|chip|" + name)),
                    )
                }
            }
        }
    }
    rows.forEachIndexed { rowIndex, rowItems ->
        item(key = built.key + "|row|" + rowIndex + "|" + (selected ?: "")) {
            Row(Modifier.padding(horizontal = horizontal), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowItems.forEachIndexed { col, item ->
                    val focusFirst = rowIndex == 0 && col == 0 && groups.isEmpty() && firstFocus != null
                    SourceCard(
                        item, resolveCard(built.section.card, item), uiLanguage,
                        onClick = { onOpen(item) },
                        width = cellWidth,
                        modifier = (if (focusFirst) Modifier.focusRequester(firstFocus!!) else Modifier)
                            .then(cardFocus(built.key + "|" + item.key)),
                    )
                }
            }
            // Paged loading: the next page once the last row of this page is composed.
            if (rowIndex == rows.lastIndex && shown.size < filtered.size) {
                LaunchedEffect(grid.pages) { grid.pages++ }
            }
        }
    }
}

private const val PAGE = 60

/** A `from: "catalog"` row from the Stremio include's addon (spec §9.3); hidden when it loads empty. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CatalogRow(
    built: BuiltSection,
    viewModel: SourceHomeViewModel,
    state: CatalogRowState?,
    uiLanguage: String?,
    horizontal: Dp,
    firstFocus: FocusRequester?,
    cardFocus: (String) -> Modifier,
    actions: SourceHomeActions,
) {
    val ref = built.catalog ?: return
    LaunchedEffect(built.key) { viewModel.loadCatalog(built.key, ref) }
    val isTv = LocalIsTvMode.current
    when (state) {
        null, CatalogRowState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = LocalSourceColors.current.accent)
        }

        CatalogRowState.Failed -> Unit
        is CatalogRowState.Loaded -> if (state.items.isNotEmpty()) {
            val limit = built.section.effectiveLimit ?: state.items.size
            // Row `seeAll` (default true): the whole catalogue in F2's catalogue screen.
            val showSeeAll = built.section.seeAll && built.section.type == TsiptvSectionType.ROW
            val seeAll = {
                actions.onNavigate(
                    NavRoutes.AddonCatalog(
                        addonId = state.addonId,
                        type = ref.type,
                        catalogId = ref.catalogId,
                        extraJson = tss.t.tsiptv.ui.screens.discover.CatalogViewModel.extraJson(listOfNotNull(ref.genre?.let { "genre" to it })),
                    )
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading(built.section, uiLanguage, horizontal) {
                    if (showSeeAll && !isTv) PillButton(stringResource(Res.string.discover_see_all), onClick = seeAll, primary = false)
                }
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                ) {
                    itemsIndexed(state.items.take(limit), key = { _, m -> m.id.orEmpty() }) { index, meta ->
                        PosterCard(
                            meta = meta,
                            width = if (isTv) 140.dp else 110.dp,
                            modifier = (if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                                .then(cardFocus(built.key + "|" + meta.id)),
                            onClick = { actions.onNavigate(catalogDetail(state.addonId, ref.type, meta)) },
                        )
                    }
                    if (showSeeAll && isTv) {
                        item(key = "see_all") {
                            PillButton(
                                stringResource(Res.string.discover_see_all), onClick = seeAll, primary = false,
                                modifier = cardFocus(built.key + "|see_all"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** F2 detail backed by the include's addon (PRD F3 §4 "Selecting an item"). */
private fun catalogDetail(addonId: String, type: String, meta: StremioMeta): NavRoutes.MediaDetail =
    DiscoverNav.detail(null, type, meta).copy(addonId = addonId)

/**
 * A `hero` (spec §7.1, PRD §4): phone — a 16:9 pager with dots that advances every 6 s unless
 * touched; TV — a banner (at most 45 % of the screen height) where ◀/▶ change banners and OK
 * opens the target; decorative banners (no target) are skipped by focus.
 */
@Composable
private fun HeroSection(
    built: BuiltSection,
    uiLanguage: String?,
    horizontal: Dp,
    firstFocus: FocusRequester?,
    cardFocus: (String) -> Modifier,
    onOpen: (SourceBanner) -> Unit,
) {
    val banners = built.banners
    if (banners.isEmpty()) return
    if (LocalIsTvMode.current) TvHero(built.key, banners, uiLanguage, horizontal, firstFocus, cardFocus, onOpen)
    else PhoneHero(banners, uiLanguage, horizontal, onOpen)
}

@Composable
private fun PhoneHero(banners: List<SourceBanner>, uiLanguage: String?, horizontal: Dp, onOpen: (SourceBanner) -> Unit) {
    val pager = rememberPagerState { banners.size }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(pager, touched, banners.size) {
        if (touched || banners.size < 2) return@LaunchedEffect
        while (true) {
            delay(HERO_ADVANCE_MS)
            pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
        }
    }
    Column(Modifier.padding(horizontal = horizontal), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                // Paused while touched.
                .pointerInput(Unit) { detectDragGestures(onDragStart = { touched = true }) { _, _ -> } },
        ) { page ->
            val banner = banners[page]
            SourceFocusSurface(onClick = { if (banner.target != null) onOpen(banner) }, modifier = Modifier.fillMaxSize(), focusedScale = 1f) {
                BannerContent(banner, uiLanguage)
            }
        }
        if (banners.size > 1) {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                banners.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) LocalSourceColors.current.accent else TSColors.White.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvHero(
    key: String,
    banners: List<SourceBanner>,
    uiLanguage: String?,
    horizontal: Dp,
    firstFocus: FocusRequester?,
    cardFocus: (String) -> Modifier,
    onOpen: (SourceBanner) -> Unit,
) {
    val targeted = banners.indices.filter { banners[it].target != null }
    var index by rememberSaveable(key) { mutableIntStateOf(targeted.firstOrNull() ?: 0) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Auto-advance only while not focused.
    LaunchedEffect(focused, banners.size) {
        if (focused || banners.size < 2) return@LaunchedEffect
        while (true) {
            delay(HERO_ADVANCE_MS)
            index = (index + 1) % banners.size
        }
    }
    // While focused, stay on a banner that has a target.
    LaunchedEffect(focused) { if (focused && index !in targeted) targeted.firstOrNull()?.let { index = it } }
    val banner = banners[index.coerceIn(banners.indices)]
    val accent = LocalSourceColors.current.accent
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val height = (screenHeight * 0.45f).coerceAtLeast(160.dp)
    val focusModifier = if (targeted.isEmpty()) Modifier else Modifier
        .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
        .then(cardFocus("$key|hero"))
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> targeted.lastOrNull { it < index }?.let { index = it; true } ?: false
                Key.DirectionRight -> targeted.firstOrNull { it > index }?.let { index = it; true } ?: false
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    banners[index].takeIf { it.target != null }?.let(onOpen)
                    true
                }
                else -> false
            }
        }
        .focusable(interactionSource = interaction)
    Box(Modifier.fillMaxWidth().padding(horizontal = horizontal)) {
        Box(
            Modifier.height(height).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .border(width = if (focused) 3.dp else 0.dp, color = if (focused) accent else Color.Transparent, shape = RoundedCornerShape(16.dp))
                .then(focusModifier),
        ) {
            BannerContent(banner, uiLanguage)
        }
    }
}

@Composable
private fun BannerContent(banner: SourceBanner, uiLanguage: String?) {
    Box(Modifier.fillMaxSize()) {
        CardImage(banner.image, banner.title?.resolve(uiLanguage).orEmpty(), Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            banner.title?.let {
                Text(it.resolve(uiLanguage), color = TSColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            banner.subtitle?.let {
                Text(it.resolve(uiLanguage), color = TSColors.TextSecondaryLight, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val HERO_ADVANCE_MS = 6_000L

/** "See all" of a row: the same query as a full grid without the section limit (PRD §4). */
@Composable
fun SourceSeeAllContent(
    route: NavRoutes.SourceSeeAll,
    viewModel: SourceHomeViewModel,
    actions: SourceHomeActions,
    onBack: () -> Unit,
) {
    val uiLanguage = LocalAppLocale.current
    LaunchedEffect(route.playlistId, uiLanguage) { viewModel.open(route.playlistId, uiLanguage) }
    val state by viewModel.state.collectAsState()
    val built = state.sections.firstOrNull { it.key == route.sectionKey }
    val items = remember(built, state.loading) { if (built == null) emptyList() else viewModel.seeAll(route.sectionKey) }
    val isTv = LocalIsTvMode.current
    val horizontal = if (isTv) TvDefaults.overscanHorizontal else 16.dp
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val restore = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val grid = remember(route.sectionKey) { GridUiState() }
    val cardFocus: (String) -> Modifier = { key ->
        Modifier.onFocusChanged { if (it.isFocused) focusedKey = key }
            .then(if (key == focusedKey) Modifier.focusRequester(restore) else Modifier)
    }
    LaunchedEffect(items.size) {
        if (!isTv || items.isEmpty()) return@LaunchedEffect
        if (focusedKey != null && restore.requestFocusAfterLayout()) return@LaunchedEffect
        first.requestFocusAfterLayout()
    }
    val title = built?.let { sectionTitle(it.section, uiLanguage) } ?: route.title
    SourceBackground(state.effective) {
        BoxWithConstraints(Modifier.fillMaxSize().then(if (isTv) Modifier else Modifier.statusBarsPadding())) {
            val contentWidth = maxWidth - horizontal * 2
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = if (isTv) TvDefaults.overscanVertical else 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "title") {
                    Row(Modifier.padding(horizontal = horizontal), verticalAlignment = Alignment.CenterVertically) {
                        if (!isTv) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TSColors.TextPrimary)
                            }
                        }
                        Text(title, color = TSColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (built != null) {
                    val asGrid = built.copy(
                        items = items,
                        section = built.section.copy(type = TsiptvSectionType.GRID, title = null, subtitle = null, builtInTitle = null),
                    )
                    gridSection(asGrid, grid, state.effective.card, uiLanguage, horizontal, contentWidth, isTv, first, cardFocus) {
                        open(it, false, built, viewModel, actions, route.playlistId)
                    }
                }
            }
        }
    }
}

/** Phone: the Home / Channels switch of a source with channels (PRD §4). */
@Composable
fun SourceHomeSwitch(showChannels: Boolean, onChange: (showChannels: Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AddonChip(stringResource(Res.string.source_home_tab), selected = !showChannels, onClick = { onChange(false) })
        AddonChip(stringResource(Res.string.source_channels_tab), selected = showChannels, onClick = { onChange(true) })
    }
}
