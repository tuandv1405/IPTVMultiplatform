package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.themes.TSColors

@Composable
fun BoxScope.LanguageIcon(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Rounded.Language,
        contentDescription = "Change Language",
        modifier = modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(end = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .clickable {
                navController.navigate(NavRoutes.LanguageSettings)
            }
            .background(TSColors.BackgroundColor.copy(0.4f), CircleShape)
            .padding(8.dp)
    )
}