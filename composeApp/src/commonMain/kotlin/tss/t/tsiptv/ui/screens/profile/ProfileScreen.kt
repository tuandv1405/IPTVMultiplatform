package tss.t.tsiptv.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.account_group_title
import tsiptv.composeapp.generated.resources.btn_deactivate_cancel
import tsiptv.composeapp.generated.resources.btn_deactivate_ok
import tsiptv.composeapp.generated.resources.btn_logout_cancel
import tsiptv.composeapp.generated.resources.btn_logout_ok
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.change_password_guide
import tsiptv.composeapp.generated.resources.change_password_title
import tsiptv.composeapp.generated.resources.confirm_new_password
import tsiptv.composeapp.generated.resources.current_password
import tsiptv.composeapp.generated.resources.deactivate_dialog_message
import tsiptv.composeapp.generated.resources.deactivate_dialog_title
import tsiptv.composeapp.generated.resources.ic_arrow_right
import tsiptv.composeapp.generated.resources.ic_key
import tsiptv.composeapp.generated.resources.ic_logout
import tsiptv.composeapp.generated.resources.ic_notification
import tsiptv.composeapp.generated.resources.ic_profile
import tsiptv.composeapp.generated.resources.ic_profile_gradient
import tsiptv.composeapp.generated.resources.ic_settings
import tsiptv.composeapp.generated.resources.ic_subscriptions
import tsiptv.composeapp.generated.resources.logout_button_title
import tsiptv.composeapp.generated.resources.logout_dialog_message
import tsiptv.composeapp.generated.resources.logout_dialog_title
import tsiptv.composeapp.generated.resources.preferences_group_title
import tsiptv.composeapp.generated.resources.profile_change_password_title
import tsiptv.composeapp.generated.resources.profile_edit_title
import tsiptv.composeapp.generated.resources.profile_notification_title
import tsiptv.composeapp.generated.resources.profile_subscription_title
import tsiptv.composeapp.generated.resources.profile_title
import tsiptv.composeapp.generated.resources.settings_deactivate_account
import tsiptv.composeapp.generated.resources.settings_title
import tsiptv.composeapp.generated.resources.deactivation_waiting_title
import tsiptv.composeapp.generated.resources.deactivation_waiting_message
import tsiptv.composeapp.generated.resources.feature_coming_soon
import tsiptv.composeapp.generated.resources.is_changing_password
import tsiptv.composeapp.generated.resources.new_password
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.save
import tsiptv.composeapp.generated.resources.user_name
import tss.t.tsiptv.core.firebase.models.DeactivationStatus
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.ui.widgets.NegativeButton
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.ui.widgets.GrayButton
import tss.t.tsiptv.ui.widgets.GradientButton2

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
    Settings(5),
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
    var showSettingsBottomSheet by remember { mutableStateOf(false) }
    var showDeactivationDialog by remember { mutableStateOf(false) }
    val displayName = remember(authState.displayName) {
        authState.displayName ?: authState.user?.email?.uppercase() ?: ""
    }
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
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = {
                    if (showLogoutDialog) {
                        showLogoutDialog = false
                    }
                }
            )
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
                            .clickable { showSettingsBottomSheet = true }
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
                showLogoutDialog = false
                onProfileEvent(LoginEvents.OnLogoutPressed)
            }
        )
    }

    if (showSettingsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsBottomSheet = false },
        ) {
            SettingsBottomSheetContent(
                deactivationRequest = authState.deactivationRequest,
                onDeactivateClick = {
                    showSettingsBottomSheet = false
                    showDeactivationDialog = true
                },
                onDismiss = { showSettingsBottomSheet = false }
            )
        }
    }

    if (showDeactivationDialog) {
        TSDialog(
            title = stringResource(Res.string.deactivate_dialog_title),
            message = stringResource(Res.string.deactivate_dialog_message),
            positiveButtonText = stringResource(Res.string.btn_deactivate_cancel),
            negativeButtonText = stringResource(Res.string.btn_deactivate_ok),
            onPositiveClick = {
                showDeactivationDialog = false
            },
            onNegativeClick = {
                showDeactivationDialog = false
                onProfileEvent(LoginEvents.OnConfirmDeactivation)
            }
        )
    }

    // Edit Username Dialog
    if (authState.showEditUsernameDialog) {
        EditUsernameDialog(
            username = authState.editUsernameValue,
            isLoading = authState.isEditUsernameLoading,
            error = authState.editUsernameError,
            onUsernameChange = { onProfileEvent(LoginEvents.OnEditUsernameChanged(it)) },
            onSave = { onProfileEvent(LoginEvents.OnSaveUsername) },
            onDismiss = { onProfileEvent(LoginEvents.OnDismissEditUsernameDialog) }
        )
    }

    // Change Password Dialog
    if (authState.showChangePasswordDialog) {
        ChangePasswordDialog(
            currentPassword = authState.currentPasswordValue,
            newPassword = authState.newPasswordValue,
            confirmPassword = authState.confirmPasswordValue,
            isLoading = authState.isChangePasswordLoading,
            error = authState.changePasswordError,
            onCurrentPasswordChange = { onProfileEvent(LoginEvents.OnCurrentPasswordChanged(it)) },
            onNewPasswordChange = { onProfileEvent(LoginEvents.OnNewPasswordChanged(it)) },
            onConfirmPasswordChange = { onProfileEvent(LoginEvents.OnConfirmPasswordChanged(it)) },
            onSave = { onProfileEvent(LoginEvents.OnSavePassword) },
            onDismiss = { onProfileEvent(LoginEvents.OnDismissChangePasswordDialog) }
        )
    }

    // Subscription Popup
    if (authState.showSubscriptionPopup) {
        SubscriptionComingSoonDialog(
            onDismiss = { onProfileEvent(LoginEvents.OnDismissSubscriptionPopup) }
        )
    }

    // Notification Permission Dialog
    if (authState.showNotificationDialog) {
        NotificationPermissionDialog { onProfileEvent(LoginEvents.OnDismissNotificationDialog) }
    }
}

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

@Composable
private fun SettingsBottomSheetContent(
    deactivationRequest: tss.t.tsiptv.core.firebase.models.DeactivationRequest? = null,
    onDeactivateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        item("SettingsTitle") {
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item("DeactivateAccount") {
            val isPendingDeactivation = deactivationRequest?.status == DeactivationStatus.PENDING

            if (isPendingDeactivation) {
                // Show waiting status when deactivation is pending
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape = TSShapes.roundedShape12)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFB923C).copy(0.1f),
                                    Color(0xFFF59E0B).copy(0.1f),
                                )
                            ),
                            shape = TSShapes.roundedShape12
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFF59E0B).copy(0.3f),
                            shape = TSShapes.roundedShape12
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.deactivation_waiting_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.deactivation_waiting_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF59E0B).copy(0.8f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            } else {
                // Show normal deactivate option when no pending request
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape = TSShapes.roundedShape12)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFDC2626).copy(0.1f),
                                    Color(0xFFEF4444).copy(0.1f),
                                )
                            ),
                            shape = TSShapes.roundedShape12
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFEF4444).copy(0.3f),
                            shape = TSShapes.roundedShape12
                        )
                        .clickable(onClick = onDeactivateClick)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_deactivate_account),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFEF4444),
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
        }

        item("SpacerBottom") {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EditUsernameDialog(
    username: String,
    isLoading: Boolean,
    error: String?,
    onUsernameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties()
    ) {
        Column(
            modifier = Modifier
                .clip(TSShapes.roundedShape20)
                .background(Color.White, TSShapes.roundedShape20)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.profile_edit_title),
                color = TSColors.TextTitlePrimaryDart,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    ),
                    disabledLabelColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    )
                ),
                label = {
                    Text(
                        stringResource(Res.string.user_name),
                        style = TSTextStyles.semiBold13.copy(
                            TSColors.SecondaryBackgroundColor.copy(
                                alpha = 0.5f
                            )
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                textStyle = TSTextStyles.normal15.copy(
                    color = TSColors.DeepBlue
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TSColors.DeepBlue,
                    strokeWidth = 2.5.dp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GrayButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                )
                GradientButton2(
                    text = stringResource(Res.string.save),
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp),
                )
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    isLoading: Boolean,
    error: String?,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties()
    ) {
        Column(
            modifier = Modifier
                .clip(TSShapes.roundedShape20)
                .background(Color.White, TSShapes.roundedShape20)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.profile_change_password_title),
                color = TSColors.TextTitlePrimaryDart,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.change_password_guide),
                color = TSColors.TextBodyPrimaryDart,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = currentPassword,
                onValueChange = onCurrentPasswordChange,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    ),
                    disabledLabelColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    )
                ),
                label = {
                    Text(
                        stringResource(Res.string.current_password),
                        style = TSTextStyles.semiBold13
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TSTextStyles.normal15.copy(
                    color = TSColors.DeepBlue
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChange,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    ),
                    disabledLabelColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    )
                ),
                label = {
                    Text(
                        stringResource(Res.string.new_password),
                        style = TSTextStyles.semiBold13
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TSTextStyles.normal15.copy(
                    color = TSColors.DeepBlue
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    ),
                    disabledLabelColor = TSColors.SecondaryBackgroundColor.copy(
                        alpha = 0.3f
                    )
                ),
                label = {
                    Text(
                        stringResource(Res.string.confirm_new_password),
                        style = TSTextStyles.semiBold13
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TSTextStyles.normal15.copy(
                    color = TSColors.DeepBlue
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TSColors.DeepBlue,
                    strokeWidth = 2.5.dp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GrayButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                )
                GradientButton2(
                    text = if (isLoading) {
                        stringResource(Res.string.is_changing_password)
                    } else {
                        stringResource(Res.string.change_password_title)
                    },
                    onClick = if (isLoading) {
                        {}
                    } else onSave,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                )
            }
        }
    }
}

@Composable
private fun SubscriptionComingSoonDialog(
    onDismiss: () -> Unit,
) {
    TSDialog(
        title = stringResource(Res.string.profile_subscription_title),
        message = stringResource(Res.string.feature_coming_soon),
        positiveButtonText = stringResource(Res.string.ok),
        onPositiveClick = onDismiss,
        onDismissRequest = onDismiss
    )
}

@Composable
private fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
) {
    TSDialog(
        title = stringResource(Res.string.profile_notification_title),
        message = stringResource(Res.string.feature_coming_soon),
        positiveButtonText = stringResource(Res.string.ok),
        onPositiveClick = onDismiss,
        onDismissRequest = onDismiss
    )
}
