package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.all_channels_title
import tsiptv.composeapp.generated.resources.continue_watching
import tss.t.tsiptv.ui.screens.ads.AdsViewModel
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.home.widget.HomeChannelHistoryItem
import tss.t.tsiptv.ui.screens.home.widget.HomeChannelItem
import tss.t.tsiptv.ui.screens.home.widget.NowPlayingCard
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.core.ads.AdPlacement
import tss.t.tsiptv.core.ads.AdsPolicy
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.ui.ads.NativeAdSlot
import tss.t.tsiptv.ui.screens.ads.rememberShopeeFallback

/** Key of the list item the sticky Home banner sits on. */
const val HOME_BANNER_ITEM_KEY = "HomeBannerAd"

/** Gap between channel cards, across and down. */
private val HomeGridSpacing = 16.dp

/** One line of the Home channel list: up to `columns` channels side by side, or a full-width ad. */
sealed interface HomeGridRow {
    /** List key. It starts with "ch:" or "ad:" like the single-column rows (the sticky banner looks for those). */
    val key: String

    data class Channels(val items: List<AdsPolicy.Row.Item<Channel>>) : HomeGridRow {
        override val key: String get() = items.first().let { "ch:" + it.index + ":" + it.item.id }
    }

    data class Ad(val ad: AdsPolicy.Row.Ad) : HomeGridRow {
        override val key: String get() = "ad:" + ad.slot
    }
}

/**
 * [rows] laid out [columns] channels per line. A native ad keeps a line of its own, so the line
 * before it may be shorter.
 */
fun homeGridRows(rows: List<AdsPolicy.Row<Channel>>, columns: Int): List<HomeGridRow> {
    val perLine = columns.coerceAtLeast(1)
    val lines = ArrayList<HomeGridRow>(rows.size / perLine + 1)
    var pending = ArrayList<AdsPolicy.Row.Item<Channel>>(perLine)
    for (row in rows) {
        when (row) {
            is AdsPolicy.Row.Item -> {
                pending.add(row)
                if (pending.size == perLine) {
                    lines.add(HomeGridRow.Channels(pending))
                    pending = ArrayList(perLine)
                }
            }

            is AdsPolicy.Row.Ad -> {
                if (pending.isNotEmpty()) {
                    lines.add(HomeGridRow.Channels(pending))
                    pending = ArrayList(perLine)
                }
                lines.add(HomeGridRow.Ad(row))
            }
        }
    }
    if (pending.isNotEmpty()) lines.add(HomeGridRow.Channels(pending))
    return lines
}

/**
 * Channel cards per line for a list [width] wide: one on phones; on desktop two, or three once
 * the window is wide enough (a single card stretched across a desktop window was too long).
 */
fun homeGridColumns(width: Dp, isDesktop: Boolean): Int = when {
    !isDesktop || width < 640.dp -> 1
    width < 1100.dp -> 2
    else -> 3
}

fun LazyListScope.homeItemList(
    adsViewModel: AdsViewModel,
    /** Channels with native ad slots (PRD R5): [AdsPolicy.interleave] of `homeUiState.listChannels`. */
    channelRows: List<AdsPolicy.Row<Channel>>,
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    categoryListState: LazyListState,
    /** Height of the sticky banner overlay (0 when it shows nothing). */
    bannerSpace: Dp,
    /** Cards per line, from [homeGridColumns]. */
    columns: Int = 1,
    onHomeEvent: (HomeEvent) -> Unit,
) {
    if (homeUiState.nowPlayingChannel != null) {
        item("NowWatchingCard") {
            NowPlayingCard(
                channelWithHistory = homeUiState.nowPlayingChannel,
                currentProgram = homeUiState.currentProgram,
                modifier = Modifier.Companion.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp),
                isPlaying = playerUIState.isPlaying,
                onHomeEvent = onHomeEvent
            )
        }
    }

    if (homeUiState.top3MostPlayedChannels.isNotEmpty() &&
        homeUiState.searchText.trim().isEmpty()
    ) {
        item("HistoryTitle") {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.continue_watching),
                modifier = Modifier.Companion.fillMaxWidth()
                    .padding(horizontal = 16.dp),
                style = TSTextStyles.semiBold17
            )
        }

        if (columns > 1) {
            val lines = homeUiState.top3MostPlayedChannels.chunked(columns)
            items(lines.size, key = { "HistoryLine:$it" }) { line ->
                GridLine(columns, lines[line].size, Modifier.padding(top = 12.dp)) {
                    lines[line].forEach { channel ->
                        HomeChannelHistoryItem(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            channel = channel,
                            onItemClick = {
                                onHomeEvent(HomeEvent.OnOpenVideoPlayer(it))
                            }
                        )
                    }
                }
            }
        } else {
            items(homeUiState.top3MostPlayedChannels) {
                HomeChannelHistoryItem(
                    modifier = Modifier.Companion.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    channel = it,
                    onItemClick = {
                        onHomeEvent(HomeEvent.OnOpenVideoPlayer(it))
                    }
                )
            }
        }
    }

    item("AllChannelTitle") {
        Text(
            text = stringResource(Res.string.all_channels_title),
            modifier = Modifier.Companion.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp),
            style = TSTextStyles.semiBold17
        )
    }

    item("GroupChannelsTitle") {
        CategoryRow(
            homeUiState = homeUiState,
            modifier = Modifier.Companion.fillMaxWidth()
                .padding(vertical = 16.dp),
            onHomeEvent = onHomeEvent,
            listState = categoryListState
        )
    }

    // PRD R3: room for the sticky Home banner. The banner itself is one overlay in HomeFeedScreen
    // (a single AdView for the screen) that sits here and stays pinned under the category chips.
    item(HOME_BANNER_ITEM_KEY) {
        Spacer(Modifier.fillMaxWidth().height(bannerSpace))
    }

    if (columns > 1) {
        homeChannelGrid(adsViewModel, homeGridRows(channelRows, columns), columns, onHomeEvent)
        return
    }

    items(
        count = channelRows.size,
        key = { index ->
            when (val row = channelRows[index]) {
                is AdsPolicy.Row.Item -> "ch:" + row.index + ":" + row.item.id
                is AdsPolicy.Row.Ad -> "ad:" + row.slot
            }
        },
        contentType = { index -> if (channelRows[index] is AdsPolicy.Row.Ad) "ad" else "channel" },
    ) { index ->
        when (val row = channelRows[index]) {
            is AdsPolicy.Row.Item -> {
                HomeChannelItem(
                    channel = row.item,
                    modifier = Modifier.Companion.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    onHomeEvent(HomeEvent.OnOpenVideoPlayer(it))
                }
                Spacer(Modifier.Companion.height(16.dp))
            }

            // PRD R5: a native ad that looks like a channel row (Shopee fallback, else nothing).
            is AdsPolicy.Row.Ad -> {
                NativeAdSlot(
                    placement = AdPlacement.HOME_NATIVE,
                    slot = row.slot,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    fallback = rememberShopeeFallback(adsViewModel, row.slot),
                )
            }
        }
    }
}

private fun LazyListScope.homeChannelGrid(
    adsViewModel: AdsViewModel,
    lines: List<HomeGridRow>,
    columns: Int,
    onHomeEvent: (HomeEvent) -> Unit,
) {
    items(
        count = lines.size,
        key = { lines[it].key },
        contentType = { if (lines[it] is HomeGridRow.Ad) "ad" else "channelLine" },
    ) { index ->
        when (val line = lines[index]) {
            is HomeGridRow.Channels -> {
                GridLine(columns, line.items.size, Modifier.padding(bottom = HomeGridSpacing)) {
                    line.items.forEach { row ->
                        HomeChannelItem(
                            channel = row.item,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            onHomeEvent(HomeEvent.OnOpenVideoPlayer(it))
                        }
                    }
                }
            }

            is HomeGridRow.Ad -> {
                NativeAdSlot(
                    placement = AdPlacement.HOME_NATIVE,
                    slot = line.ad.slot,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    fallback = rememberShopeeFallback(adsViewModel, line.ad.slot),
                )
            }
        }
    }
}

/**
 * Cards side by side, all as tall as the tallest. A short last line keeps the card width of a
 * full one (empty space on the right).
 */
@Composable
private fun GridLine(
    columns: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(HomeGridSpacing),
    ) {
        content()
        repeat(columns - count) { Spacer(Modifier.weight(1f)) }
    }
}
