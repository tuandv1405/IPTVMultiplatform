package tss.t.tsiptv.ui.screens.home.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes

private val SelectedColor = Color(0xFF22C55E)

@Composable
fun HomeCategoryItem(
    categoryName: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onItemClick: () -> Unit = {},
) {
    val bgColorFraction by animateFloatAsState(if (isSelected) 1f else 0f)
    Box(
        modifier = modifier
            .clip(TSShapes.roundedShapeExtra)
            .background(
                color = lerp(
                    start = TSColors.SecondaryBackgroundColor,
                    stop = SelectedColor,
                    fraction = bgColorFraction
                ),
                shape = TSShapes.roundedShapeExtra
            )
            .clickable(onClick = onItemClick)
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = categoryName,
            color = lerp(
                start = TSColors.TextSecondary,
                stop = Color.White,
                fraction = bgColorFraction
            ),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
