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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.painterResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_loading_gradient_overlay
import tss.t.tsiptv.core.database.Channel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes

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
            model = remember {
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
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = channel.id,
                color = TSColors.TextSecondaryLight,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}