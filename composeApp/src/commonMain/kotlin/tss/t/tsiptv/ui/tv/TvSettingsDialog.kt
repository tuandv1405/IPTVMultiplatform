package tss.t.tsiptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChangeCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.addons_title
import androidx.compose.material.icons.rounded.Extension
import tsiptv.composeapp.generated.resources.bottom_sheet_change_language
import tsiptv.composeapp.generated.resources.bottom_sheet_import_playlist
import tsiptv.composeapp.generated.resources.bottom_sheet_refresh_channel
import tsiptv.composeapp.generated.resources.bottom_sheet_select_playlist
import tsiptv.composeapp.generated.resources.btn_logout_cancel
import tsiptv.composeapp.generated.resources.btn_logout_ok
import tsiptv.composeapp.generated.resources.channel_count_format
import tsiptv.composeapp.generated.resources.login
import tsiptv.composeapp.generated.resources.logout_button_title
import tsiptv.composeapp.generated.resources.logout_dialog_message
import tsiptv.composeapp.generated.resources.logout_dialog_title
import tsiptv.composeapp.generated.resources.rate_app_desc
import tsiptv.composeapp.generated.resources.rate_app_title
import tsiptv.composeapp.generated.resources.settings_title
import tsiptv.composeapp.generated.resources.ui_mode_title
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.core.database.entity.toPlaylist
import tss.t.tsiptv.core.uimode.LocalUiMode
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.settings.UiModeSelectionDialog
import tss.t.tsiptv.ui.screens.settings.titleRes
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.widgets.TSDialog

private enum class TvSettingsPage { MENU, PLAYLISTS, UI_MODE, LOGOUT }

/**
 * TV counterpart of the phone's settings bottom sheet, plus the account entry:
 * log in when signed out (the TV layout does not require it), log out otherwise.
 */
@Composable
fun TvSettingsDialog(
    homeUiState: HomeUiState,
    totalPlaylist: List<PlaylistWithChannelCount>,
    onHomeEvent: (HomeEvent) -> Unit,
    onImportPlaylist: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    signedInEmail: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismissRequest: () -> Unit,
    onRateApp: (() -> Unit)? = null,
    onOpenAddons: (() -> Unit)? = null,
) {
    var page by remember { mutableStateOf(TvSettingsPage.MENU) }

    when (page) {
        TvSettingsPage.MENU -> SettingsMenu(
            hasPlaylist = homeUiState.playListId != null,
            onSelectPlaylist = { page = TvSettingsPage.PLAYLISTS },
            onImportPlaylist = {
                onDismissRequest()
                onImportPlaylist()
            },
            onRefresh = {
                onHomeEvent(HomeEvent.RefreshIPTVSource)
                onDismissRequest()
            },
            onLanguage = {
                onDismissRequest()
                onOpenLanguageSettings()
            },
            onUiMode = { page = TvSettingsPage.UI_MODE },
            signedInEmail = signedInEmail,
            onLogin = {
                onDismissRequest()
                onLogin()
            },
            onLogout = { page = TvSettingsPage.LOGOUT },
            onRateApp = onRateApp?.let { rate ->
                {
                    onDismissRequest()
                    rate()
                }
            },
            onAddons = onOpenAddons?.let { open ->
                {
                    onDismissRequest()
                    open()
                }
            },
            onDismissRequest = onDismissRequest,
        )

        TvSettingsPage.PLAYLISTS -> PlaylistPicker(
            totalPlaylist = totalPlaylist,
            currentPlaylistId = homeUiState.playListId,
            onPick = {
                onHomeEvent(HomeEvent.OnRequestChangePlaylist(it.playlist.toPlaylist()))
                onDismissRequest()
            },
            onDismissRequest = { page = TvSettingsPage.MENU },
        )

        TvSettingsPage.UI_MODE -> UiModeSelectionDialog(onDismissRequest = onDismissRequest)

        TvSettingsPage.LOGOUT -> TSDialog(
            title = stringResource(Res.string.logout_dialog_title),
            message = stringResource(Res.string.logout_dialog_message),
            positiveButtonText = stringResource(Res.string.btn_logout_ok),
            negativeButtonText = stringResource(Res.string.btn_logout_cancel),
            onPositiveClick = {
                onDismissRequest()
                onLogout()
            },
            onNegativeClick = { page = TvSettingsPage.MENU },
            onDismissRequest = { page = TvSettingsPage.MENU },
        )
    }
}

@Composable
private fun TvDialogPanel(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 520.dp)
                .background(TSColors.SecondaryBackgroundColor, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = TSColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingsMenu(
    hasPlaylist: Boolean,
    onSelectPlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    onRefresh: () -> Unit,
    onLanguage: () -> Unit,
    onUiMode: () -> Unit,
    signedInEmail: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRateApp: (() -> Unit)?,
    onDismissRequest: () -> Unit,
    onAddons: (() -> Unit)? = null,
) {
    val firstItem = remember { FocusRequester() }
    TvDialogPanel(
        title = stringResource(Res.string.settings_title),
        onDismissRequest = onDismissRequest
    ) {
        // Scrollable: on a phone held in landscape (or a small TV UI scale) the
        // menu is taller than the screen, and D-pad focus must be able to bring
        // the last rows (Rate, Login/Logout) into view.
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TvMenuItem(
                title = stringResource(Res.string.bottom_sheet_select_playlist),
                icon = Icons.Rounded.ChangeCircle,
                onClick = onSelectPlaylist,
                modifier = Modifier.focusRequester(firstItem)
            )
            TvMenuItem(
                title = stringResource(Res.string.bottom_sheet_import_playlist),
                icon = Icons.Rounded.FileDownload,
                onClick = onImportPlaylist
            )
            // RefreshIPTVSource assumes a current playlist.
            if (hasPlaylist) {
                TvMenuItem(
                    title = stringResource(Res.string.bottom_sheet_refresh_channel),
                    icon = Icons.Rounded.Refresh,
                    onClick = onRefresh
                )
            }
            // F2: Settings → Addons (the addon manager, TV layout).
            if (onAddons != null) {
                TvMenuItem(
                    title = stringResource(Res.string.addons_title),
                    icon = Icons.Rounded.Extension,
                    onClick = onAddons
                )
            }
            TvMenuItem(
                title = stringResource(Res.string.bottom_sheet_change_language),
                icon = Icons.Rounded.Language,
                onClick = onLanguage
            )
            TvMenuItem(
                title = stringResource(Res.string.ui_mode_title),
                description = stringResource(LocalUiMode.current.titleRes),
                icon = Icons.Rounded.Tv,
                onClick = onUiMode
            )
            if (onRateApp != null) {
                TvMenuItem(
                    title = stringResource(Res.string.rate_app_title),
                    description = stringResource(Res.string.rate_app_desc),
                    icon = Icons.Rounded.Star,
                    onClick = onRateApp
                )
            }
            // The TV layout does not require an account; signing in is optional.
            if (signedInEmail == null) {
                TvMenuItem(
                    title = stringResource(Res.string.login),
                    icon = Icons.AutoMirrored.Rounded.Login,
                    onClick = onLogin
                )
            } else {
                TvMenuItem(
                    title = stringResource(Res.string.logout_button_title),
                    description = signedInEmail,
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    onClick = onLogout
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        firstItem.requestFocusAfterLayout()
    }
}

@Composable
private fun PlaylistPicker(
    totalPlaylist: List<PlaylistWithChannelCount>,
    currentPlaylistId: String?,
    onPick: (PlaylistWithChannelCount) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val currentItem = remember { FocusRequester() }
    TvDialogPanel(
        title = stringResource(Res.string.bottom_sheet_select_playlist),
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(totalPlaylist, key = { it.playlist.id }) { item ->
                val isCurrent = item.playlist.id == currentPlaylistId
                TvMenuItem(
                    title = item.playlist.name,
                    description = stringResource(Res.string.channel_count_format, item.channelCount),
                    // F3: a TS IPTV Source has its own icon.
                    icon = if (tss.t.tsiptv.core.tsiptv.TsiptvSourceIds.isSourcePlaylist(item.playlist.id)) {
                        androidx.compose.material.icons.Icons.Rounded.Dashboard
                    } else Icons.AutoMirrored.Rounded.PlaylistPlay,
                    selected = isCurrent,
                    modifier = if (isCurrent) Modifier.focusRequester(currentItem) else Modifier,
                    onClick = { onPick(item) },
                    trailing = {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = TSColors.AccentGreen
                            )
                        }
                    }
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        currentItem.requestFocusAfterLayout()
    }
}
