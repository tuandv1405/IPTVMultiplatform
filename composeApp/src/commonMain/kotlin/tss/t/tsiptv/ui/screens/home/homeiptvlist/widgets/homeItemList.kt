package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.all_channels_title
import tsiptv.composeapp.generated.resources.continue_watching
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.home.widget.HomeChannelHistoryItem
import tss.t.tsiptv.ui.screens.home.widget.HomeChannelItem
import tss.t.tsiptv.ui.screens.home.widget.NowPlayingCard
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.themes.TSTextStyles

fun LazyListScope.homeItemList(
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    categoryListState: LazyListState,
    onHomeEvent: (HomeEvent) -> Unit,
) {
    if (homeUiState.nowPlayingChannel != null) {
        item("NowWatchingCard") {
            NowPlayingCard(
                channelWithHistory = homeUiState.nowPlayingChannel,
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

    items(homeUiState.listChannels) { channel ->
        HomeChannelItem(
            channel = channel,
            modifier = Modifier.Companion.fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            onHomeEvent(HomeEvent.OnOpenVideoPlayer(it))
        }
        Spacer(Modifier.Companion.height(16.dp))
    }
}
