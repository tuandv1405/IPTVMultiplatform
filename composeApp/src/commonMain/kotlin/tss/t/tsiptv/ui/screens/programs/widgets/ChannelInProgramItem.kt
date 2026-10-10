package tss.t.tsiptv.ui.screens.programs.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import tss.t.tsiptv.ui.screens.source.initials
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.programs_today_format
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles

@Composable
fun ChannelInProgramItem(
    item: ChannelWithProgramCount,
    onClick: (ChannelWithProgramCount) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable {
                onClick(item)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelAvatar(
            name = item.name ?: item.channelId,
            logoUrl = item.logoUrl,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f),
        ) {
            Text(
                item.name ?: item.channelId,
                style = TSTextStyles.semiBold15
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(
                    Res.string.programs_today_format,
                    item.programCount
                ),
                style = TSTextStyles.normal13.copy(
                    color = TSColors.TextSecondary
                )
            )
        }
        Spacer(Modifier.width(12.dp))
    }
}

/**
 * The channel's logo on a tile; the channel's initials when it has no logo or the logo fails to
 * load (many guides and playlists carry no logo for some channels).
 */
@Composable
private fun ChannelAvatar(name: String, logoUrl: String?) {
    val shape = TSShapes.roundedShape8
    val tile = Modifier.size(60.dp).clip(shape).background(TSColors.SecondaryBackgroundColor, shape)
    val fallback: @Composable () -> Unit = {
        Box(tile, contentAlignment = Alignment.Center) {
            Text(
                text = initials(name),
                style = TSTextStyles.semiBold15,
                color = TSColors.TextSecondary,
            )
        }
    }
    if (logoUrl.isNullOrBlank()) {
        fallback()
        return
    }
    SubcomposeAsyncImage(
        model = logoUrl,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = tile.padding(6.dp),
        loading = { fallback() },
        error = { fallback() },
    )
}
