package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.ui.MediaPlayerContent
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes


internal val MiniPlayerHeight = 90.dp

@Composable
fun BoxScope.HomeMiniPlayer(
    showMiniPlayer: Boolean,
    contentPadding: PaddingValues,
    onHomeEvent: (HomeEvent) -> Unit,
    mediaItem: MediaItem,
    hazeState: HazeState,
    program: IPTVProgram?,
    playerViewModel: PlayerViewModel,
    onHideMiniPlayer: () -> Unit,
) {
    AnimatedVisibility(
        visible = showMiniPlayer,
        modifier = Modifier.Companion
            .align(Alignment.Companion.BottomCenter),
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = 300,
                easing = LinearOutSlowInEasing
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(
                durationMillis = 300,
                easing = LinearOutSlowInEasing
            )
        ) + fadeOut(
            targetAlpha = 0.5f,
            animationSpec = tween(
                durationMillis = 300,
                easing = LinearOutSlowInEasing
            )
        )
    ) {
        Row(
            modifier = Modifier.Companion
                .navigationBarsPadding()
                .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(TSShapes.roundedShape8)
                .clickable {
                    onHomeEvent(HomeEvent.OnResumeMediaItem(mediaItem))
                }
                .hazeEffect(hazeState),
            verticalAlignment = Alignment.Companion.CenterVertically,
        ) {
            MediaPlayerContent(
                player = playerViewModel.player,
                modifier = Modifier.Companion
                    .size(160.dp, MiniPlayerHeight)
                    .clip(TSShapes.roundedShape8)
                    .background(
                        color = TSColors.PlayerBackgroundColor,
                        shape = TSShapes.roundedShape8
                    )
            )
            Spacer(Modifier.Companion.width(12.dp))
            Column(
                modifier = Modifier.Companion.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = mediaItem.title,
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.Companion.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = program?.title ?: mediaItem.id,
                    color = TSColors.TextSecondaryLight,
                    fontWeight = FontWeight.Companion.Normal,
                    fontSize = 13.sp,
                    lineHeight = 14.sp
                )
            }
            Image(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                modifier = Modifier.Companion.size(32.dp)
                    .clip(CircleShape)
                    .padding(4.dp)
                    .clickable {
                        if (showMiniPlayer) {
                            onHideMiniPlayer()
                            playerViewModel.stopMedia()
                        }
                    },
                colorFilter = ColorFilter.Companion.tint(TSColors.TextPrimary)
            )
        }
    }
}
