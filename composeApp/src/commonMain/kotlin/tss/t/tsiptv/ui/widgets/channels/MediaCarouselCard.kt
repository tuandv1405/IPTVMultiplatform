package tss.t.tsiptv.ui.widgets.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.ui.themes.TSColors

@Composable
fun MediaCarouselCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    isLive: Boolean = false,
) {
    Card(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .height(120.dp)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
                if (isLive) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .background(LiveRed, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "LIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TSColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = TSColors.TextSecondary, fontSize = 12.sp)
                }
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = TSColors.TextSecondary
                )
            }
        }
    }
}

@Preview
@Composable
fun MediaCarouselCardPreview() {
    MediaCarouselCard(
        title = "Title",
        subtitle = "Subtitle",
        imageUrl = "https://picsum.photos/200/300",
        isLive = true
    )
}