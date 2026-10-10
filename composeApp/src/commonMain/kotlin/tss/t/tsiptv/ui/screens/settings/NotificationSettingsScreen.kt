package tss.t.tsiptv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.notif_blocked
import tsiptv.composeapp.generated.resources.notif_debug_token
import tsiptv.composeapp.generated.resources.notif_enable
import tsiptv.composeapp.generated.resources.notif_general
import tsiptv.composeapp.generated.resources.notif_general_desc
import tsiptv.composeapp.generated.resources.notif_open_settings
import tsiptv.composeapp.generated.resources.notif_rationale
import tsiptv.composeapp.generated.resources.notif_tv_note
import tsiptv.composeapp.generated.resources.notif_unsupported
import tsiptv.composeapp.generated.resources.notif_updates
import tsiptv.composeapp.generated.resources.notif_updates_desc
import tsiptv.composeapp.generated.resources.profile_notification_title
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.feature.push.PushManager
import tss.t.tsiptv.feature.push.PushPermission
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvFocusableSurface

/**
 * Profile › Notifications (docs/prd-push-notifications.md): the master switch (asks for the Android
 * 13+ permission only here, after the rationale), the topic switches, and system-settings help.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, push: PushManager = koinInject()) {
    val settings by push.settings.collectAsStateWithLifecycle()
    val permission by push.permission.collectAsStateWithLifecycle()
    val token by push.token.collectAsStateWithLifecycle()
    val asked by push.asked.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isTv = LocalIsTvMode.current
    // Back from system settings: the permission may have changed.
    LifecycleResumeEffect(Unit) {
        push.refreshPermission()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(TSColors.backgroundGradientMain).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TSColors.TextPrimary)
            }
            Text(
                stringResource(Res.string.profile_notification_title),
                color = TSColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!push.isSupported) {
                Note(stringResource(Res.string.notif_unsupported))
                return@Column
            }
            Note(stringResource(Res.string.notif_rationale))
            if (isTv) Note(stringResource(Res.string.notif_tv_note))
            val blocked = permission == PushPermission.DENIED && settings.enabled
            SwitchRow(
                title = stringResource(Res.string.notif_enable),
                checked = settings.enabled,
                onChange = { on -> scope.launch { push.setEnabled(on) } },
            )
            // Explained only after the prompt was shown once (before that, the switch asks).
            if (permission == PushPermission.DENIED && (asked || settings.enabled)) {
                Text(stringResource(Res.string.notif_blocked), color = TSColors.LoadingYellow, fontSize = 14.sp)
                PillButton(stringResource(Res.string.notif_open_settings), onClick = { push.openSystemSettings() }, primary = false)
            }
            if (settings.enabled && !blocked) {
                SwitchRow(
                    title = stringResource(Res.string.notif_general),
                    description = stringResource(Res.string.notif_general_desc),
                    checked = settings.general,
                    onChange = { scope.launch { push.setGeneral(it) } },
                )
                SwitchRow(
                    title = stringResource(Res.string.notif_updates),
                    description = stringResource(Res.string.notif_updates_desc),
                    checked = settings.updates,
                    onChange = { scope.launch { push.setUpdates(it) } },
                )
            }
            // Debug builds only: the token, to send a test message from the Firebase console.
            if (push.showDebugToken && settings.enabled) {
                PillButton("Debug: test notification → Addons", onClick = { push.showTestNotification("tsiptv://addons") }, primary = false)
                PillButton("Debug: test notification → bad link", onClick = { push.showTestNotification("intent://evil#Intent;end") }, primary = false)
            }
            if (push.showDebugToken && token != null) {
                Text(stringResource(Res.string.notif_debug_token), color = TSColors.TextSecondary, fontSize = 12.sp)
                SelectionContainer { Text(token.orEmpty(), color = TSColors.TextSecondaryLight, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        color = TSColors.TextSecondaryLight,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(TSColors.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(12.dp),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    TvFocusableSurface(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.02f,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TSColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                description?.let { Text(it, color = TSColors.TextSecondary, fontSize = 13.sp) }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = TSColors.AccentCyan),
            )
        }
    }
}
