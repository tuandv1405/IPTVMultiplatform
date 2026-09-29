package tss.t.tsiptv.ui.widgets.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.channel_badge_radio
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes

/** The playlist's channel number before the name, as remotes and EPGs show it: `101  Example News`. */
fun Channel.displayTitle(): String = number?.let { "$it  $name" } ?: name

/** Radio icon and label, for rows and cards of radio channels. */
@Composable
fun RadioBadge(
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp,
    fontSize: TextUnit = 11.sp,
) {
    Row(
        modifier = modifier
            .background(TSColors.AccentCyan.copy(alpha = 0.18f), TSShapes.roundedShape4)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Radio,
            contentDescription = null,
            tint = TSColors.AccentCyan,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(Res.string.channel_badge_radio),
            color = TSColors.AccentCyan,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
        )
    }
}
