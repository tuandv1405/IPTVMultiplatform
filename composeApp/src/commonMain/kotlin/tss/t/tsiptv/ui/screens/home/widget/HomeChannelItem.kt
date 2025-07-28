package tss.t.tsiptv.ui.screens.home.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_loading_gradient_overlay
import tsiptv.composeapp.generated.resources.today_format
import tsiptv.composeapp.generated.resources.yesterday_format
import tss.t.tsiptv.core.database.entity.ChannelWithHistory
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.ChannelHistory
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.utils.formatDateTime
import tss.t.tsiptv.utils.formatDynamic
import tss.t.tsiptv.utils.isToday
import tss.t.tsiptv.utils.isYesterday

@Composable
fun HomeChannelItem(
    channel: Channel,
    modifier: Modifier = Modifier,
    onItemClick: (Channel) -> Unit = {},
) {
    val platformContext = LocalPlatformContext.current
    Row(
        modifier = modifier.clip(TSShapes.roundedShape12)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape12)
            .clickable(
                onClick = {
                    onItemClick(channel)
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = CenterVertically
    ) {
        AsyncImage(
            model = remember(channel.logoUrl) {
                ImageRequest.Builder(platformContext)
                    .data(channel.logoUrl)
                    .crossfade(200)
                    .diskCacheKey(channel.logoUrl)
                    .build()
            },
            contentDescription = channel.name,
            modifier = Modifier.size(48.dp)
                .clip(TSShapes.roundedShape8),
            contentScale = ContentScale.Inside,
            onError = {
                it.result.throwable.let {
                    println(it.message)
                }
            },
            error = painterResource(Res.drawable.ic_loading_gradient_overlay),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = channel.name,
                color = TSColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Text(
                text = channel.id,
                color = TSColors.TextSecondaryLight,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                textAlign = TextAlign.Start,
                lineHeight = 14.sp
            )
        }
    }
}


@Composable
fun HomeChannelHistoryItem(
    channel: ChannelWithHistory,
    modifier: Modifier = Modifier,
    onItemClick: (Channel) -> Unit = {},
) {
    val platformContext = LocalPlatformContext.current
    var description by remember {
        mutableStateOf(
            channel.lastPlayedTimestamp.formatDynamic("dd-MM, HH:mm")
        )
    }

    LaunchedEffect(channel.channelId, channel.lastPlayedTimestamp) {
        description = when {
            channel.lastPlayedTimestamp.isToday() -> {
                getString(
                    Res.string.today_format,
                    channel.lastPlayedTimestamp.formatDynamic("HH:mm")
                )
            }

            channel.lastPlayedTimestamp.isYesterday() -> {
                getString(
                    Res.string.yesterday_format,
                    channel.lastPlayedTimestamp.formatDynamic("HH:mm")
                )
            }

            else -> channel.lastPlayedTimestamp.formatDynamic("dd-MM, HH:mm")
        }
    }

    Row(
        modifier = modifier.clip(TSShapes.roundedShape12)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape12)
            .clickable(
                onClick = {
                    onItemClick(channel.getChannel())
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = CenterVertically
    ) {
        AsyncImage(
            model = remember(channel.logoUrl) {
                ImageRequest.Builder(platformContext)
                    .data(channel.logoUrl)
                    .crossfade(200)
                    .diskCacheKey(channel.logoUrl)
                    .build()
            },
            contentDescription = channel.channelName,
            modifier = Modifier.size(48.dp)
                .clip(TSShapes.roundedShape8),
            contentScale = ContentScale.Inside,
            onError = {
                it.result.throwable.let {
                    println(it.message)
                }
            },
            error = painterResource(Res.drawable.ic_loading_gradient_overlay),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = channel.channelName,
                color = TSColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )

            Text(
                text = channel.categoryId + " • " + description,
                color = TSColors.TextSecondaryLight,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                textAlign = TextAlign.Start,
                lineHeight = 14.sp
            )
        }
    }
}