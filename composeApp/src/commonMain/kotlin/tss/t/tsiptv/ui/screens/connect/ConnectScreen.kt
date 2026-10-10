package tss.t.tsiptv.ui.screens.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.connect_title
import tsiptv.composeapp.generated.resources.devices_count
import tsiptv.composeapp.generated.resources.devices_error
import tsiptv.composeapp.generated.resources.devices_last_seen
import tsiptv.composeapp.generated.resources.devices_remove_confirm
import tsiptv.composeapp.generated.resources.devices_sign_out_remote
import tsiptv.composeapp.generated.resources.devices_this_device
import tsiptv.composeapp.generated.resources.devices_title
import tsiptv.composeapp.generated.resources.lan_cancel
import tsiptv.composeapp.generated.resources.lan_close
import tsiptv.composeapp.generated.resources.lan_conditions_body
import tsiptv.composeapp.generated.resources.lan_conditions_title
import tsiptv.composeapp.generated.resources.lan_forget
import tsiptv.composeapp.generated.resources.lan_no_paired_tvs
import tsiptv.composeapp.generated.resources.lan_paired_tvs
import tsiptv.composeapp.generated.resources.lan_tv_no_paired
import tsiptv.composeapp.generated.resources.lan_tv_paired_phones
import tsiptv.composeapp.generated.resources.lan_tv_receive_address
import tsiptv.composeapp.generated.resources.lan_tv_receive_hint
import tsiptv.composeapp.generated.resources.lan_tv_pairing_open
import tsiptv.composeapp.generated.resources.lan_tv_receive_off
import tsiptv.composeapp.generated.resources.lan_tv_receive_ready
import tsiptv.composeapp.generated.resources.lan_tv_receive_title
import tsiptv.composeapp.generated.resources.lan_tv_unpair
import tsiptv.composeapp.generated.resources.lan_tv_unpair_confirm
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.reward_ad_unavailable
import tsiptv.composeapp.generated.resources.reward_granted
import tsiptv.composeapp.generated.resources.reward_limit_reached
import tsiptv.composeapp.generated.resources.send_tv_file_unavailable
import tsiptv.composeapp.generated.resources.send_tv_title
import tsiptv.composeapp.generated.resources.sync_applied
import tsiptv.composeapp.generated.resources.sync_apply_failed_some
import tsiptv.composeapp.generated.resources.sync_applying
import tsiptv.composeapp.generated.resources.sync_current_from
import tsiptv.composeapp.generated.resources.sync_error
import tsiptv.composeapp.generated.resources.sync_later
import tsiptv.composeapp.generated.resources.sync_merge
import tsiptv.composeapp.generated.resources.sync_merge_desc
import tsiptv.composeapp.generated.resources.sync_new_available
import tsiptv.composeapp.generated.resources.sync_none
import tsiptv.composeapp.generated.resources.sync_push
import tsiptv.composeapp.generated.resources.sync_push_desc
import tsiptv.composeapp.generated.resources.sync_pushed
import tsiptv.composeapp.generated.resources.sync_quota_reached
import tsiptv.composeapp.generated.resources.sync_remaining
import tsiptv.composeapp.generated.resources.sync_replace
import tsiptv.composeapp.generated.resources.sync_replace_confirm_message
import tsiptv.composeapp.generated.resources.sync_replace_confirm_title
import tsiptv.composeapp.generated.resources.sync_replace_desc
import tsiptv.composeapp.generated.resources.sync_sign_in
import tsiptv.composeapp.generated.resources.sync_skipped_files
import tsiptv.composeapp.generated.resources.sync_task_rewarded
import tsiptv.composeapp.generated.resources.sync_title
import tsiptv.composeapp.generated.resources.sync_unlimited
import tsiptv.composeapp.generated.resources.sync_too_large
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.feature.account.ApplyResult
import tss.t.tsiptv.feature.account.DeviceLimitPolicy
import tss.t.tsiptv.feature.account.DevicePlatform
import tss.t.tsiptv.feature.account.RegisteredDevice
import tss.t.tsiptv.feature.account.SyncMode
import tss.t.tsiptv.feature.lan.LanReceiverController
import tss.t.tsiptv.feature.lan.PlaylistCommand
import tss.t.tsiptv.feature.lan.ReceiverState
import tss.t.tsiptv.feature.lan.SharedPlaylist
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvMenuItem
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.utils.TimeStampFormat
import tss.t.tsiptv.utils.formatDynamic

/**
 * "TV & thiết bị" (phone and TV, D-pad navigable): receive from phone (TV), send a playlist and
 * paired TVs (phone), device sync and signed-in devices (both). PRD §2.6, §3, §4, §5.
 */
@Composable
fun ConnectScreen(
    isTvLayout: Boolean,
    onBack: () -> Unit,
    onSyncApplied: (ApplyResult) -> Unit,
    viewModel: ConnectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val database: IPTVDatabase = koinInject()
    val playlists by remember { database.getAllPlaylists() }.collectAsStateWithLifecycle(emptyList())
    val receiver: LanReceiverController = koinInject()
    val receiverState by receiver.state.collectAsStateWithLifecycle()
    val pairedPhones by receiver.pairedPhones.collectAsStateWithLifecycle()
    var sendPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var fileNotice by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<RegisteredDevice?>(null) }
    val firstItem = remember { FocusRequester() }
    val phoneFocus = remember { mutableMapOf<String, FocusRequester>() }
    var confirmUnpair by remember { mutableStateOf<tss.t.tsiptv.feature.lan.PairedPeer?>(null) }
    var focusAfterUnpair by remember { mutableStateOf<String?>(null) }
    var unpaired by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // TV: pairing mode only while this screen is shown and resumed (QC r2 N2).
    if (isTvLayout) {
        androidx.lifecycle.compose.LifecycleResumeEffect(receiver) {
            receiver.setPairingOpen(true)
            onPauseOrDispose { receiver.setPairingOpen(false) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onApplied = onSyncApplied
        viewModel.refresh()
        firstItem.requestFocusAfterLayout()
    }

    Column(Modifier.fillMaxSize().background(TSColors.BackgroundColor).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = if (isTvLayout) 40.dp else 8.dp, vertical = 8.dp)) {
            TvMenuItem(
                title = stringResource(Res.string.connect_title),
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onBack,
                modifier = Modifier.widthIn(max = 420.dp).focusRequester(firstItem),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (isTvLayout) 48.dp else 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ---- TV: receive -------------------------------------------------------------------
            if (isTvLayout) {
                item("receive") {
                    ConnectSectionTitle(stringResource(Res.string.lan_tv_receive_title))
                    when (val s = receiverState) {
                        is ReceiverState.Running -> {
                            ConnectBody(stringResource(Res.string.lan_tv_receive_ready, s.name), color = TSColors.TextPrimary)
                            if (s.addresses.isNotEmpty()) {
                                ConnectBody(stringResource(Res.string.lan_tv_receive_address, s.addresses.joinToString(", ") { "$it:${s.port}" }))
                            }
                        }
                        ReceiverState.Stopped -> CircularProgressIndicator(Modifier.size(20.dp), color = TSColors.AccentCyan)
                        ReceiverState.Failed, ReceiverState.Unsupported -> ConnectBody(stringResource(Res.string.lan_tv_receive_off))
                    }
                    ConnectBody(stringResource(Res.string.lan_tv_receive_hint))
                    ConnectBody(stringResource(Res.string.lan_tv_pairing_open), color = TSColors.AccentCyan)
                }
                item("receive_conditions") {
                    var open by remember { mutableStateOf(false) }
                    TvMenuItem(title = stringResource(Res.string.lan_conditions_title), onClick = { open = !open })
                    if (open) ConnectBody(stringResource(Res.string.lan_conditions_body))
                }
                item("phones_title") { ConnectSectionTitle(stringResource(Res.string.lan_tv_paired_phones)) }
                if (pairedPhones.isEmpty()) item("phones_none") { ConnectBody(stringResource(Res.string.lan_tv_no_paired)) }
                pairedPhones.forEachIndexed { index, peer ->
                    item("phone_${peer.id}") {
                        TvMenuItem(
                            title = peer.name,
                            description = stringResource(Res.string.lan_tv_unpair),
                            icon = Icons.Rounded.PhoneAndroid,
                            modifier = Modifier.focusRequester(phoneFocus.getOrPut(peer.id) { FocusRequester() }),
                            // QC r3 N8: confirm first; focus then moves to the next row.
                            onClick = {
                                focusAfterUnpair = (pairedPhones.getOrNull(index + 1) ?: pairedPhones.getOrNull(index - 1))?.id
                                confirmUnpair = peer
                            },
                            trailing = { androidx.compose.material3.Icon(Icons.Rounded.LinkOff, null, tint = TSColors.TextSecondary) },
                        )
                    }
                }
            } else {
                // ---- Phone: send a playlist, paired TVs -------------------------------------------
                item("send_title") { ConnectSectionTitle(stringResource(Res.string.send_tv_title)) }
                playlists.forEach { playlist ->
                    item("send_${playlist.id}") {
                        TvMenuItem(
                            title = playlist.name,
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            onClick = {
                                if (playlist.sourceType == PlaylistSourceType.FILE) fileNotice = true else sendPlaylist = playlist
                            },
                            trailing = { androidx.compose.material3.Icon(Icons.Rounded.Tv, null, tint = TSColors.AccentCyan) },
                        )
                    }
                }
                item("tvs_title") { ConnectSectionTitle(stringResource(Res.string.lan_paired_tvs)) }
                if (state.pairedTvs.isEmpty()) item("tvs_none") { ConnectBody(stringResource(Res.string.lan_no_paired_tvs)) }
                state.pairedTvs.forEach { peer ->
                    item("tv_${peer.id}") {
                        TvMenuItem(
                            title = peer.name,
                            description = stringResource(Res.string.lan_forget),
                            icon = Icons.Rounded.Tv,
                            onClick = { viewModel.forgetTv(peer.id) },
                            trailing = { androidx.compose.material3.Icon(Icons.Rounded.LinkOff, null, tint = TSColors.TextSecondary) },
                        )
                    }
                }
            }

            // ---- Account: sync and devices -------------------------------------------------------
            item("sync_title") { ConnectSectionTitle(stringResource(Res.string.sync_title)) }
            if (!state.signedIn) {
                item("signin") { ConnectBody(stringResource(Res.string.sync_sign_in)) }
            } else {
                state.incoming?.let { doc ->
                    item("incoming") {
                        ConnectBody(stringResource(Res.string.sync_new_available, doc.fromDeviceName), color = TSColors.AccentCyan)
                        TvMenuItem(
                            title = stringResource(Res.string.sync_merge),
                            description = stringResource(Res.string.sync_merge_desc),
                            icon = Icons.Rounded.CallMerge,
                            onClick = { viewModel.preview(doc, SyncMode.MERGE) },
                        )
                        TvMenuItem(
                            title = stringResource(Res.string.sync_replace),
                            description = stringResource(Res.string.sync_replace_desc),
                            icon = Icons.Rounded.SwapHoriz,
                            onClick = { viewModel.preview(doc, SyncMode.REPLACE) },
                        )
                        TvMenuItem(
                            title = stringResource(Res.string.sync_later),
                            icon = Icons.Rounded.Schedule,
                            onClick = { viewModel.dismissIncoming(doc) },
                        )
                    }
                }
                item("push") {
                    val remaining = state.remainingSyncs
                    TvMenuItem(
                        title = stringResource(Res.string.sync_push),
                        description = stringResource(Res.string.sync_push_desc, state.pushableCount) + (remaining?.let {
                            "\n" + when {
                                // Unlimited (docs/prd-subscriptions.md §2.2): no counter.
                                state.unlimitedSyncs && it > 0 -> stringResource(Res.string.sync_unlimited)
                                it > 0 -> stringResource(Res.string.sync_remaining, it.toInt())
                                else -> stringResource(Res.string.sync_quota_reached)
                            }
                        } ?: ""),
                        icon = Icons.Rounded.CloudUpload,
                        onClick = viewModel::push,
                        trailing = { if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = TSColors.AccentCyan) },
                    )
                    if (state.skippedFileCount > 0) ConnectBody(stringResource(Res.string.sync_skipped_files, state.skippedFileCount))
                    // Rewarded ads only (PRD §3.3, §5.3).
                    if (remaining == 0L && state.canEarnSync) {
                        RewardTask(
                            availability = state.rewards,
                            title = stringResource(Res.string.sync_task_rewarded, (state.quota?.syncAds ?: 0).toInt()),
                            busy = state.busy,
                            onClick = viewModel::watchAdForSync,
                        )
                    }
                    if (remaining == 0L && !state.unlimitedSyncs) UpgradeUnlimitedItem()
                    val current = state.currentSync
                    ConnectBody(
                        if (current == null) stringResource(Res.string.sync_none)
                        else stringResource(Res.string.sync_current_from, current.fromDeviceName, current.createdAt.formatDynamic(TimeStampFormat.yyyyMMdd_HHmmss.formatStr)),
                    )
                }

                item("devices_title") {
                    ConnectSectionTitle(stringResource(Res.string.devices_title) + "  ·  " +
                        stringResource(Res.string.devices_count, state.devices.size, DeviceLimitPolicy.MAX_DEVICES))
                }
                state.devices.forEach { device ->
                    item("device_${device.id}") {
                        val mine = device.id == state.myDeviceId
                        TvMenuItem(
                            title = device.displayName() + if (mine) "  (" + stringResource(Res.string.devices_this_device) + ")" else "",
                            description = device.lastSeenText() +
                                if (mine) "" else "\n" + stringResource(Res.string.devices_sign_out_remote),
                            icon = if (device.platform == DevicePlatform.ANDROID_TV) Icons.Rounded.Tv else Icons.Rounded.Devices,
                            selected = mine,
                            onClick = { if (!mine) confirmRemove = device },
                        )
                    }
                }
            }
        }
    }

    sendPlaylist?.let { p ->
        TvSendDialog(
            command = PlaylistCommand(SharedPlaylist(name = p.name, url = p.url, epgUrls = p.epgUrls)),
            onDismiss = { sendPlaylist = null },
        )
    }
    if (fileNotice) {
        MessageDialog(stringResource(Res.string.send_tv_title), stringResource(Res.string.send_tv_file_unavailable)) { fileNotice = false }
    }
    confirmUnpair?.let { peer ->
        ConnectDialog(title = stringResource(Res.string.lan_tv_unpair), onDismissRequest = { confirmUnpair = null }) {
            ConnectBody(stringResource(Res.string.lan_tv_unpair_confirm, peer.name), color = TSColors.TextPrimary)
            val cancel = remember { FocusRequester() }
            ConnectButtonRow {
                // Cancel is focused: an accidental OK on the remote keeps the phone paired.
                ConnectButton(stringResource(Res.string.lan_cancel), { confirmUnpair = null }, Modifier.focusRequester(cancel))
                ConnectButton(
                    stringResource(Res.string.lan_tv_unpair),
                    {
                        confirmUnpair = null
                        scope.launch {
                            receiver.unpair(peer.id)
                            phoneFocus.remove(peer.id)
                            unpaired++
                        }
                    },
                    primary = true,
                )
            }
            LaunchedEffect(Unit) { cancel.requestFocusAfterLayout() }
        }
    }
    LaunchedEffect(unpaired) {
        if (unpaired == 0) return@LaunchedEffect
        val target = focusAfterUnpair?.let(phoneFocus::get) ?: firstItem
        target.requestFocusAfterLayout()
    }

    confirmRemove?.let { device ->
        ConnectDialog(title = stringResource(Res.string.devices_sign_out_remote), onDismissRequest = { confirmRemove = null }) {
            ConnectBody(stringResource(Res.string.devices_remove_confirm, device.displayName()), color = TSColors.TextPrimary)
            val confirm = remember { FocusRequester() }
            ConnectButtonRow {
                ConnectButton(stringResource(Res.string.lan_cancel), { confirmRemove = null })
                ConnectButton(
                    stringResource(Res.string.devices_sign_out_remote),
                    {
                        confirmRemove = null
                        viewModel.signOutRemote(device)
                    },
                    Modifier.focusRequester(confirm),
                    primary = true,
                )
            }
            LaunchedEffect(Unit) { confirm.requestFocusAfterLayout() }
        }
    }
    state.preview?.let { preview ->
        ConnectDialog(title = stringResource(Res.string.sync_replace_confirm_title), onDismissRequest = viewModel::cancelPreview) {
            ConnectBody(
                stringResource(
                    Res.string.sync_replace_confirm_message,
                    preview.plan.toRemove.size,
                    preview.plan.toRemove.joinToString(", ") { it.name }.ifEmpty { "—" },
                    preview.plan.toAdd.size,
                ),
                color = TSColors.TextPrimary,
            )
            val cancel = remember { FocusRequester() }
            ConnectButtonRow {
                ConnectButton(stringResource(Res.string.lan_cancel), viewModel::cancelPreview, Modifier.focusRequester(cancel))
                ConnectButton(stringResource(Res.string.sync_replace), viewModel::confirmPreview, primary = true)
            }
            // Destructive: focus starts on Cancel.
            LaunchedEffect(Unit) { cancel.requestFocusAfterLayout() }
        }
    }
    state.applying?.let { (done, total) ->
        ConnectDialog(title = stringResource(Res.string.sync_title), onDismissRequest = {}, dismissOnOutside = false) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = TSColors.AccentCyan)
                Text(stringResource(Res.string.sync_applying, done, total), color = TSColors.TextPrimary, fontSize = 16.sp)
            }
        }
    }
    state.message?.let { message ->
        val text = when (message) {
            is ConnectMessage.Pushed -> stringResource(Res.string.sync_pushed, message.count) +
                if (message.skippedFiles > 0) "\n" + stringResource(Res.string.sync_skipped_files, message.skippedFiles) else ""
            ConnectMessage.SyncNoneLeft -> stringResource(Res.string.sync_quota_reached)
            ConnectMessage.SyncTooLarge -> stringResource(Res.string.sync_too_large)
            ConnectMessage.SyncFailed -> stringResource(Res.string.sync_error)
            is ConnectMessage.Applied -> stringResource(Res.string.sync_applied, message.result.added, message.result.updated, message.result.removed) +
                if (message.result.failed.isNotEmpty()) "\n" + stringResource(Res.string.sync_apply_failed_some, message.result.failed.size) +
                    ": " + message.result.failed.joinToString(", ") else ""
            ConnectMessage.RewardGranted -> stringResource(Res.string.reward_granted)
            is ConnectMessage.RewardProgress -> stringResource(Res.string.sync_task_rewarded, message.watched.toInt())
            ConnectMessage.RewardUnavailable -> stringResource(Res.string.reward_ad_unavailable)
            ConnectMessage.RewardCapped -> stringResource(Res.string.reward_limit_reached)
            ConnectMessage.DevicesFailed -> stringResource(Res.string.devices_error)
        }
        MessageDialog(stringResource(Res.string.sync_title), text, viewModel::clearMessage)
    }
}

/** A one-button message dialog (OK focused, for the D-pad). */
@Composable
fun MessageDialog(title: String, text: String, onDismiss: () -> Unit) {
    ConnectDialog(title = title, onDismissRequest = onDismiss) {
        ConnectBody(text, color = TSColors.TextPrimary)
        val ok = remember { FocusRequester() }
        ConnectButtonRow { ConnectButton(stringResource(Res.string.ok), onDismiss, Modifier.focusRequester(ok), primary = true) }
        LaunchedEffect(Unit) { ok.requestFocusAfterLayout() }
    }
}
