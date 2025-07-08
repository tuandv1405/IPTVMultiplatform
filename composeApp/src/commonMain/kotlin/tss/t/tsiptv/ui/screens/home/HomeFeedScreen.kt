package tss.t.tsiptv.ui.screens.home

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState


/**
 * Home feed screen showing the list of channels
 */
@Composable
fun HomeFeedScreen(
    onChannelClick: (tss.t.tsiptv.core.database.Channel) -> Unit,
    hazeState: HazeState,
) {
    HomeChannelList(
        onChannelClick = onChannelClick,
        hazeState = hazeState,
    )
}
