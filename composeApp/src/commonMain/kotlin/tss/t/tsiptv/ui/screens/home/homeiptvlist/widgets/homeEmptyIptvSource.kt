package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.iptv_help_title
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.themes.TSColors

fun LazyListScope.homeEmptyIptvSource(
    navController: NavHostController,
    parentNavController: NavHostController,
) {
    item("EmptyIptvSourceCard") {
        EmptyIptvSourceCard(
            navController = navController,
            parentNavController = parentNavController,
        )
    }
    item("EmptyIptvHelp") {
        Spacer(Modifier.Companion.height(20.dp))
        Text(
            stringResource(Res.string.iptv_help_title),
            style = MaterialTheme.typography.titleLarge
                .copy(
                    color = TSColors.TextSecondary,
                    fontWeight = FontWeight.Companion.Normal,
                    fontSize = 15.sp,
                    textDecoration = TextDecoration.Companion.Underline
                ),
            modifier = Modifier.Companion.clickable {
                val router =
                    NavRoutes.WebView("https://dvt1405.github.io/iMediaReleasePages/")
                parentNavController.navigate(
                    router,
                ) {
                    launchSingleTop = true
                }
            }
        )
        Spacer(Modifier.Companion.height(20.dp))
    }

    item("EmptyIPTVIntroduce") {
        EmptyIPTVIntroduce()
    }
}