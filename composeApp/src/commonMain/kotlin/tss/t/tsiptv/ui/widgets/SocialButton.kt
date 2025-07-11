package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import tss.t.tsiptv.ui.themes.TSColors


@OptIn(ExperimentalResourceApi::class)
@Composable
fun SocialButton(
    iconRes: DrawableResource,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, TSColors.TextSecondary.copy(alpha = 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TSColors.TextPrimary,
            containerColor = Color.Transparent
        )
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(TSColors.AccentCyan)
        )
    }
}