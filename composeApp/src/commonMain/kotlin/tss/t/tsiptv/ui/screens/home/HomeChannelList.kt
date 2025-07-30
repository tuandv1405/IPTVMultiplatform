package tss.t.tsiptv.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import tss.t.tsiptv.core.model.Channel

/**
 * A component that displays a list of channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChannelList(
    onChannelClick: (Channel) -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    hazeState: HazeState,
) {
}
