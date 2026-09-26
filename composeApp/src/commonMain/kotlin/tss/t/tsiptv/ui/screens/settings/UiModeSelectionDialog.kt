package tss.t.tsiptv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ui_mode_auto
import tsiptv.composeapp.generated.resources.ui_mode_auto_desc
import tsiptv.composeapp.generated.resources.ui_mode_phone
import tsiptv.composeapp.generated.resources.ui_mode_phone_desc
import tsiptv.composeapp.generated.resources.ui_mode_title
import tsiptv.composeapp.generated.resources.ui_mode_tv
import tsiptv.composeapp.generated.resources.ui_mode_tv_desc
import tss.t.tsiptv.core.uimode.LocalUiMode
import tss.t.tsiptv.core.uimode.UiMode
import tss.t.tsiptv.core.uimode.UiModeRepository
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvMenuItem
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout

val UiMode.titleRes: StringResource
    get() = when (this) {
        UiMode.AUTO -> Res.string.ui_mode_auto
        UiMode.PHONE -> Res.string.ui_mode_phone
        UiMode.TV -> Res.string.ui_mode_tv
    }

private val UiMode.descriptionRes: StringResource
    get() = when (this) {
        UiMode.AUTO -> Res.string.ui_mode_auto_desc
        UiMode.PHONE -> Res.string.ui_mode_phone_desc
        UiMode.TV -> Res.string.ui_mode_tv_desc
    }

/**
 * Lets the user pick Automatic / Phone / TV. Saving is enough: App observes the
 * stored mode and swaps layouts, so the dialog only has to dismiss itself.
 * Rows are TV menu items, which are focusable for a remote and tappable on a phone.
 */
@Composable
fun UiModeSelectionDialog(onDismissRequest: () -> Unit) {
    val repository = koinInject<UiModeRepository>()
    val currentMode = LocalUiMode.current
    val scope = rememberCoroutineScope()
    val currentFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .background(TSColors.SecondaryBackgroundColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.ui_mode_title),
                color = TSColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            UiMode.entries.forEach { mode ->
                val isCurrent = mode == currentMode
                TvMenuItem(
                    title = stringResource(mode.titleRes),
                    description = stringResource(mode.descriptionRes),
                    icon = when (mode) {
                        UiMode.AUTO -> Icons.Rounded.AutoMode
                        UiMode.PHONE -> Icons.Rounded.PhoneAndroid
                        UiMode.TV -> Icons.Rounded.Tv
                    },
                    selected = isCurrent,
                    modifier = if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier,
                    onClick = {
                        scope.launch {
                            repository.setUiMode(mode)
                            onDismissRequest()
                        }
                    },
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

    // Start the remote on the active choice instead of nowhere.
    LaunchedEffect(Unit) {
        currentFocus.requestFocusAfterLayout()
    }
}
