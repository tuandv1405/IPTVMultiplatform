package tss.t.tsiptv.ui.screens.mediadetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tss.t.tsiptv.core.stremio.ClassifiedStream
import tss.t.tsiptv.core.stremio.MediaRules
import tss.t.tsiptv.core.stremio.StreamGroup
import tss.t.tsiptv.core.stremio.StreamKind
import tss.t.tsiptv.core.stremio.StremioVideo
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.addons.AddonChip
import tss.t.tsiptv.ui.screens.addons.AddonDialog
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.screens.addons.PosterImage
import tss.t.tsiptv.ui.screens.addons.posterAspect
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvDefaults
import tss.t.tsiptv.ui.tv.TvFocusableSurface
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.utils.LocalAppViewModelStoreOwner
import tss.t.tsiptv.utils.PlatformUtils
import tss.t.tsiptv.utils.getUrlOpener
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.source_stream_default
import tsiptv.composeapp.generated.resources.addon_more
import tsiptv.composeapp.generated.resources.addon_partial_failure
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.meta_not_found
import tsiptv.composeapp.generated.resources.meta_play
import tsiptv.composeapp.generated.resources.meta_rating
import tsiptv.composeapp.generated.resources.meta_season
import tsiptv.composeapp.generated.resources.meta_specials
import tsiptv.composeapp.generated.resources.meta_upcoming
import tsiptv.composeapp.generated.resources.player_back
import tsiptv.composeapp.generated.resources.stream_badge_may_not_play
import tsiptv.composeapp.generated.resources.stream_badge_region
import tsiptv.composeapp.generated.resources.stream_external_tv
import tsiptv.composeapp.generated.resources.stream_none_playable
import tsiptv.composeapp.generated.resources.stream_open
import tsiptv.composeapp.generated.resources.stream_open_external
import tsiptv.composeapp.generated.resources.stream_opens_browser
import tsiptv.composeapp.generated.resources.stream_picker_title
import tsiptv.composeapp.generated.resources.stream_show_unsupported
import tsiptv.composeapp.generated.resources.stream_unsupported

/** Meta detail (PRD §6) with the stream picker (§7). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaDetailScreen(
    route: NavRoutes.MediaDetail,
    onNavigate: (NavRoutes.RootRoutes) -> Unit,
    onPlay: (mediaItemId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<MediaDetailViewModel>()
    val playerViewModel = koinViewModel<PlayerViewModel>(viewModelStoreOwner = LocalAppViewModelStoreOwner.current!!)
    val uiLanguage = tss.t.tsiptv.core.language.LocalAppLocale.current
    LaunchedEffect(route, uiLanguage) {
        val sourcePlaylistId = route.sourcePlaylistId
        if (sourcePlaylistId != null) {
            // F3: a TS IPTV Source movie/series; unnamed streams are "Source N".
            val labels = (1..tss.t.tsiptv.core.parser.tsiptv.TsiptvLimits.MAX_STREAMS).map {
                org.jetbrains.compose.resources.getString(Res.string.source_stream_default, it)
            }
            viewModel.openSource(sourcePlaylistId, route.id, route.videoId, route.openStreams, uiLanguage, labels)
        } else {
            viewModel.open(route.type, route.id, route.addonId, route.videoId, route.openStreams, route.previewJson)
        }
    }
    val state by viewModel.state.collectAsState()
    // Every (re)entry, e.g. Back from the player: pick up the episode that was just watched.
    LaunchedEffect(Unit) { viewModel.refreshLastWatched() }
    val isTv = LocalIsTvMode.current
    val scope = rememberCoroutineScope()
    var confirmExternal by remember { mutableStateOf<StreamKind.ExternalBrowser?>(null) }
    var showUrl by remember { mutableStateOf<String?>(null) }

    fun choose(group: StreamGroup, row: ClassifiedStream) {
        when (val kind = row.kind) {
            is StreamKind.Playable -> scope.launch {
                val (item, playback) = viewModel.playbackFor(group, row) ?: return@launch
                viewModel.closeStreams()
                playerViewModel.playStream(item, playback)
                onPlay(item.id)
            }
            is StreamKind.ExternalBrowser -> confirmExternal = kind
            is StreamKind.Internal -> viewModel.routeFor(kind.url)?.let { viewModel.closeStreams(); onNavigate(it) }
            is StreamKind.Unsupported -> Unit
        }
    }

    BackHandler(enabled = state.picker != null) { viewModel.closeStreams() }

    // F3 hero `autoplay` (spec §7.3): the first playable stream plays directly, once.
    var autoplayed by rememberSaveable(route) { mutableStateOf(!route.autoplay) }
    val picker = state.picker
    LaunchedEffect(picker?.loaded, autoplayed) {
        if (autoplayed || picker == null || !picker.loaded) return@LaunchedEffect
        val target = picker.streams.groups.firstNotNullOfOrNull { group ->
            viewModel.visible(group, showUnsupported = false).firstOrNull { it.kind is StreamKind.Playable }?.let { group to it }
        }
        autoplayed = true
        target?.let { (group, row) -> choose(group, row) }
    }

    tss.t.tsiptv.ui.screens.source.SourceAppearanceScope(route.sourcePlaylistId) { sourceApplied ->
    Box(Modifier.fillMaxSize().then(if (sourceApplied) Modifier else Modifier.background(TSColors.BackgroundColor))) {
        when {
            state.loading && state.meta == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = TSColors.AccentCyan)
            state.notFound || state.meta == null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.meta_not_found), color = TSColors.TextSecondaryLight, fontSize = 16.sp)
                val focus = remember { FocusRequester() }
                PillButton(stringResource(Res.string.player_back), onClick = onBack, modifier = Modifier.focusRequester(focus))
                LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
            }
            else -> DetailBody(state, viewModel, isTv, onBack, onNavigate)
        }

        // Stream picker: a right side panel on TV, a bottom sheet elsewhere.
        if (isTv) {
            // A focusable popup: the D-pad cannot leave the panel (◀ used to land on the page behind),
            // and Back closes it.
            state.picker?.let { picker ->
                Popup(
                    alignment = Alignment.CenterEnd,
                    onDismissRequest = viewModel::closeStreams,
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier.width(460.dp).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(TSColors.DeepBlue.copy(alpha = 0.9f), TSColors.DeepBlue.copy(alpha = 0.98f))))
                            .padding(start = 16.dp, end = TvDefaults.overscanHorizontal / 2, top = TvDefaults.overscanVertical, bottom = TvDefaults.overscanVertical),
                    ) {
                        StreamPickerContent(picker, viewModel, isTv = true, onChoose = ::choose, onBack = viewModel::closeStreams)
                    }
                }
            }
        } else {
            state.picker?.let { picker -> PickerSheet(picker, viewModel, ::choose) }
        }
    }
    }

    confirmExternal?.let { external ->
        AddonDialog(title = null, onDismissRequest = { confirmExternal = null }) {
            Text(stringResource(Res.string.stream_open_external, external.host), color = TSColors.TextPrimary, fontSize = 16.sp)
            val focus = remember { FocusRequester() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                PillButton(stringResource(Res.string.cancel), onClick = { confirmExternal = null }, primary = false)
                PillButton(stringResource(Res.string.stream_open), modifier = Modifier.focusRequester(focus), onClick = {
                    confirmExternal = null
                    scope.launch {
                        val opener = getUrlOpener()
                        // TV without a browser: show the link as text (PRD "TV").
                        if (!opener.openUrl(external.url)) showUrl = external.url
                    }
                })
            }
            LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
        }
    }
    showUrl?.let { url ->
        AddonDialog(title = null, onDismissRequest = { showUrl = null }) {
            Text(stringResource(Res.string.stream_external_tv), color = TSColors.TextSecondaryLight)
            SelectionContainer { Text(url, color = TSColors.TextPrimary, fontSize = 14.sp) }
            val focus = remember { FocusRequester() }
            PillButton(stringResource(Res.string.player_back), onClick = { showUrl = null }, modifier = Modifier.focusRequester(focus))
            LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
        }
    }
}

@Composable
private fun DetailBody(
    state: MediaDetailUiState,
    viewModel: MediaDetailViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    onNavigate: (NavRoutes.RootRoutes) -> Unit,
) {
    val meta = state.meta ?: return
    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    val lastEpisodeFocus = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }
    val season = state.seasons.getOrNull(state.selectedSeason)
    val horizontal = if (isTv) TvDefaults.overscanHorizontal else 16.dp
    var expanded by remember { mutableStateOf(false) }
    val lastVideoId = state.lastWatched?.videoId

    // Not keyed on the selected season: choosing a season chip must not pull focus into the episodes.
    LaunchedEffect(meta.id, state.isSeries, state.loading, lastVideoId) {
        if (state.loading) return@LaunchedEffect
        // Initial position: the last watched episode (scrolled into view on every platform), and on
        // TV the focus: that episode, else the first season chip (series), else Play.
        val focusedEpisode = state.isSeries && season?.episodes?.any { it.id == lastVideoId } == true
        if (focusedEpisode) {
            val index = season!!.episodes.indexOfFirst { it.id == lastVideoId }
            listState.scrollToItem(3 + index)
            if (isTv) lastEpisodeFocus.requestFocusAfterLayout()
        } else if (isTv) {
            val focused = state.isSeries && state.seasons.isNotEmpty() && seasonFocus.requestFocusAfterLayout()
            if (!focused) playFocus.requestFocusAfterLayout()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth()) {
                PosterImage(meta.background ?: meta.poster, "", Modifier.fillMaxWidth().aspectRatio(if (isTv) 3.2f else 1.6f))
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), TSColors.BackgroundColor))
                    )
                )
                if (!isTv) {
                    IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TSColors.TextPrimary)
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = horizontal, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    PosterImage(
                        meta.poster, meta.displayName,
                        Modifier.width(if (isTv) 140.dp else 96.dp).aspectRatio(posterAspect(meta.resolvedPosterShape)).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(meta.displayName, color = TSColors.TextPrimary, fontSize = if (isTv) 28.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val facts = listOfNotNull(meta.releaseInfo ?: meta.year, meta.runtime, meta.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() })
                        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
                        meta.imdbRating?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(Res.string.meta_rating, it), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = horizontal), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.failedAddons > 0) {
                    Text(stringResource(Res.string.addon_partial_failure, state.failedAddons), color = TSColors.TextSecondary, fontSize = 13.sp)
                }
                state.playVideo?.let { video ->
                    PillButton(
                        stringResource(Res.string.meta_play),
                        onClick = { viewModel.openStreams(video) },
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.focusRequester(playFocus),
                    )
                }
                meta.description?.takeIf { it.isNotBlank() }?.let { description ->
                    TvFocusableSurface(onClick = { expanded = !expanded }, color = Color.Transparent, focusedScale = 1.01f, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            description,
                            modifier = Modifier.padding(4.dp),
                            color = TSColors.TextSecondaryLight,
                            fontSize = 15.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Only stremio:/// links are handled; all other links are not shown.
                val links = meta.links.orEmpty().filter { it.isInternal && viewModel.routeFor(it.url) != null }
                if (links.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(links) { _, link ->
                            AddonChip(link.name.orEmpty(), selected = false, onClick = { viewModel.routeFor(link.url)?.let(onNavigate) })
                        }
                    }
                }
            }
        }
        if (state.isSeries && state.seasons.isNotEmpty()) {
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontal, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.seasons) { index, group ->
                        val label = when {
                            group.isSpecials -> stringResource(Res.string.meta_specials)
                            group.season == null -> "—"
                            else -> stringResource(Res.string.meta_season, group.season)
                        }
                        AddonChip(
                            label,
                            selected = index == state.selectedSeason,
                            onClick = { viewModel.selectSeason(index) },
                            modifier = if (index == state.selectedSeason) Modifier.focusRequester(seasonFocus) else Modifier,
                        )
                    }
                }
            }
            season?.episodes.orEmpty().forEach { video ->
                item(key = "ep_" + video.id) {
                    EpisodeRow(
                        video = video,
                        upcoming = MediaRules.isUpcoming(video, state.nowMs),
                        lastWatched = video.id == lastVideoId,
                        horizontal = horizontal,
                        modifier = if (video.id == lastVideoId) Modifier.focusRequester(lastEpisodeFocus) else Modifier,
                        onClick = { viewModel.openStreams(video) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    video: StremioVideo,
    upcoming: Boolean,
    lastWatched: Boolean,
    horizontal: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TvFocusableSurface(
        onClick = { if (!upcoming) onClick() },
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
        color = if (lastWatched) TSColors.AccentCyan.copy(alpha = 0.12f) else TSColors.SecondaryBackgroundColor,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterImage(video.thumbnail, video.displayTitle, Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(video.episodeNumber?.toString(), video.displayTitle.takeIf { it.isNotBlank() }).joinToString(". "),
                    color = if (upcoming) TSColors.TextSecondary else TSColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val released = video.released?.take(10)
                Text(
                    listOfNotNull(released, if (upcoming) stringResource(Res.string.meta_upcoming) else null).joinToString(" · "),
                    color = if (upcoming) TSColors.LoadingYellow else TSColors.TextSecondary,
                    fontSize = 12.sp,
                )
                video.synopsis?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = TSColors.TextSecondaryLight, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(picker: StreamPickerState, viewModel: MediaDetailViewModel, onChoose: (StreamGroup, ClassifiedStream) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = viewModel::closeStreams,
        sheetState = sheetState,
        containerColor = TSColors.SecondaryBackgroundColor,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            StreamPickerContent(picker, viewModel, isTv = false, onChoose = onChoose, onBack = viewModel::closeStreams)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StreamPickerContent(
    picker: StreamPickerState,
    viewModel: MediaDetailViewModel,
    isTv: Boolean,
    onChoose: (StreamGroup, ClassifiedStream) -> Unit,
    onBack: () -> Unit,
) {
    val streams = picker.streams
    var overflow by remember { mutableStateOf(false) }
    val preferredFocus = remember { FocusRequester() }
    val fallbackFocus = remember { FocusRequester() }
    val isIos = PlatformUtils.platform.isIOS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.stream_picker_title), Modifier.weight(1f), color = TSColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (!isTv) {
            Box {
                IconButton(onClick = { overflow = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.addon_more), tint = TSColors.TextPrimary)
                }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.stream_show_unsupported)) },
                        trailingIcon = { if (picker.showUnsupported) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = { overflow = false; viewModel.toggleUnsupported() },
                    )
                }
            }
        }
    }
    picker.video.displayTitle.takeIf { it.isNotBlank() }?.let {
        Text(it, color = TSColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))
    if (streams.failedAddons > 0) {
        Text(stringResource(Res.string.addon_partial_failure, streams.failedAddons), color = TSColors.TextSecondary, fontSize = 13.sp)
    }
    // Only after the first answer: before it, "no playable stream" would flash while requests start.
    val noneYet = picker.loaded && !streams.loading && !streams.hasPlayable
    LazyColumn(
        Modifier.fillMaxWidth().then(if (isTv) Modifier.weight(1f, fill = false) else Modifier.height(480.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Flattened index over the visible rows, matching StreamPickerState.preferredIndex.
        var flatIndex = 0
        streams.groups.forEach { group ->
            val rows = viewModel.visible(group, picker.showUnsupported)
            if (group.loading || rows.isNotEmpty()) {
                item(key = "g|" + group.addonId) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(group.addon?.name.orEmpty(), color = TSColors.TextSecondaryLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (group.loading) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(14.dp), strokeWidth = 2.dp, color = TSColors.AccentCyan)
                    }
                }
            }
            rows.forEach { row ->
                val index = flatIndex++
                item(key = "s|" + group.addonId + "|" + index) {
                    StreamRow(
                        row = row,
                        isIos = isIos,
                        modifier = when {
                            index == picker.preferredIndex -> Modifier.focusRequester(preferredFocus)
                            index == 0 -> Modifier.focusRequester(fallbackFocus)
                            else -> Modifier
                        },
                        onClick = { onChoose(group, row) },
                    )
                }
            }
        }
        if (noneYet) {
            item(key = "none") {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.stream_none_playable), color = TSColors.TextSecondaryLight, fontSize = 15.sp)
                    PillButton(stringResource(Res.string.player_back), onClick = onBack, modifier = Modifier.focusRequester(fallbackFocus))
                }
            }
        }
        if (isTv) {
            item(key = "more") {
                // TV overflow: a focusable More button with "Show unsupported streams".
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(Res.string.addon_more), onClick = { overflow = !overflow }, primary = false, icon = Icons.Rounded.MoreVert)
                    if (overflow) {
                        AddonChip(stringResource(Res.string.stream_show_unsupported), picker.showUnsupported, onClick = viewModel::toggleUnsupported)
                    }
                }
            }
        }
    }
    LaunchedEffect(picker.preferredIndex, streams.loading) {
        if (!isTv) return@LaunchedEffect
        if (picker.preferredIndex >= 0) preferredFocus.requestFocusAfterLayout() else if (!streams.loading) fallbackFocus.requestFocusAfterLayout()
    }
}

@Composable
private fun StreamRow(row: ClassifiedStream, isIos: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val unsupported = !row.isSelectable
    TvFocusableSurface(
        onClick = { if (!unsupported) onClick() },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
        color = if (unsupported) TSColors.White.copy(alpha = 0.03f) else TSColors.White.copy(alpha = 0.07f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (unsupported) {
                    // No kind label and no addon text for unsupported rows (policy "No torrent advertising").
                    Text(stringResource(Res.string.stream_unsupported), color = TSColors.TextSecondary, fontSize = 14.sp)
                } else {
                    Text(row.name.ifBlank { "—" }, color = TSColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    row.description?.let { Text(it, color = TSColors.TextSecondaryLight, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    val badges = listOfNotNull(
                        stringResource(Res.string.stream_badge_region).takeIf { row.isRegionLimited },
                        stringResource(Res.string.stream_badge_may_not_play).takeIf { isIos && row.mayNotPlayOnIos },
                    )
                    if (badges.isNotEmpty()) Text(badges.joinToString(" · "), color = TSColors.LoadingYellow, fontSize = 12.sp)
                }
            }
            if (row.kind is StreamKind.ExternalBrowser) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(Res.string.stream_opens_browser), tint = TSColors.TextSecondaryLight)
            }
        }
    }
}

