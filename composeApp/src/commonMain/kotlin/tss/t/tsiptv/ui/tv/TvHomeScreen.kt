package tss.t.tsiptv.ui.tv

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.all_channels_title
import tsiptv.composeapp.generated.resources.app_name
import tsiptv.composeapp.generated.resources.bottom_sheet_import_playlist
import tsiptv.composeapp.generated.resources.channel_count_format
import tsiptv.composeapp.generated.resources.continue_watching
import tsiptv.composeapp.generated.resources.empty_iptv_source_title
import tsiptv.composeapp.generated.resources.settings_title
import tsiptv.composeapp.generated.resources.tv_import_playlist_hint
import tsiptv.composeapp.generated.resources.tv_no_channels
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.themes.TSColors

/**
 * Home for the TV layout: a category rail on the left and a channel grid on the
 * right, navigated entirely with the D-pad. Uses the same [HomeUiState] and
 * [HomeEvent]s as the phone home, so both layouts share one HomeViewModel.
 * Works signed out; [signedInEmail] is null until the user logs in from Settings.
 */
@Composable
fun TvHomeScreen(
    homeUiState: HomeUiState,
    totalPlaylist: List<PlaylistWithChannelCount>,
    playingChannelId: String?,
    onHomeEvent: (HomeEvent) -> Unit,
    onImportPlaylist: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    signedInEmail: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRateApp: (() -> Unit)? = null,
) {
    var showSettings by remember { mutableStateOf(false) }
    val railFocus = remember { FocusRequester() }
    val focusTargetRequester = remember { FocusRequester() }
    // Returning from the player puts the remote back on the channel that is
    // playing (the user may have zapped away from the one opened), not at the
    // top of the rail. Saved with the back stack entry.
    var lastOpenedId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusTargetId = playingChannelId ?: lastOpenedId
    val gridState = rememberLazyGridState()
    val railState = rememberLazyListState()
    // HomeViewModel's init asks for history before playListId is set, so it
    // loads nothing; the phone feed re-requests it on entry, and so must this.
    LaunchedEffect(homeUiState.playListId) {
        if (homeUiState.playListId != null) onHomeEvent(HomeEvent.LoadHistory)
    }

    // A new category starts at the top of the grid. Compared against a saved id
    // rather than keyed on first composition, so coming back from the player
    // keeps the restored scroll position.
    var shownCategoryId by rememberSaveable { mutableStateOf(homeUiState.selectedCategory?.id) }
    LaunchedEffect(homeUiState.selectedCategory?.id) {
        val id = homeUiState.selectedCategory?.id
        if (id != shownCategoryId) {
            shownCategoryId = id
            gridState.scrollToItem(0)
        }
    }
    val hasPlaylist = homeUiState.playListId != null

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TSColors.backgroundGradientMain)
    ) {
        CategoryRail(
            homeUiState = homeUiState,
            listState = railState,
            selectedFocus = railFocus,
            onHomeEvent = onHomeEvent,
            onSettings = { showSettings = true },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when {
                homeUiState.isLoading && homeUiState.listChannels.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = TSColors.AccentCyan
                    )
                }

                !hasPlaylist -> EmptyPlaylist(onImportPlaylist)

                else -> ChannelGrid(
                    homeUiState = homeUiState,
                    gridState = gridState,
                    playingChannelId = playingChannelId,
                    focusTargetId = focusTargetId,
                    focusTarget = focusTargetRequester,
                    onOpenChannel = {
                        lastOpenedId = it.id
                        onHomeEvent(HomeEvent.OnOpenVideoPlayer(it))
                    },
                )
            }
        }
    }

    if (showSettings) {
        TvSettingsDialog(
            homeUiState = homeUiState,
            totalPlaylist = totalPlaylist,
            onHomeEvent = onHomeEvent,
            onImportPlaylist = onImportPlaylist,
            onOpenLanguageSettings = onOpenLanguageSettings,
            signedInEmail = signedInEmail,
            onLogin = onLogin,
            onLogout = onLogout,
            onDismissRequest = { showSettings = false },
            onRateApp = onRateApp,
        )
    }

    // A TV has no pointer: without an initial focus the first D-pad press lands
    // nowhere visible. Start on the selected category.
    // With no playlist, EmptyPlaylist takes focus on its import button instead.
    LaunchedEffect(hasPlaylist) {
        if (!hasPlaylist) return@LaunchedEffect
        val restored = focusTargetId != null && focusTargetRequester.requestFocusAfterLayout()
        if (!restored) {
            // The selected category may be scrolled out of the rail; bring its
            // row into composition first or there is nothing to focus.
            val selectedIndex = homeUiState.categories
                .indexOfFirst { it.id == homeUiState.selectedCategory?.id } + 1
            railState.scrollToItem(selectedIndex)
            railFocus.requestFocusAfterLayout()
        }
    }
}

@Composable
private fun CategoryRail(
    homeUiState: HomeUiState,
    listState: LazyListState,
    selectedFocus: FocusRequester,
    onHomeEvent: (HomeEvent) -> Unit,
    onSettings: () -> Unit,
) {
    val selectedId = homeUiState.selectedCategory?.id

    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(TSColors.SecondaryBackgroundColor.copy(alpha = 0.7f))
            .padding(
                start = TvDefaults.overscanHorizontal / 2,
                end = 12.dp,
                top = TvDefaults.overscanVertical,
                bottom = TvDefaults.overscanVertical
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.LiveTv,
                contentDescription = null,
                tint = TSColors.AccentGreen,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    color = TSColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                homeUiState.playListName?.let {
                    Text(
                        text = it,
                        color = TSColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item(key = "all") {
                TvMenuItem(
                    title = stringResource(Res.string.all_channels_title),
                    icon = Icons.Rounded.Apps,
                    selected = selectedId == null,
                    modifier = if (selectedId == null) Modifier.focusRequester(selectedFocus) else Modifier,
                    onClick = { onHomeEvent(HomeEvent.OnClearFilterCategory) }
                )
            }
            items(homeUiState.categories, key = { it.id }) { category ->
                val isSelected = category.id == selectedId
                TvMenuItem(
                    title = category.name,
                    icon = Icons.Rounded.Folder,
                    selected = isSelected,
                    modifier = if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier,
                    onClick = { onHomeEvent(HomeEvent.OnCategorySelected(category)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TvMenuItem(
            title = stringResource(Res.string.settings_title),
            icon = Icons.Rounded.Settings,
            onClick = onSettings
        )
    }
}

@Composable
private fun ChannelGrid(
    homeUiState: HomeUiState,
    gridState: LazyGridState,
    playingChannelId: String?,
    focusTargetId: String?,
    focusTarget: FocusRequester,
    onOpenChannel: (Channel) -> Unit,
) {
    val showHistory = homeUiState.selectedCategory == null &&
            homeUiState.allPlayedChannels.isNotEmpty()
    val history = homeUiState.allPlayedChannels.take(12)
    // One requester, one owner: the history row comes first, so if the channel
    // is in both the row claims it and the grid card does not.
    val targetInHistory = showHistory && history.any { it.channelId == focusTargetId }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 32.dp,
            end = TvDefaults.overscanHorizontal,
            top = TvDefaults.overscanVertical,
            bottom = TvDefaults.overscanVertical
        ),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (showHistory) {
            item(key = "history_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(Res.string.continue_watching))
            }
            item(key = "history_row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    // Room for the focus scale so the first card is not clipped.
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)
                ) {
                    items(history, key = { it.channelId }) { item ->
                        HistoryCard(
                            history = item,
                            isPlaying = item.channelId == playingChannelId,
                            modifier = if (item.channelId == focusTargetId) {
                                Modifier.focusRequester(focusTarget)
                            } else {
                                Modifier
                            },
                            onClick = { onOpenChannel(item.getChannel()) }
                        )
                    }
                }
            }
        }

        item(key = "channels_title", span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(
                title = homeUiState.selectedCategory?.name
                    ?: stringResource(Res.string.all_channels_title),
                subtitle = stringResource(
                    Res.string.channel_count_format,
                    homeUiState.listChannels.size
                )
            )
        }

        if (homeUiState.listChannels.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(Res.string.tv_no_channels),
                    color = TSColors.TextSecondary,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 48.dp)
                )
            }
        }

        items(homeUiState.listChannels, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                isPlaying = channel.id == playingChannelId,
                modifier = if (channel.id == focusTargetId && !targetInHistory) {
                    Modifier.focusRequester(focusTarget)
                } else {
                    Modifier
                },
                onClick = { onOpenChannel(channel) }
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            color = TSColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (subtitle != null) {
            Spacer(Modifier.width(12.dp))
            Text(text = subtitle, color = TSColors.TextSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) { focused ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    modifier = Modifier.fillMaxSize().padding(14.dp)
                )
                if (isPlaying) PlayingBadge(Modifier.align(Alignment.TopEnd))
            }
            Text(
                text = channel.name,
                color = if (focused) TSColors.TextPrimary else TSColors.TextSecondaryLight,
                fontSize = 16.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun HistoryCard(
    history: ChannelWithHistory,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.width(260.dp),
    ) { focused ->
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 56.dp)
                    .clip(TvDefaults.cardShape)
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(
                    name = history.channelName,
                    logoUrl = history.logoUrl,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = history.channelName,
                color = if (focused) TSColors.TextPrimary else TSColors.TextSecondaryLight,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = TSColors.AccentGreen
                )
            }
        }
    }
}

@Composable
private fun PlayingBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(TvDefaults.cardShape)
            .background(TSColors.AccentGreen)
            .padding(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = TSColors.DeepBlue,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Channel logo, falling back to the channel's initials when there is none or it fails. */
@Composable
internal fun ChannelLogo(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    val fallback: @Composable () -> Unit = {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = name.split(' ', '-', '_')
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercase() },
                color = TSColors.TextSecondaryLight,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
    if (logoUrl.isNullOrBlank()) {
        fallback()
        return
    }
    SubcomposeAsyncImage(
        model = logoUrl,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        error = { fallback() },
    )
}

@Composable
private fun EmptyPlaylist(onImportPlaylist: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.LiveTv,
            contentDescription = null,
            tint = TSColors.AccentCyan,
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(
                        listOf(TSColors.AccentCyan.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.empty_iptv_source_title),
            color = TSColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.tv_import_playlist_hint),
            color = TSColors.TextSecondary,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(32.dp))
        TvMenuItem(
            title = stringResource(Res.string.bottom_sheet_import_playlist),
            icon = Icons.Rounded.FileDownload,
            onClick = onImportPlaylist,
            modifier = Modifier
                .width(320.dp)
                .focusRequester(focus)
        )
    }
    LaunchedEffect(Unit) {
        focus.requestFocusAfterLayout()
    }
}
