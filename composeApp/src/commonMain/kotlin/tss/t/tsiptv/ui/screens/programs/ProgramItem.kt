package tss.t.tsiptv.ui.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.tsiptv.core.parser.IPTVProgram
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles

@Composable
fun ProgramItem(
    program: IPTVProgram,
    isCurrentProgram: Boolean,
    paddingValues: PaddingValues,
) {
    Row(
        modifier = Modifier
            .padding(paddingValues)
            .clip(TSShapes.roundedShape12)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape12)
            .then(
                other = if (isCurrentProgram) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                TSColors.GradientBlue.copy(0.5f),
                                TSColors.GradientGreen.copy(0.5f),
                            )
                        ),
                        shape = TSShapes.roundedShape12
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!program.startTimeStr.isNullOrEmpty()) {
            BasicText(
                text = program.startTimeStr!! + "-" + program.endTimeStr,
                style = TSTextStyles.semiBold13.copy(TSColors.TextSecondary),
                modifier = Modifier.width(85.dp),
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 9.sp,
                    maxFontSize = 13.sp
                )
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.title,
                style = TSTextStyles.primaryBody
            )
        }

        if (isCurrentProgram) {
            Box(
                modifier = Modifier.size(6.dp)
                    .clip(CircleShape)
                    .background(TSColors.RedNotify, CircleShape)
            )
        }
    }
}
