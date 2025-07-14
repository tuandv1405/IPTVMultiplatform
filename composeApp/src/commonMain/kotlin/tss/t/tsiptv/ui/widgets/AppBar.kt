package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_back_navigation
import tss.t.tsiptv.ui.themes.TSColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPTVAppBar(
    modifier: Modifier = Modifier,
    title: String = "Add IPTV Source",
    backIcon: DrawableResource = Res.drawable.ic_back_navigation,
    onBackClick: () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        navigationIcon = {
            Image(
                modifier = Modifier.padding(start = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBackClick)
                    .padding(16.dp),
                contentDescription = "Back button",
                painter = painterResource(backIcon),
            )
        },
        title = {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = TSColors.TextPrimary,
                fontSize = 17.sp
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TSColors.BackgroundColor,
        ),
    )
}
