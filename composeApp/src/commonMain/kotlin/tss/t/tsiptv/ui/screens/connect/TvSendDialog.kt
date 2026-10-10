package tss.t.tsiptv.ui.screens.connect

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.lan_cancel
import tsiptv.composeapp.generated.resources.lan_cast_none_found
import tsiptv.composeapp.generated.resources.lan_cast_searching
import tsiptv.composeapp.generated.resources.lan_cast_sent
import tsiptv.composeapp.generated.resources.lan_cast_title
import tsiptv.composeapp.generated.resources.lan_close
import tsiptv.composeapp.generated.resources.lan_conditions_body
import tsiptv.composeapp.generated.resources.lan_conditions_title
import tsiptv.composeapp.generated.resources.lan_connect
import tsiptv.composeapp.generated.resources.lan_connect_by_ip
import tsiptv.composeapp.generated.resources.lan_error_clock
import tsiptv.composeapp.generated.resources.lan_error_rejected
import tsiptv.composeapp.generated.resources.lan_ip_hint
import tsiptv.composeapp.generated.resources.lan_ip_invalid
import tsiptv.composeapp.generated.resources.lan_not_supported
import tsiptv.composeapp.generated.resources.lan_pair_busy
import tsiptv.composeapp.generated.resources.lan_pair_enter_code
import tsiptv.composeapp.generated.resources.lan_pair_failed
import tsiptv.composeapp.generated.resources.lan_pair_locked
import tsiptv.composeapp.generated.resources.lan_pair_title
import tsiptv.composeapp.generated.resources.lan_pair_wrong_code
import tsiptv.composeapp.generated.resources.lan_paired_badge
import tsiptv.composeapp.generated.resources.lan_send_failed
import tsiptv.composeapp.generated.resources.reward_ad_unavailable
import tsiptv.composeapp.generated.resources.reward_granted
import tsiptv.composeapp.generated.resources.reward_limit_reached
import tsiptv.composeapp.generated.resources.reward_unavailable_consent
import tsiptv.composeapp.generated.resources.reward_unavailable_first_day
import tsiptv.composeapp.generated.resources.reward_unavailable_platform
import tsiptv.composeapp.generated.resources.reward_unavailable_tv
import tsiptv.composeapp.generated.resources.send_tv_quota_reached
import tsiptv.composeapp.generated.resources.send_tv_remaining
import tsiptv.composeapp.generated.resources.send_tv_sent
import tsiptv.composeapp.generated.resources.send_tv_sign_in
import tsiptv.composeapp.generated.resources.send_tv_sign_in_button
import tsiptv.composeapp.generated.resources.send_tv_not_counted
import tsiptv.composeapp.generated.resources.lan_pair_code_expired
import tsiptv.composeapp.generated.resources.lan_tv_busy
import tsiptv.composeapp.generated.resources.lan_pair_open_tv_screen
import tsiptv.composeapp.generated.resources.send_tv_task_rewarded
import tsiptv.composeapp.generated.resources.send_tv_tasks
import tsiptv.composeapp.generated.resources.send_tv_title
import tsiptv.composeapp.generated.resources.send_tv_unlimited
import tsiptv.composeapp.generated.resources.quota_upgrade_unlimited
import tsiptv.composeapp.generated.resources.quota_upgrade_unlimited_desc
import androidx.compose.material.icons.rounded.AllInclusive
import tss.t.tsiptv.feature.account.RewardAvailability
import tss.t.tsiptv.feature.lan.LanCommand
import tss.t.tsiptv.feature.lan.PlaylistCommand
import tss.t.tsiptv.feature.lan.TvSendMessage
import tss.t.tsiptv.feature.lan.TvSendStep
import tss.t.tsiptv.feature.lan.TvSendViewModel
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvMenuItem
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout

/** Opens the sign-in screen (provided at the app root; null where there is none). */
val LocalSignInAction = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Phone: pick a TV, pair on first use, send [command] (a cast or a playlist). PRD §2.1 / §3.1.
 */
@Composable
fun TvSendDialog(command: LanCommand, onDismiss: () -> Unit) {
    val viewModel = koinViewModel<TvSendViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(command) {
        viewModel.open(command)
        onDispose { viewModel.close() }
    }
    val isPlaylist = command is PlaylistCommand
    val title = stringResource(if (isPlaylist) Res.string.send_tv_title else Res.string.lan_cast_title)

    ConnectDialog(title = title, onDismissRequest = onDismiss) {
        when (val step = state.step) {
            TvSendStep.Connecting, TvSendStep.Sending -> Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) { CircularProgressIndicator(color = TSColors.AccentCyan) }

            is TvSendStep.EnterCode -> EnterCode(
                tvName = step.tvName,
                error = step.error,
                onSubmit = viewModel::submitCode,
                onCancel = viewModel::cancelPairing,
            )

            is TvSendStep.Done -> {
                ConnectBody(
                    stringResource(if (isPlaylist) Res.string.send_tv_sent else Res.string.lan_cast_sent, step.tvName),
                    color = TSColors.TextPrimary,
                )
                state.message?.let { ConnectBody(stringResource(messageText(it)), color = TSColors.AccentCyan) }
                val close = remember { FocusRequester() }
                ConnectButtonRow { ConnectButton(stringResource(Res.string.lan_close), onDismiss, Modifier.focusRequester(close), primary = true) }
                LaunchedEffect(Unit) { close.requestFocusAfterLayout() }
            }

            TvSendStep.Pick -> PickTv(
                state = state,
                isPlaylist = isPlaylist,
                viewModel = viewModel,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun PickTv(
    state: tss.t.tsiptv.feature.lan.TvSendUiState,
    isPlaylist: Boolean,
    viewModel: TvSendViewModel,
    onDismiss: () -> Unit,
) {
    if (!state.supported) {
        ConnectBody(stringResource(Res.string.lan_not_supported))
        ConnectButtonRow { ConnectButton(stringResource(Res.string.lan_close), onDismiss) }
        return
    }
    val signedOut = isPlaylist && state.quota?.signedIn == false
    // Signed out: the sign-in line below says it once, with its button.
    state.message?.takeUnless { signedOut && it == TvSendMessage.SIGN_IN }?.let {
        ConnectBody(stringResource(messageText(it)), color = TSColors.AccentCyan)
    }

    if (isPlaylist) {
        state.quota?.let { q ->
            when {
                !q.signedIn -> {
                    ConnectBody(stringResource(Res.string.send_tv_sign_in))
                    LocalSignInAction.current?.let { signIn ->
                        ConnectButtonRow {
                            ConnectButton(stringResource(Res.string.send_tv_sign_in_button), {
                                onDismiss()
                                signIn()
                            }, primary = true)
                        }
                    }
                }
                // Unlimited (docs/prd-subscriptions.md §2.2): no counter and no rewarded task.
                q.unlimited && (q.remaining == null || q.remaining > 0) -> ConnectBody(stringResource(Res.string.send_tv_unlimited))
                q.remaining != null -> {
                    ConnectBody(
                        if (q.remaining > 0) stringResource(Res.string.send_tv_remaining, q.remaining.toInt())
                        else stringResource(Res.string.send_tv_quota_reached),
                    )
                    // Rewarded ads only (PRD §3.3): never banner clicks or timed interstitials.
                    if (q.canEarn && q.remaining == 0L) {
                        ConnectSectionTitle(stringResource(Res.string.send_tv_tasks))
                        RewardTask(
                            availability = q.rewards,
                            title = stringResource(Res.string.send_tv_task_rewarded),
                            busy = state.busyReward,
                            onClick = viewModel::watchAdForSend,
                        )
                    }
                    if (!q.unlimited && q.remaining == 0L) UpgradeUnlimitedItem(onBeforeOpen = onDismiss)
                }
            }
        }
    }

    val firstTarget = remember { FocusRequester() }
    var showHelp by remember { mutableStateOf(false) }
    // After a few seconds with nothing found, "No TV found" and the conditions replace the spinner.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6_000)
        if (viewModel.state.value.targets.isEmpty()) showHelp = true
    }
    if (state.targets.isEmpty() && !showHelp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            if (state.searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TSColors.AccentCyan)
            Spacer(Modifier.width(10.dp))
            ConnectBody(stringResource(Res.string.lan_cast_searching))
        }
    }
    state.targets.forEachIndexed { index, target ->
        TvMenuItem(
            title = target.device.name,
            description = if (target.paired) stringResource(Res.string.lan_paired_badge) else null,
            icon = Icons.Rounded.Tv,
            modifier = if (index == 0) Modifier.focusRequester(firstTarget) else Modifier,
            onClick = { viewModel.pick(target.device) },
            trailing = { if (target.paired) Icon(Icons.Rounded.CheckCircle, null, tint = TSColors.AccentGreen) },
        )
    }
    LaunchedEffect(state.targets.isNotEmpty()) { if (state.targets.isNotEmpty()) firstTarget.requestFocusAfterLayout() }

    if (state.targets.isEmpty() && showHelp) ConnectBody(stringResource(Res.string.lan_cast_none_found), color = TSColors.TextPrimary)
    if (showHelp) {
        ConnectSectionTitle(stringResource(Res.string.lan_conditions_title))
        ConnectBody(stringResource(Res.string.lan_conditions_body))
    } else {
        TvMenuItem(title = stringResource(Res.string.lan_conditions_title), onClick = { showHelp = true })
    }

    ConnectSectionTitle(stringResource(Res.string.lan_connect_by_ip))
    var address by remember { mutableStateOf("") }
    OutlinedTextField(
        value = address,
        onValueChange = { address = it.trim().take(64) },
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.lan_ip_hint), fontSize = 14.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TSColors.TextPrimary,
            unfocusedTextColor = TSColors.TextPrimary,
            focusedBorderColor = TSColors.AccentCyan,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    ConnectButtonRow {
        ConnectButton(stringResource(Res.string.lan_close), onDismiss)
        ConnectButton(stringResource(Res.string.lan_connect), { viewModel.connectByIp(address) }, primary = true, enabled = address.isNotBlank())
    }
}

@Composable
private fun EnterCode(
    tvName: String,
    error: TvSendMessage?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    ConnectBody(stringResource(Res.string.lan_pair_title, tvName), color = TSColors.TextPrimary)
    ConnectBody(stringResource(Res.string.lan_pair_enter_code))
    error?.let { ConnectBody(stringResource(messageText(it)), color = TSColors.AccentCyan) }
    OutlinedTextField(
        value = code,
        onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, letterSpacing = 8.sp, color = TSColors.TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TSColors.AccentCyan),
        modifier = Modifier.fillMaxWidth().focusRequester(field),
    )
    LaunchedEffect(Unit) { field.requestFocusAfterLayout() }
    ConnectButtonRow {
        ConnectButton(stringResource(Res.string.lan_cancel), onCancel)
        ConnectButton(stringResource(Res.string.lan_connect), { onSubmit(code) }, primary = true, enabled = code.length == 6)
    }
}

internal fun messageText(message: TvSendMessage): StringResource = when (message) {
    TvSendMessage.UNREACHABLE -> Res.string.lan_send_failed
    TvSendMessage.WRONG_CODE -> Res.string.lan_pair_wrong_code
    TvSendMessage.PAIR_FAILED -> Res.string.lan_pair_failed
    TvSendMessage.PAIR_BUSY -> Res.string.lan_pair_busy
    TvSendMessage.PAIR_LOCKED -> Res.string.lan_pair_locked
    TvSendMessage.CLOCK -> Res.string.lan_error_clock
    TvSendMessage.REJECTED -> Res.string.lan_error_rejected
    TvSendMessage.NOT_SUPPORTED -> Res.string.lan_not_supported
    TvSendMessage.IP_INVALID -> Res.string.lan_ip_invalid
    TvSendMessage.QUOTA_REACHED -> Res.string.send_tv_quota_reached
    TvSendMessage.SIGN_IN -> Res.string.send_tv_sign_in
    TvSendMessage.REWARD_GRANTED -> Res.string.reward_granted
    TvSendMessage.REWARD_UNAVAILABLE -> Res.string.reward_ad_unavailable
    TvSendMessage.REWARD_CAPPED -> Res.string.reward_limit_reached
    TvSendMessage.CODE_EXPIRED -> Res.string.lan_pair_code_expired
    TvSendMessage.TV_BUSY -> Res.string.lan_tv_busy
    TvSendMessage.OPEN_TV_SCREEN -> Res.string.lan_pair_open_tv_screen
    TvSendMessage.SEND_NOT_COUNTED -> Res.string.send_tv_not_counted
}

/** "Get Unlimited" on a used-up quota (docs/prd-subscriptions.md §3.2): opens the plans screen. */
@Composable
fun UpgradeUnlimitedItem(onBeforeOpen: () -> Unit = {}) {
    val open = tss.t.tsiptv.ui.screens.plans.LocalOpenPlans.current ?: return
    TvMenuItem(
        title = stringResource(Res.string.quota_upgrade_unlimited),
        description = stringResource(Res.string.quota_upgrade_unlimited_desc),
        icon = Icons.Rounded.AllInclusive,
        onClick = {
            onBeforeOpen()
            open()
        },
    )
}

/**
 * A rewarded-ad task: the button when ads may show, otherwise one line saying why not (24 h ad-free
 * start, no consent, TV layout, platform).
 */
@Composable
fun RewardTask(availability: RewardAvailability, title: String, busy: Boolean, onClick: () -> Unit) {
    if (availability == RewardAvailability.AVAILABLE) {
        TvMenuItem(
            title = title,
            onClick = { if (!busy) onClick() },
            trailing = { if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = TSColors.AccentCyan) },
        )
    } else {
        ConnectBody(
            stringResource(
                when (availability) {
                    RewardAvailability.AD_FREE_PERIOD -> Res.string.reward_unavailable_first_day
                    RewardAvailability.NO_CONSENT -> Res.string.reward_unavailable_consent
                    RewardAvailability.TV_LAYOUT -> Res.string.reward_unavailable_tv
                    else -> Res.string.reward_unavailable_platform
                }
            )
        )
    }
}