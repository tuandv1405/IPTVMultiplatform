package tss.t.tsiptv.ui.screens.connect

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.connect_title
import tsiptv.composeapp.generated.resources.devices_cancel_sign_in
import tsiptv.composeapp.generated.resources.devices_last_seen
import tsiptv.composeapp.generated.resources.devices_limit_message
import tsiptv.composeapp.generated.resources.devices_limit_title
import tsiptv.composeapp.generated.resources.devices_removed_notice
import tsiptv.composeapp.generated.resources.devices_sign_out_remote
import tsiptv.composeapp.generated.resources.devices_title
import tsiptv.composeapp.generated.resources.sync_later
import tsiptv.composeapp.generated.resources.sync_new_available
import tsiptv.composeapp.generated.resources.sync_title
import tss.t.tsiptv.feature.account.DeviceGate
import tss.t.tsiptv.feature.account.DeviceLimitPolicy
import tss.t.tsiptv.feature.account.DevicePlatform
import tss.t.tsiptv.feature.account.DeviceSessionManager
import tss.t.tsiptv.feature.account.SyncService
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvMenuItem
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.utils.TimeStampFormat
import tss.t.tsiptv.utils.formatDynamic

/**
 * Account-level prompts shown on Home (phone and TV): the 4-device limit (PRD §4.2), "signed out
 * from another device", and "Có bản đồng bộ mới từ …" (PRD §5.2).
 */
@Composable
fun AccountHost(onOpenConnect: () -> Unit) {
    val sessions: DeviceSessionManager = koinInject()
    val sync: SyncService = koinInject()
    val gate by sessions.gate.collectAsStateWithLifecycle()
    val uid by sessions.uid.collectAsStateWithLifecycle()
    val incoming by sync.incoming.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // A newer sync from another device is looked up when the app opens (and on each sign-in).
    LaunchedEffect(uid) { if (uid != null) sync.refresh() }

    when (val g = gate) {
        is DeviceGate.LimitReached -> ConnectDialog(
            title = stringResource(Res.string.devices_limit_title, DeviceLimitPolicy.MAX_DEVICES),
            onDismissRequest = {},
            dismissOnOutside = false,
        ) {
            ConnectBody(stringResource(Res.string.devices_limit_message, g.devices.size), color = TSColors.TextPrimary)
            val first = remember { FocusRequester() }
            g.devices.forEachIndexed { index, device ->
                TvMenuItem(
                    title = device.displayName(),
                    description = device.lastSeenText() +
                        "\n" + stringResource(Res.string.devices_sign_out_remote),
                    icon = if (device.platform == DevicePlatform.ANDROID_TV) Icons.Rounded.Tv else Icons.Rounded.Devices,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { if (!g.busy) scope.launch { sessions.signOutRemote(device.id) } },
                    trailing = { if (g.busy) CircularProgressIndicator(Modifier.size(18.dp), color = TSColors.AccentCyan) },
                )
            }
            ConnectButtonRow {
                ConnectButton(stringResource(Res.string.devices_cancel_sign_in), { scope.launch { sessions.cancelSignIn() } })
            }
            LaunchedEffect(Unit) { first.requestFocusAfterLayout() }
        }

        DeviceGate.RemovedElsewhere -> MessageDialog(
            title = stringResource(Res.string.devices_title),
            text = stringResource(Res.string.devices_removed_notice),
            onDismiss = sessions::dismissNotice,
        )

        DeviceGate.None -> incoming?.takeIf { it.createdAt > sync.promptedCreatedAt }?.let { doc ->
            ConnectDialog(title = stringResource(Res.string.sync_title), onDismissRequest = { scope.launch { sync.dismiss(doc) } }) {
                ConnectBody(stringResource(Res.string.sync_new_available, doc.fromDeviceName), color = TSColors.TextPrimary)
                val open = remember { FocusRequester() }
                ConnectButtonRow {
                    ConnectButton(stringResource(Res.string.sync_later), { scope.launch { sync.dismiss(doc) } })
                    ConnectButton(
                        stringResource(Res.string.connect_title),
                        {
                            // Asked once per app session; the Connect screen keeps offering it.
                            sync.promptedCreatedAt = doc.createdAt
                            onOpenConnect()
                        },
                        Modifier.focusRequester(open),
                        primary = true,
                    )
                }
                LaunchedEffect(Unit) { open.requestFocusAfterLayout() }
            }
        }
    }
}
