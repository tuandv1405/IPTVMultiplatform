package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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

fun LazyListScope.homeItemList(
    adsViewModel: AdsViewModel,
    /** Channels with native ad slots (PRD R5): [AdsPolicy.interleave] of `homeUiState.listChannels`. */
    channelRows: List<AdsPolicy.Row<Channel>>,
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    categoryListState: LazyListState,
    /** Height of the sticky banner overlay (0 when it shows nothing). */
    bannerSpace: Dp,
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
