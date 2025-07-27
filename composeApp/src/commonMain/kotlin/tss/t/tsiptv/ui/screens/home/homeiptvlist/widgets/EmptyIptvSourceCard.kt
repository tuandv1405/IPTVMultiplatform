package tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.btn_add_iptv_source_title
import tsiptv.composeapp.generated.resources.empty_iptv_source_title
import tsiptv.composeapp.generated.resources.ic_home_empty
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.GradientButton1

@Composable
fun EmptyIptvSourceCard(
    navController: NavHostController,
    parentNavController: NavHostController,
) {
    Column(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(top = 20.dp)
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.Companion.clip(CircleShape)
                .background(TSColors.IconContainerColor)
                .size(64.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_home_empty),
                contentDescription = "Save",
                modifier = Modifier.Companion.size(26.dp)
                    .align(Alignment.Companion.Center),
                colorFilter = ColorFilter.Companion.tint(Color.Companion.White)
            )
        }

        Spacer(Modifier.Companion.height(20.dp))

        Text(
            stringResource(Res.string.empty_iptv_source_title),
            style = MaterialTheme.typography.titleMedium
                .copy(
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.Companion.Medium,
                    fontSize = 18.sp
                )
        )
        Spacer(Modifier.Companion.height(16.dp))

        GradientButton1(
            text = stringResource(Res.string.btn_add_iptv_source_title),
        ) {
            parentNavController.navigate(NavRoutes.ImportIptv) {
                launchSingleTop = true
            }
        }
    }
}