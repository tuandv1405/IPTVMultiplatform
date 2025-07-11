package tss.t.tsiptv.ui.widgets.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.core.database.ChannelEntity
import tss.t.tsiptv.ui.themes.TSColors

val CardDark = Color(0xFF2D3748)
val IconBgGreen = Color(0xFF34D399)
val LiveRed = Color(0xFFEF4444)

@Composable
fun ChannelListItem(
    channel: ChannelEntity,
    icon: ImageVector = Icons.Default.SportsSoccer,
    bgColor: Color = Color(0xFF3B82F6),
    isLive: Boolean = false,
) {
    val statusColor = if (isLive) LiveRed else IconBgGreen
    val subtitle = channel.categoryId?.replaceFirstChar { it.uppercase() } ?: "General"
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor), // Use derived color
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = channel.name,
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                ) // Direct from ChannelEntity
                Text(
                    subtitle,
                    color = TSColors.TextSecondary,
                    fontSize = 14.sp
                ) // Use derived subtitle
            }
            Box(
                modifier = Modifier.size(8.dp).background(statusColor, CircleShape)
            ) // Use derived status
        }
    }
}

@Preview
@Composable
private fun ChannelListItemPreview() {
    val sampleChannel = ChannelEntity(
        id = "1",
        name = "Sky Sports",
        url = "",
        logoUrl = "",
        categoryId = "sports",
        playlistId = "p1",
        isFavorite = false
    )
    ChannelListItem(channel = sampleChannel)
}