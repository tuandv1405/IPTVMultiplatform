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
import coil3.compose.AsyncImage
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
        AsyncImage(
            model = item.logoUrl,
            contentDescription = item.channelId,
            modifier = Modifier.size(60.dp)
                .clip(TSShapes.roundedShape8)
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
