package tss.t.tsiptv.ui.widgets.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun FeaturedPlayerCard(
    title: String,
    subtitle: String,
    description: String,
    imageUrl: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(title, color = TSColors.TextPrimary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                IconButton(onClick = { /* Play */ }) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    color = TSColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(description, color = TSColors.TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
@Preview
fun FeaturedPlayerCardPreview() {
    FeaturedPlayerCard(
        title = "Title",
        subtitle = "Subtitle",
        description = "Description",
        imageUrl = "https://picsum.photos/200/300"
    )
}