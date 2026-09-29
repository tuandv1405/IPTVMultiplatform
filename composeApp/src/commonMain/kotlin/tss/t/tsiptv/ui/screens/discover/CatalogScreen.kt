package tss.t.tsiptv.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.addons.AddonChip
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.screens.addons.PosterCard
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvDefaults
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.addon_partial_failure
import tsiptv.composeapp.generated.resources.addon_retry
import tsiptv.composeapp.generated.resources.discover_empty_catalog

/** Catalogue screen ("See all", `stremio:///discover/…`): genre chips + paged grid (PRD §5). */
@Composable
fun CatalogScreen(route: NavRoutes.AddonCatalog, onNavigate: (NavRoutes.RootRoutes) -> Unit, onBack: () -> Unit) {
    val viewModel = koinViewModel<CatalogViewModel>()
    LaunchedEffect(route) { viewModel.open(route.addonId, route.type, route.catalogId, route.extraJson) }
    val state by viewModel.state.collectAsState()
    val isTv = LocalIsTvMode.current
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val horizontal = if (isTv) TvDefaults.overscanHorizontal else 16.dp

    // Load the next page when the grid is within ~2 rows of its end.
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && !state.loading && !state.failed) viewModel.loadMore() }
    // TV: the last focused card/chip survives the trip to a detail page (saveable), so Back lands
    // on it. The initial focus runs once per visit (not saveable): a genre change reloads the
    // grid while focus must stay on the chip.
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    val trackFocus: (String) -> Modifier = { key ->
        Modifier.onFocusChanged { if (it.isFocused) focusedKey = key }
            .then(if (key == focusedKey) Modifier.focusRequester(restoreFocus) else Modifier)
    }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (isTv && !initialFocusDone && state.items.isNotEmpty()) {
            initialFocusDone = (focusedKey != null && restoreFocus.requestFocusAfterLayout()) ||
                firstFocus.requestFocusAfterLayout()
        }
    }

    Box(Modifier.fillMaxSize().background(TSColors.backgroundGradientMain)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (isTv) horizontal else 4.dp, vertical = if (isTv) TvDefaults.overscanVertical else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isTv) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TSColors.TextPrimary)
                    }
                }
                val title = state.catalog?.let { c -> state.addon?.let { catalogTitle(c, it) } }.orEmpty()
                Text(title, color = TSColors.TextPrimary, fontSize = if (isTv) 24.sp else 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.genreOptions.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontal, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.genreOptions) { option ->
                        AddonChip(
                            option,
                            option in state.selectedGenres,
                            onClick = { viewModel.toggleGenre(option) },
                            modifier = trackFocus("genre|$option"),
                        )
                    }
                }
            }
            val shape = state.items.firstOrNull()?.resolvedPosterShape ?: "poster"
            val minCell = when (shape) {
                "landscape" -> if (isTv) 240.dp else 180.dp
                "square" -> if (isTv) 160.dp else 120.dp
                else -> if (isTv) 140.dp else 108.dp
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minCell),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = horizontal, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
            ) {
                itemsIndexed(state.items, key = { _, m -> m.id.orEmpty() }) { index, meta ->
                    PosterCard(
                        meta = meta,
                        width = minCell,
                        modifier = (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(trackFocus("card|" + meta.id)),
                        onClick = { onNavigate(DiscoverNav.detail(state.addon, route.type, meta)) },
                    )
                }
                // No stable key (QC F3 r2): with one, the grid stayed anchored on this footer when the
                // first page was inserted above it and opened near the end of that page.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.size(28.dp), color = TSColors.AccentCyan)
                            state.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(Res.string.addon_partial_failure, 1), color = TSColors.TextSecondary)
                                PillButton(stringResource(Res.string.addon_retry), onClick = viewModel::loadMore)
                            }
                            state.notFound || (state.endReached && state.items.isEmpty()) ->
                                Text(stringResource(Res.string.discover_empty_catalog), color = TSColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
