package tss.t.tsiptv.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.btn_add_iptv_source_title
import tsiptv.composeapp.generated.resources.empty_iptv_source_title
import tsiptv.composeapp.generated.resources.ic_info
import tsiptv.composeapp.generated.resources.iptv_help_title
import tsiptv.composeapp.generated.resources.what_is_iptv_desc
import tsiptv.composeapp.generated.resources.what_is_iptv_title
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.login.provider.LocalAuthProvider
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.GradientButton1
import tss.t.tsiptv.ui.widgets.HeaderWithAvatar


/**
 * Home feed screen showing the list of channels
 */
@Composable
fun HomeFeedScreen(
    navController: NavHostController,
    parentNavController: NavHostController,
    hazeState: HazeState,
) {
    val scrollState = rememberLazyListState()
    val authState = LocalAuthProvider.current

    val helloTitle = remember(authState) {
        "Hello${authState?.user?.displayName?.let { " $it" } ?: ""}"
    }
    val name = remember(authState) {
        authState?.user?.email ?: ""
    }

    Scaffold(
        topBar = {
            HeaderWithAvatar(
                modifier = Modifier
                    .background(TSColors.BackgroundColor)
                    .hazeEffect(hazeState)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                helloTitle = helloTitle,
                name = name,
                notificationCount = 10,
                onSettingClick = {
                    parentNavController.navigate(NavRoutes.LanguageSettings())
                },
                onNotificationClick = {

                },
                onAvatarClick = {
                    navController.navigate(NavRoutes.HomeScreens.PROFILE)
                }
            )
        }
    ) {
        LazyColumn(
            state = scrollState,
            contentPadding = it,
            modifier = Modifier.fillMaxSize()
                .hazeSource(hazeState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("EmptyIptvSourceCard") {
                EmptyIptvSourceCard(
                    navController = navController,
                    parentNavController = parentNavController,
                )
            }
            item("EmptyIptvHelp") {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(Res.string.iptv_help_title),
                    style = MaterialTheme.typography.titleLarge
                        .copy(
                            color = TSColors.TextSecondary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            textDecoration = TextDecoration.Underline
                        ),
                    modifier = Modifier.clickable {
                        // Set the URL parameter before navigating
                        val router =
                            NavRoutes.WebView("https://dvt1405.github.io/iMediaReleasePages/")
                        parentNavController.navigate(
                            router,
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
                Spacer(Modifier.height(20.dp))
            }

            item("EmptyIPTVIntroduce") {
                EmptyIPTVIntroduce()
            }
        }
    }
}

@Composable
private fun EmptyIptvSourceCard(
    navController: NavHostController,
    parentNavController: NavHostController,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.clip(CircleShape)
                .background(TSColors.IconContainerColor)
                .size(64.dp)
        ) {
            Image(
                imageVector = Icons.Rounded.Save,
                contentDescription = "Save",
                modifier = Modifier.size(20.dp)
                    .align(Alignment.Center),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(Res.string.empty_iptv_source_title),
            style = MaterialTheme.typography.titleMedium
                .copy(
                    color = TSColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp
                )
        )
        Spacer(Modifier.height(16.dp))

        GradientButton1(
            text = stringResource(Res.string.btn_add_iptv_source_title),
        ) {
            parentNavController.navigate(NavRoutes.ImportIptv) {
                launchSingleTop = true
            }
        }
    }
}

@Composable
fun EmptyIPTVIntroduce() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .padding(horizontal = 16.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor, TSShapes.roundedShape16)
            .padding(vertical = 24.dp, horizontal = 24.dp)
    ) {
        Image(
            painterResource(Res.drawable.ic_info),
            contentDescription = "Logo",
            modifier = Modifier.size(32.dp)
                .clip(CircleShape)
                .background(Color(0x333B82F6))
                .padding(8.dp),
            colorFilter = ColorFilter.tint(Color(0xFF60A5FA))
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                stringResource(Res.string.what_is_iptv_title),
                style = MaterialTheme.typography.titleMedium
                    .copy(
                        color = TSColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.what_is_iptv_desc),
                style = MaterialTheme.typography.bodyMedium
                    .copy(
                        color = TSColors.TextSecondary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                    ),
            )
        }
    }
}
