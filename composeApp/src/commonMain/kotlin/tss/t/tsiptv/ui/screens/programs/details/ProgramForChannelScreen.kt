package tss.t.tsiptv.ui.screens.programs.details

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.viewmodel.koinViewModel
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.HorizontalDividersGradient
import tss.t.tsiptv.ui.widgets.TSAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramForChannelScreen(
    channel: ChannelWithProgramCount,
) {
    val viewModel = koinViewModel<ProgramDetailViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProgram(channel)
    }
    val hazeState = rememberHazeState(blurEnabled = true)
    val infiniteTransition = rememberInfiniteTransition("Loading")
    val shimmerColor by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquidFlow"
    )

    Scaffold(
        topBar = {
            TSAppBar(
                modifier = Modifier.fillMaxWidth()
                    .background(TSColors.BackgroundColor)
                    .hazeEffect(state = hazeState),
                title = channel.name ?: channel.channelId
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.hazeSource(hazeState)
                .animateContentSize(),
            contentPadding = it,
            userScrollEnabled = !uiState.isLoading,
        ) {
            if (uiState.isLoading) {
                items(50) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .height(40.dp)
                            .blur(20.dp)
                            .background(
                                TSColors.White.copy(alpha = shimmerColor),
                                TSShapes.roundedShape12
                            )
                    )
                    HorizontalDividersGradient()
                }
            }
            items(uiState.programList) {
                DetailProgramItem(
                    it,
                    channel
                )
                HorizontalDividersGradient()
            }
        }
    }
}
