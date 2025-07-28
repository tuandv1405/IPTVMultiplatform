package tss.t.tsiptv.ui.screens.home.homeiptvlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.bottom_sheet_add_iptv
import tsiptv.composeapp.generated.resources.bottom_sheet_change_language
import tsiptv.composeapp.generated.resources.bottom_sheet_import_playlist
import tsiptv.composeapp.generated.resources.bottom_sheet_refresh_channel
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.IconTitleActionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingOptionsBottomSheet(
    parentNavController: NavHostController,
    onDismissRequest: () -> Unit = {},
    onHomeEvent: (HomeEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
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
                imageVector = Icons.Rounded.Add,
                title = stringResource(Res.string.bottom_sheet_add_iptv),
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
            IconTitleActionItem(
                imageVector = Icons.Rounded.Language,
                title = stringResource(Res.string.bottom_sheet_change_language),
                onClick = {
                    parentNavController.navigate(NavRoutes.LanguageSettings())
                    onDismissRequest()
                }
            )
        }
    }
}
