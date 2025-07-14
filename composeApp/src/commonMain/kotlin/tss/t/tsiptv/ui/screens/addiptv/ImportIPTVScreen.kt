package tss.t.tsiptv.ui.screens.addiptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.add_iptv_source_name_label_title
import tsiptv.composeapp.generated.resources.add_iptv_source_name_placeholder_title
import tsiptv.composeapp.generated.resources.add_iptv_source_title
import tsiptv.composeapp.generated.resources.add_iptv_source_url_label_title
import tsiptv.composeapp.generated.resources.add_iptv_source_url_placeholder_title
import tsiptv.composeapp.generated.resources.btn_add_iptv_source_title
import tsiptv.composeapp.generated.resources.cancel_parsing_btn_cancel
import tsiptv.composeapp.generated.resources.cancel_parsing_btn_ok
import tsiptv.composeapp.generated.resources.cancel_parsing_message
import tsiptv.composeapp.generated.resources.cancel_parsing_title
import tsiptv.composeapp.generated.resources.error_dialog_title
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.tips_add_iptv_source_desc
import tsiptv.composeapp.generated.resources.tips_add_iptv_source_title
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.AppLogoCircle
import tss.t.tsiptv.ui.widgets.IPTVAppBar
import tss.t.tsiptv.ui.widgets.PositiveButton
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.ui.widgets.TSTextField
import tss.t.tsiptv.ui.widgets.Tips
import tss.t.tsiptv.utils.isValidUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ImportIPTVScreen(
    hazeState: HazeState = remember {
        HazeState()
    },
    homeUiState: HomeUiState = HomeUiState(),
    initSourceName: String = "",
    initSourceUrl: String = "",
    onEvent: (HomeEvent) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var inputSourceName by remember { mutableStateOf(initSourceName) }
    var inputSourceUrl by remember { mutableStateOf(initSourceUrl) }

    val showProgress = remember(homeUiState.isLoading) {
        homeUiState.isLoading
    }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showError by remember(homeUiState.error) {
        mutableStateOf(homeUiState.error != null)
    }

    if (showCancelDialog) {
        TSDialog(
            title = stringResource(Res.string.cancel_parsing_title),
            message = stringResource(Res.string.cancel_parsing_message),
            positiveButtonText = stringResource(Res.string.cancel_parsing_btn_cancel),
            negativeButtonText = stringResource(Res.string.cancel_parsing_btn_ok),
            onPositiveClick = {
                showCancelDialog = false
            },
            onNegativeClick = {
                showCancelDialog = false
                onEvent(HomeEvent.OnCancelParseIPTVSource)
            }
        )
    }

    if (showError) {
        TSDialog(
            title = stringResource(Res.string.error_dialog_title),
            message = homeUiState.error?.message ?: "",
            positiveButtonText = stringResource(Res.string.ok),
            onPositiveClick = {
                showError = false
            },
            onDismissRequest = {
                onEvent(HomeEvent.OnDismissErrorDialog)
            }
        )
    }

    val progressIndicator: (@Composable () -> Unit)? = remember(showProgress) {
        if (showProgress) {
            {
                AnimatedVisibility(
                    modifier = Modifier.height(20.dp),
                    visible = showProgress
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TSColors.DeepBlue,
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        } else null
    }

    Scaffold(
        topBar = {
            Column {
                IPTVAppBar(
                    modifier = Modifier.hazeEffect(hazeState),
                    title = stringResource(Res.string.add_iptv_source_title),
                    onBackClick = {
                        onEvent(HomeEvent.OnBackPressed)
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.height(1.dp),
                    color = TSColors.strokeColor
                )
            }
        },
        containerColor = TSColors.BackgroundColor,
        modifier = Modifier.clickable(
            interactionSource = remember {
                MutableInteractionSource()
            },
            indication = null,
            onClick = {
                focusManager.clearFocus(false)
            }
        )
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .hazeSource(hazeState)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("SpacerTop") {
                Spacer(
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .height(32.dp)
                )
            }
            item("Logo") {
                AppLogoCircle(
                    modifier = Modifier.padding(top = 16.dp),
                    size = 64.dp,
                    iconSize = 24.dp,
                    shape = TSShapes.roundedShape16
                )
            }

            item("InputSourceName") {
                TSTextField(
                    modifier = Modifier.padding(top = 52.dp)
                        .padding(horizontal = 20.dp),
                    value = inputSourceName,
                    label = stringResource(Res.string.add_iptv_source_name_label_title),
                    onValueChange = {
                        inputSourceName = it
                    },
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.add_iptv_source_name_placeholder_title),
                            color = TSColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            item("InputSourceLink") {
                TSTextField(
                    modifier = Modifier.padding(top = 12.dp)
                        .padding(horizontal = 20.dp), value = inputSourceUrl,
                    label = stringResource(Res.string.add_iptv_source_url_label_title),
                    onValueChange = {
                        inputSourceUrl = it.trim()
                    },
                    isError = remember(inputSourceUrl) {
                        inputSourceUrl.isNotEmpty() && !inputSourceUrl.isValidUrl()
                    },
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.add_iptv_source_url_placeholder_title),
                            color = TSColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            item("BtnAddSource") {
                PositiveButton(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    text = stringResource(Res.string.btn_add_iptv_source_title),
                    icon = progressIndicator,
                ) {
                    if (!showProgress) {
                        onEvent(HomeEvent.OnParseIPTVSource(inputSourceName, inputSourceUrl))
                    } else {
                        showCancelDialog = true
                    }
                }

            }

            item("Tips") {
                Tips(
                    modifier = Modifier.padding(top = 32.dp)
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    title = stringResource(Res.string.tips_add_iptv_source_title),
                    desc = stringResource(Res.string.tips_add_iptv_source_desc)
                )
            }
        }
    }
}
