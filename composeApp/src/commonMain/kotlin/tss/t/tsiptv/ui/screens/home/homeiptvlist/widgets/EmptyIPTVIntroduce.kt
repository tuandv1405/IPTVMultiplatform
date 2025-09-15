package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_info
import tsiptv.composeapp.generated.resources.what_is_iptv_desc
import tsiptv.composeapp.generated.resources.what_is_iptv_title
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes

@Composable
fun EmptyIPTVIntroduce() {
    Row(
        Modifier.Companion
            .fillMaxWidth()
            .padding(top = 20.dp)
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(vertical = 24.dp, horizontal = 24.dp)
    ) {
        Image(
            painterResource(Res.drawable.ic_info),
            contentDescription = "Logo",
            modifier = Modifier.Companion.size(32.dp)
                .clip(CircleShape)
                .background(Color(0x333B82F6))
                .padding(8.dp),
            colorFilter = ColorFilter.Companion.tint(Color(0xFF60A5FA))
        )
        Spacer(Modifier.Companion.width(16.dp))
        Column {
            Text(
                stringResource(Res.string.what_is_iptv_title),
                style = MaterialTheme.typography.titleMedium
                    .copy(
                        color = TSColors.TextPrimary,
                        fontWeight = FontWeight.Companion.Medium,
                        fontSize = 15.sp,
                    ),
            )
            Spacer(Modifier.Companion.height(16.dp))
            Text(
                stringResource(Res.string.what_is_iptv_desc),
                style = MaterialTheme.typography.bodyMedium
                    .copy(
                        color = TSColors.TextSecondary,
                        fontWeight = FontWeight.Companion.Normal,
                        fontSize = 13.sp,
                    )
            )
        }
    }
}