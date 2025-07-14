package tss.t.tsiptv.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.account_group_title
import tsiptv.composeapp.generated.resources.btn_logout_cancel
import tsiptv.composeapp.generated.resources.btn_logout_ok
import tsiptv.composeapp.generated.resources.ic_key
import tsiptv.composeapp.generated.resources.ic_logout
import tsiptv.composeapp.generated.resources.ic_notification
import tsiptv.composeapp.generated.resources.ic_profile
import tsiptv.composeapp.generated.resources.ic_profile_gradient
import tsiptv.composeapp.generated.resources.ic_settings
import tsiptv.composeapp.generated.resources.ic_subscriptions
import tsiptv.composeapp.generated.resources.logout_button_title
import tsiptv.composeapp.generated.resources.preferences_group_title
import tsiptv.composeapp.generated.resources.profile_change_password_title
import tsiptv.composeapp.generated.resources.profile_edit_title
import tsiptv.composeapp.generated.resources.profile_notification_title
import tsiptv.composeapp.generated.resources.profile_subscription_title
import tsiptv.composeapp.generated.resources.profile_title
import tsiptv.composeapp.generated.resources.ic_arrow_right
import tsiptv.composeapp.generated.resources.logout_dialog_message
import tsiptv.composeapp.generated.resources.logout_dialog_title
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.NegativeButton
import tss.t.tsiptv.ui.widgets.TSDialog

data class ProfileItem(
    val title: StringResource,
    val icon: DrawableResource,
    val action: ProfileScreenActions,
)

enum class ProfileScreenActions(val value: Int) {
    EditProfile(0),
    ChangePassword(1),
    Help(2),
    Subscription(3),
    Notification(4),
    Logout(10);

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authState: AuthUiState,
    hazeState: HazeState,
    onProfileEvent: (LoginEvents) -> Unit = {},
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val displayName = remember(authState.displayName) {
        authState.displayName ?: authState.user?.email?.uppercase() ?: ""
    }
    var isShowLogoutButton by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val accountGroupItems = remember {
        listOf(
            ProfileItem(
                title = Res.string.profile_edit_title,
                icon = Res.drawable.ic_profile_gradient,
                action = ProfileScreenActions.EditProfile
            ),
            ProfileItem(
                title = Res.string.profile_change_password_title,
                icon = Res.drawable.ic_key,
                action = ProfileScreenActions.ChangePassword
            ),
            ProfileItem(
                title = Res.string.profile_subscription_title,
                icon = Res.drawable.ic_subscriptions,
                action = ProfileScreenActions.Subscription
            )
        )
    }

    val preferencesGroupItems = remember {
        listOf(
            ProfileItem(
                title = Res.string.profile_notification_title,
                icon = Res.drawable.ic_notification,
                action = ProfileScreenActions.Notification
            )
        )
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.hazeSource(hazeState),
            state = listState,
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
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                            ),
                            shape = CircleShape
                        )
                        .padding(32.dp)
                ) {
                    //Circle image
                    Image(
                        painter = painterResource(Res.drawable.ic_profile),
                        contentDescription = null,
                        modifier = Modifier.clip(CircleShape)
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(TSColors.SecondaryBackgroundColor)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontWeight = FontWeight.W700
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = remember(authState.user) {
                        authState.user?.email ?: authState.user?.uid ?: ""
                    },
                    style = MaterialTheme.typography
                        .titleMedium.copy(color = TSColors.TextSecondary),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            item("AccountGroup") {
                Text(
                    text = stringResource(Res.string.account_group_title),
                    style = MaterialTheme.typography.titleLarge
                        .copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp, top = 20.dp)
                )
            }

            items(accountGroupItems) {
                ProfileItem(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    item = it,
                    onClick = { act ->
                        onProfileEvent(LoginEvents.OnProfileActionEvent(act))
                    }
                )
            }

            item("AccountPreferences") {
                Text(
                    text = stringResource(Res.string.preferences_group_title),
                    style = MaterialTheme.typography.titleLarge
                        .copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp, top = 20.dp)
                )
            }

            items(preferencesGroupItems) {
                ProfileItem(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    item = it,
                    onClick = { act ->
                        onProfileEvent(LoginEvents.OnProfileActionEvent(act))
                    }
                )
            }

            item("Logout") {
                NegativeButton(
                    text = stringResource(Res.string.logout_button_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = 20.dp,
                            end = 20.dp,
                            start = 20.dp
                        ),
                    icon = Res.drawable.ic_logout,
                    onClick = {
                        showLogoutDialog = true
                    }
                )
            }

            item("SpacerBottom") {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 56.dp)
                )
            }
        }
    }

    if (showLogoutDialog) {
        TSDialog(
            title = stringResource(Res.string.logout_dialog_title),
            message = stringResource(Res.string.logout_dialog_message),
            positiveButtonText = stringResource(Res.string.btn_logout_cancel),
            negativeButtonText = stringResource(Res.string.btn_logout_ok),
            onPositiveClick = {
                showLogoutDialog = false
            },
            onNegativeClick = {
                onProfileEvent(LoginEvents.OnLogoutPressed)
            }
        )
    }
}

val bgItemColor = Color(0xFF1F2937)

@Composable
fun ProfileItem(
    modifier: Modifier = Modifier,
    item: ProfileItem,
    onClick: (ProfileScreenActions) -> Unit = {},
) {
    ProfileItem(
        modifier = modifier,
        title = item.title,
        icon = item.icon,
        action = item.action,
        onClick = onClick
    )
}

@Composable
fun ProfileItem(
    modifier: Modifier = Modifier,
    title: StringResource,
    icon: DrawableResource,
    action: ProfileScreenActions,
    onClick: (ProfileScreenActions) -> Unit = {},
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = TSShapes.roundedShape12)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1F2937).copy(0.8f),
                        Color(0xFF374151).copy(0.8f),
                    )
                ),
                shape = TSShapes.roundedShape12
            )
            .border(
                width = 1.dp,
                color = Color(0xFF374151).copy(0.5f),
                shape = TSShapes.roundedShape12
            )
            .clickable(onClick = { onClick(action) })
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
                .clip(TSShapes.roundedShape8)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(10.dp, 24.dp)
        )

    }

}
