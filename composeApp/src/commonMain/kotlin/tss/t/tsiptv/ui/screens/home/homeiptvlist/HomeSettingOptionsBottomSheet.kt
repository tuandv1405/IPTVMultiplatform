package tss.t.tsiptv.ui.screens.home.homeiptvlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChangeCircle
import tsiptv.composeapp.generated.resources.connect_desc
import tsiptv.composeapp.generated.resources.connect_title
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.addons_title
import tsiptv.composeapp.generated.resources.bottom_sheet_change_language
import tsiptv.composeapp.generated.resources.bottom_sheet_import_playlist
import tsiptv.composeapp.generated.resources.bottom_sheet_refresh_channel
import tsiptv.composeapp.generated.resources.bottom_sheet_select_playlist
import tsiptv.composeapp.generated.resources.ui_mode_title
import tss.t.tsiptv.core.uimode.LocalUiMode
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.settings.UiModeSelectionDialog
import tss.t.tsiptv.ui.screens.settings.titleRes
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.IconTitleActionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingOptionsBottomSheet(
    parentNavController: NavHostController,
    onDismissRequest: () -> Unit = {},
    onHomeEvent: (HomeEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()
    var showUiModeDialog by remember { mutableStateOf(false) }

    if (showUiModeDialog) {
        UiModeSelectionDialog(
            onDismissRequest = {
                showUiModeDialog = false
                onDismissRequest()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = TSShapes.roundShapeTop16,
        containerColor = TSColors.SecondaryBackgroundColor,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(TSShapes.roundedShape4),
                color = TSColors.White.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            IconTitleActionItem(
                imageVector = Icons.Rounded.ChangeCircle,
                title = stringResource(Res.string.bottom_sheet_select_playlist),
                onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        onDismissRequest()
                        onHomeEvent(HomeEvent.OnChangeIPTVSourcePressed)
                    }
                }
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = TSColors.White.copy(alpha = 0.08f)
            )
            IconTitleActionItem(
                imageVector = Icons.Rounded.FileDownload,
                title = stringResource(Res.string.bottom_sheet_import_playlist),
                onClick = {
                    parentNavController.navigate(NavRoutes.ImportIptv)
                    onDismissRequest()
                }
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = TSColors.White.copy(alpha = 0.08f)
            )
            IconTitleActionItem(
                imageVector = Icons.Rounded.Refresh,
                title = stringResource(Res.string.bottom_sheet_refresh_channel),
                onClick = {
                    onHomeEvent(HomeEvent.RefreshIPTVSource)
                    onDismissRequest()
                }
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = TSColors.White.copy(alpha = 0.08f)
            )
            // Stremio-compatible addons (same place as in the TV settings dialog).
            IconTitleActionItem(
                imageVector = Icons.Rounded.Extension,
                title = stringResource(Res.string.addons_title),
                onClick = {
                    parentNavController.navigate(NavRoutes.Addons)
                    onDismissRequest()
                }
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = TSColors.White.copy(alpha = 0.08f)
            )
            // Play on TV, send playlists, device sync, signed-in devices (prd-tv-cast-and-sync).
            IconTitleActionItem(
                imageVector = Icons.Rounded.Cast,
                title = stringResource(Res.string.connect_title),
                description = stringResource(Res.string.connect_desc),
                onClick = {
                    parentNavController.navigate(NavRoutes.Connect)
                    onDismissRequest()
                }
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = TSColors.White.copy(alpha = 0.08f)
            )
            IconTitleActionItem(
                imageVector = Icons.Rounded.Language,
                title = stringResource(Res.string.bottom_sheet_change_language),
                onClick = {
                    parentNavController.navigate(NavRoutes.LanguageSettings)
                    onDismissRequest()
                }
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = TSColors.White.copy(alpha = 0.08f)
            )
            IconTitleActionItem(
                imageVector = Icons.Rounded.Tv,
                title = stringResource(Res.string.ui_mode_title),
                description = stringResource(LocalUiMode.current.titleRes),
                onClick = { showUiModeDialog = true }
            )
        }
    }
}
