package tss.t.tsiptv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ic_profile
import tsiptv.composeapp.generated.resources.ic_settings
import tsiptv.composeapp.generated.resources.logout_button_title
import tsiptv.composeapp.generated.resources.profile_title
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.widgets.GradientButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authState: AuthUiState,
    hazeState: HazeState,
    onEvent: (LoginEvents) -> Unit = {},
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val displayName = remember(authState.displayName) {
        authState.displayName ?: authState.user?.email?.uppercase() ?: ""
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .hazeEffect(state = hazeState, style = HazeStyle.Unspecified)
            .statusBarsPadding()
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.hazeSource(hazeState)
        ) {
            item("HeaderProfile") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.profile_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.W700
                        )
                    )
                    Image(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.clip(CircleShape)
                            .size(40.dp)
                            .background(TSColors.SecondaryBackgroundColor)
                            .padding(8.dp)
                            .clip(CircleShape)
                    )
                }
            }

            item("ProfileScreen") {
                Box(
                    modifier = Modifier.size(128.dp)
                        .clip(CircleShape)
                        .background(TSColors.SecondaryBackgroundColor, CircleShape)
                        .padding(32.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_profile),
                        contentDescription = null,
                        modifier = Modifier.clip(CircleShape)
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(TSColors.SecondaryBackgroundColor)
                    )
                }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    fontWeight = FontWeight.W700
                )
            }

            item("LogoutButton") {
                GradientButton(
                    stringResource(Res.string.logout_button_title),
                    onClick = {
                        showLogoutDialog = !showLogoutDialog
                    },
                    modifier = Modifier.padding(top = 24.dp),
                    paddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
    }

    AnimatedVisibility(showLogoutDialog) {
        BasicAlertDialog(
            onDismissRequest = {
                showLogoutDialog = false
            },
            content = {

            }
        )
        onEvent(LoginEvents.OnLogoutPressed)
    }
}
