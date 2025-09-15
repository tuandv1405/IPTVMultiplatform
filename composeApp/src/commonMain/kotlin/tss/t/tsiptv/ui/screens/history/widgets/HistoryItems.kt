package tss.t.tsiptv.ui.screens.history.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.getString
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.today_format
import tsiptv.composeapp.generated.resources.yesterday_format
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.utils.formatDynamic
import tss.t.tsiptv.utils.isToday
import tss.t.tsiptv.utils.isYesterday

@Composable
fun HistoryItems(
    channelWithHistory: ChannelWithHistory,
    isPlaying: Boolean = false,
    isProgression: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(horizontal = 16.dp),
    onItemClick: (ChannelWithHistory) -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
) {
    val localContext = LocalPlatformContext.current

    var description by remember {
        mutableStateOf(
            channelWithHistory.lastPlayedTimestamp.formatDynamic("dd-MM, HH:mm")
        )
    }

    LaunchedEffect(channelWithHistory.channelId, channelWithHistory.lastPlayedTimestamp) {
        description = when {
            channelWithHistory.lastPlayedTimestamp.isToday() -> {
                getString(
                    Res.string.today_format,
                    channelWithHistory.lastPlayedTimestamp.formatDynamic("HH:mm")
                )
            }

            channelWithHistory.lastPlayedTimestamp.isYesterday() -> {
                getString(
                    Res.string.yesterday_format,
                    channelWithHistory.lastPlayedTimestamp.formatDynamic("HH:mm")
                )
            }

            else -> channelWithHistory.lastPlayedTimestamp.formatDynamic("dd-MM, HH:mm")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(paddingValues)
            .clip(TSShapes.roundedShape16)
            .clickable(onClick = {
                onItemClick(channelWithHistory)
            })
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF161A32),
                        Color(0xFF2D3238),
                    )
                ),
                shape = TSShapes.roundedShape16
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(localContext)
                .data(channelWithHistory.logoUrl)
                .crossfade(200)
                .build(),
            contentDescription = channelWithHistory.channelName,
            modifier = Modifier.size(64.dp)
                .clip(TSShapes.roundedShape16)
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channelWithHistory.channelName,
                style = TSTextStyles.semiBold15
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = channelWithHistory.channelName,
                style = TSTextStyles.normal13.copy(
                    color = TSColors.TextSecondary
                )
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = description,
                style = TSTextStyles.normal11.copy(
                    color = TSColors.TextSecondary
                )
            )
        }
        PlayPauseButtonGradient(
            isPlaying = isPlaying,
            isProgression = isProgression,
            onPlayPauseClick = onPlayPauseClick
        )
    }
}

@Composable
fun PlayPauseButtonGradient(
    isPlaying: Boolean = false,
    isProgression: Boolean = false,
    onPlayPauseClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier.size(64.dp)
            .clip(CircleShape)
            .background(TSColors.baseGradient, CircleShape)
            .clickable(onClick = onPlayPauseClick),
        contentAlignment = Alignment.Center
    ) {

        when {
            isProgression -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            else -> {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "PlayPause",
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "PlayPause",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
