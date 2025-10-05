package tss.t.tsiptv.ui.screens.programs.details

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_profile_gradient
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.utils.formatDate
import tss.t.tsiptv.utils.formatHourMinute
import tss.t.tsiptv.utils.isToday

@Composable
fun DetailProgramItem(
    program: IPTVProgram,
    channel: ChannelWithProgramCount,
) {
    Column(
        modifier = Modifier.clickable(onClick = {

        }).padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                modifier = Modifier.size(70.dp)
                    .clip(TSShapes.roundedShape8)
                    .border(
                        1.dp,
                        TSColors.baseGradient,
                        TSShapes.roundedShape8
                    ),
                contentDescription = "",
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(
                        program.logo ?: channel.logoUrl
                        ?: Res.drawable.ic_profile_gradient
                    )
                    .crossfade(200)
                    .build(),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    program.title,
                    style = TSTextStyles.semiBold15
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    program.description?.trim() ?: "",
                    style = TSTextStyles.secondaryBody
                )
            }
            if (program.startTime.isToday()) {
                Text(
                    "${program.startTime.formatHourMinute()} - ${program.endTime.formatHourMinute()}",
                    style = TSTextStyles.normal13.copy(
                        color = TSColors.TextSecondaryLight
                    )
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "${program.startTime.formatHourMinute()} - ${program.endTime.formatHourMinute()}",
                        style = TSTextStyles.normal13.copy(
                            color = TSColors.TextSecondaryLight
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        program.startTime.formatDate(),
                        style = TSTextStyles.normal13.copy(
                            color = TSColors.TextSecondaryLight
                        )
                    )
                }
            }
        }
    }
}
