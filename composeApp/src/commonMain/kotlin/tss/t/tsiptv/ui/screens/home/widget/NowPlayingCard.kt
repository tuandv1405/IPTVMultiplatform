package tss.t.tsiptv.ui.screens.home.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.now_playing_title
import tsiptv.composeapp.generated.resources.pause
import tsiptv.composeapp.generated.resources.play
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.player.models.toMediaItem
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.utils.formatToday

@Composable
fun NowPlayingCard(
    channelWithHistory: ChannelWithHistory,
    currentProgram: IPTVProgram? = null,
    modifier: Modifier,
    isPlaying: Boolean,
    onHomeEvent: (HomeEvent) -> Unit,
) {
    val channel = remember(channelWithHistory) {
        channelWithHistory.getChannel()
    }
    val platformContext = LocalPlatformContext.current
    Column(
        modifier = modifier
            .clickable(onClick = {
                if (!isPlaying) {
                    onHomeEvent(HomeEvent.OnOpenVideoPlayer(channel))
                } else {
                    onHomeEvent(HomeEvent.OnResumeMediaItem(channel.toMediaItem()))
                }
            })
            .fillMaxWidth()
            .clip(TSShapes.roundedShape16)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6),
                    )
                ),
                shape = TSShapes.roundedShape16
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.now_playing_title),
                    color = TSColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(2.dp))
                Text(
                    text = channel.name,
                    color = TSColors.TextSecondaryLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 14.sp
                )

            }

            AsyncImage(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9)
                    .clip(TSShapes.roundedShape8),
                model = ImageRequest.Builder(context = platformContext)
                    .data(channel.logoUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = stringResource(Res.string.now_playing_title),
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp)
                    .clip(CircleShape)
                    .background(TSColors.White.copy(0.2f), CircleShape)
                    .clickable(onClick = {
                        if (!isPlaying) {
                            onHomeEvent(HomeEvent.OnPlayNowPlaying(channel))
                        } else {
                            onHomeEvent(HomeEvent.OnPauseNowPlaying(channel))
                        }
                    }),
            ) {
                AnimatedContent(isPlaying) { isPlaying ->
                    if (isPlaying) {
                        Icon(
                            Icons.Rounded.Pause,
                            contentDescription = stringResource(Res.string.play),
                            tint = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(Res.string.pause),
                            tint = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                currentProgram?.title?.let {
                    Text(
                        text = it,
                        style = TSTextStyles.semiBold13
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = channelWithHistory.lastPlayedTimestamp.formatToday(),
                    style = TSTextStyles.normal13
                )
            }
        }
    }
}

@Composable
fun NowPlayingCard(
    channel: Channel,
    category: Category,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    onEvent: (HomeEvent) -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(TSShapes.roundedShape16)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6),
                    )
                ),
                shape = TSShapes.roundedShape16
            )
            .clickable(onClick = {
                onEvent(HomeEvent.OnOpenVideoPlayer(channel))
            })
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(Res.string.now_playing_title),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channel.name,
            color = TSColors.TextSecondaryLight,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayIcon()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = channel.lastWatched.toString(),
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = TSColors.TextSecondaryLight
                )
            }
        }
    }
}

@Composable
fun PlayIcon() {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .padding(8.dp)
            .background(
                color = Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Image(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Play Icon",
            modifier = Modifier.size(20.dp)
                .align(Alignment.Center),
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}


@Composable
@Preview
fun NowPlayingCardHistoryPreview() {
    NowPlayingCard(
        channelWithHistory = ChannelWithHistory(
            channelName = "Sample Channel",
            lastWatched = 123456789,
            categoryId = "1",
            playlistId = "1",
            isFavorite = false,
            channelUrl = "https://example.com/stream",
            logoUrl = "https://example.com/logo.png",
            channelId = "1",
            lastPlayedTimestamp = Clock.System.now().toEpochMilliseconds() - 30 * 1000,
            historyId = 10L,
            playCount = 10,
            totalPlayedTimeMs = 1000L,
            totalDurationMs = 1000L,
            currentPositionMs = 1000L
        ),
        modifier = Modifier,
        isPlaying = false,
        onHomeEvent = {}
    )
}
