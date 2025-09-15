package tss.t.tsiptv.ui.screens.home.homeiptvlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.bottom_sheet_select_playlist
import tsiptv.composeapp.generated.resources.channel_count_format
import tsiptv.composeapp.generated.resources.round_close_24
import tss.t.tsiptv.core.database.entity.PlaylistEntity
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.ui.themes.StreamVaultTheme
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.ui.widgets.HorizontalDividersGradient
import tss.t.tsiptv.ui.widgets.IPTVAppBar
import tss.t.tsiptv.utils.TimeStampFormat
import tss.t.tsiptv.utils.formatDynamic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChangeIPTVSourceBottomSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    totalPlaylist: List<PlaylistWithChannelCount>,
    currentPlaylistId: String,
    hazeState: HazeState,
    onChange: (PlaylistWithChannelCount) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = TSShapes.roundShapeTop16,
        containerColor = TSColors.BackgroundColor,
        dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(TSShapes.roundedShape4),
                    color = TSColors.White.copy(alpha = 0.4f)
                ) {}
                IPTVAppBar(
                    modifier = Modifier.hazeEffect(hazeState),
                    title = stringResource(Res.string.bottom_sheet_select_playlist),
                    windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
                    backIcon = Res.drawable.round_close_24,
                    onBackClick = {
                        coroutineScope.launch {
                            if (sheetState.isVisible) {
                                sheetState.hide()
                            }
                        }.invokeOnCompletion {
                            onDismissRequest()
                        }
                    },
                )
                HorizontalDividersGradient()

            }
        }
    ) {
        BodyContent(totalPlaylist, currentPlaylistId, onChange)
    }
}

@Composable
private fun BodyContent(
    totalPlaylist: List<PlaylistWithChannelCount>,
    currentPlaylistId: String,
    onChange: (PlaylistWithChannelCount) -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    LazyColumn {
        items(totalPlaylist.size) {
            val playlist = totalPlaylist[it]
            PlaylistItem(
                selected = currentPlaylistId == playlist.playlist.id,
                playlist = playlist,
                onChange = onChange
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PlaylistItem(
    selected: Boolean,
    playlist: PlaylistWithChannelCount,
    onChange: (PlaylistWithChannelCount) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape12)
            .background(
                color = if (selected) {
                    TSColors.DeepBlue
                } else {
                    TSColors.SecondaryBackgroundColor
                },
                shape = TSShapes.roundedShape12
            )
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        shape = TSShapes.roundedShape12,
                        brush = TSColors.baseGradient
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = {
                onChange(playlist)
            })
            .padding(
                vertical = 12.dp,
                horizontal = 16.dp
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = playlist.playlist.name,
                style = TSTextStyles.semiBold13
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(
                    Res.string.channel_count_format,
                    playlist.channelCount
                ),
                style = TSTextStyles.normal11
            )
        }
        Spacer(Modifier.height(2.dp))
        Row {
            Text(
                playlist.playlist.url,
                style = TSTextStyles.normal13.copy(TSColors.GradientGreen)
            )
            Spacer(Modifier.weight(1f))
            Text(
                playlist.playlist
                    .lastUpdated.formatDynamic(TimeStampFormat.yyyyMMdd_HHmmss.formatStr),
                style = TSTextStyles.normal11
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun HomeChangeIPTVSourceBottomSheet_Preview() {
    StreamVaultTheme {
        BodyContent(
            totalPlaylist = listOf(
                PlaylistWithChannelCount(
                    playlist = PlaylistEntity(
                        id = "123",
                        name = "Test",
                        url = "https://test",
                        lastUpdated = Clock.System.now().toEpochMilliseconds(),
                        format = "XML"
                    ),
                    channelCount = 266
                ),
                PlaylistWithChannelCount(
                    playlist = PlaylistEntity(
                        id = "",
                        name = "Test",
                        url = "https://test",
                        lastUpdated = Clock.System.now().toEpochMilliseconds(),
                        format = "XML"
                    ),
                    channelCount = 266
                )
            ),
            currentPlaylistId = "123"
        ) {}
    }
}
