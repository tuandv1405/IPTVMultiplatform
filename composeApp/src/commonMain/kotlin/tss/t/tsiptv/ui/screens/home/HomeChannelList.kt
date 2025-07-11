package tss.t.tsiptv.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import tss.t.tsiptv.core.database.Channel
import tss.t.tsiptv.core.database.IPTVDatabase

/**
 * A component that displays a list of channels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChannelList(
    onChannelClick: (Channel) -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    hazeState: HazeState,
) {
}
