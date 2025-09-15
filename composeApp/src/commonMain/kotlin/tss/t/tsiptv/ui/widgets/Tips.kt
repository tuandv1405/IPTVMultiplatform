package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_info
import tsiptv.composeapp.generated.resources.what_is_iptv_desc
import tsiptv.composeapp.generated.resources.what_is_iptv_title
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes


@Composable
fun Tips(
    modifier: Modifier = Modifier,
    iconInfo: DrawableResource = Res.drawable.ic_info,
    title: String = stringResource(Res.string.what_is_iptv_title),
    desc: String = stringResource(Res.string.what_is_iptv_desc),
    iconSize: Dp = 32.dp,
) {
    Row(
        modifier = modifier
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(vertical = 24.dp, horizontal = 24.dp)
    ) {
        Image(
            painterResource(iconInfo),
            contentDescription = "Logo",
            modifier = Modifier.size(iconSize)
                .clip(CircleShape)
                .background(Color(0x333B82F6))
                .padding(8.dp),
            colorFilter = ColorFilter.tint(Color(0xFF60A5FA))
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
                    .copy(
                        color = TSColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium
                    .copy(
                        color = TSColors.TextSecondary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                    ),
            )
        }
    }
}