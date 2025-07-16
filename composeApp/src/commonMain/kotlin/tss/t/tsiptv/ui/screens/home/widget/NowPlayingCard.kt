package tss.t.tsiptv.ui.screens.home.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.now_playing_title
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes

@Composable
fun NowPlayingCard(
    channel: Channel,
    category: Category,
    modifier: Modifier = Modifier,
    onEvent: (HomeEvent) -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(TSShapes.roundedShape16)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6),
                    )
                ),
                shape = TSShapes.roundedShape16
            )
            .clickable(onClick = {
                onEvent(HomeEvent.OnOpenVideoPlayer(channel))
            })
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(Res.string.now_playing_title),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channel.name,
            color = TSColors.TextSecondaryLight,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayIcon()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = channel.lastWatched.toString(),
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = TSColors.TextSecondaryLight
                )
            }
        }
    }
}

@Composable
fun PlayIcon() {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .padding(8.dp)
            .background(
                color = Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Image(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Play Icon",
            modifier = Modifier.size(20.dp)
                .align(Alignment.Center),
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}

@Composable
@Preview
fun NowPlayingCardPreview() {
    NowPlayingCard(
        channel = Channel(
            name = "Channel Name",
            lastWatched = 123456789,
            categoryId = 1.toString(),
            playlistId = 1.toString(),
            isFavorite = false,
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            logoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            id = 1.toString()
        ),
        category = Category(
            name = "Category Name",
            id = 1.toString(),
            playlistId = 1.toString()
        )
    )
}
