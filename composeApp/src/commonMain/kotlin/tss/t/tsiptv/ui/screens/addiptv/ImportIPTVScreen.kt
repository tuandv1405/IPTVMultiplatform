package tss.t.tsiptv.ui.screens.addiptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.pointer.pointerInput
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
import tsiptv.composeapp.generated.resources.import_file_unavailable_tv
import tsiptv.composeapp.generated.resources.import_from_file_title
import tsiptv.composeapp.generated.resources.ok
import tss.t.tsiptv.platform.rememberPlaylistFilePicker
import tss.t.tsiptv.ui.widgets.GrayButton
import tsiptv.composeapp.generated.resources.tips_add_iptv_source_desc
import tsiptv.composeapp.generated.resources.tips_add_iptv_source_title
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.ui.screens.home.SourceImportState
import tss.t.tsiptv.ui.screens.source.SourceAdultIncludeDialog
import tss.t.tsiptv.ui.screens.source.SourceErrorDialog
import tss.t.tsiptv.ui.screens.source.SourceFetchingDialog
import tss.t.tsiptv.ui.screens.source.SourcePreviewDialog
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.AppLogoCircle
import tss.t.tsiptv.ui.widgets.TSAppBarXBackIcon
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
    // Explicit focus order for the D-pad: back → name → link → add → import from file.
    // The top bar sits in a separate Scaffold slot, and 2D search from it never reached
    // the fields below on Android TV.
    val nameFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val addButtonFocus = remember { FocusRequester() }
    val fileButtonFocus = remember { FocusRequester() }
    var inputSourceName by remember { mutableStateOf(initSourceName) }
    var inputSourceUrl by remember { mutableStateOf(initSourceUrl) }
    val filePicker = rememberPlaylistFilePicker { picked ->
        onEvent(HomeEvent.OnImportFile(inputSourceName.trim(), picked))
    }

    homeUiState.pendingSingleStream?.let {
        SingleStreamDialog(
            onAdd = { onEvent(HomeEvent.OnConfirmSingleStream) },
            onCancel = { onEvent(HomeEvent.OnCancelSingleStream) },
        )
    }
    homeUiState.pendingFileReplace?.let { displayName ->
        ReplacePlaylistDialog(
            displayName = displayName,
            onReplace = { onEvent(HomeEvent.OnConfirmReplaceFile) },
            onCancel = { onEvent(HomeEvent.OnCancelReplaceFile) },
        )
    }

    // F3: TS IPTV Source import (preview, includes, 18+, errors).
    when (val source = homeUiState.sourceImport) {
        is SourceImportState.Preview -> SourcePreviewDialog(
            preview = source.preview,
            onImport = { onEvent(HomeEvent.OnSourceImport(it)) },
            onCancel = { onEvent(HomeEvent.OnSourceImportDismiss) },
        )
        is SourceImportState.Fetching -> SourceFetchingDialog(source.done, source.total, onCancel = { onEvent(HomeEvent.OnSourceImportDismiss) })
        SourceImportState.AdultInclude -> SourceAdultIncludeDialog(
            onConfirm = { onEvent(HomeEvent.OnSourceAdultIncludeConfirmed) },
            onCancel = { onEvent(HomeEvent.OnSourceImportDismiss) },
        )
        is SourceImportState.Rejected -> SourceErrorDialog(source.report, onDismiss = { onEvent(HomeEvent.OnSourceImportDismiss) })
        null -> Unit
    }

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
            message = homeUiState.error?.let { importErrorMessage(it) } ?: "",
            positiveButtonText = stringResource(Res.string.ok),
            // Both clear the error in the view model, or it reappears on Home.
            onPositiveClick = {
                showError = false
                onEvent(HomeEvent.OnDismissErrorDialog)
            },
            onDismissRequest = {
                showError = false
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
            Column(modifier = Modifier.focusProperties { down = nameFocus }) {
                TSAppBarXBackIcon(
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
        // Tap outside the fields to dismiss the keyboard. A tap detector rather
        // than clickable: clickable is also a focus target, and on a TV the
        // D-pad's first move landed on this invisible, full-screen one.
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus(false) }
        }
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
                        .padding(horizontal = 20.dp)
                        .dpadVertical(
                            onUp = { focusManager.moveFocus(FocusDirection.Up) },
                            onDown = { urlFocus.requestFocus() },
                        )
                        .focusRequester(nameFocus),
                    value = inputSourceName,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { urlFocus.requestFocus() }),
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
                        .padding(horizontal = 20.dp)
                        .dpadVertical(
                            onUp = { nameFocus.requestFocus() },
                            onDown = { addButtonFocus.requestFocus() },
                        )
                        .focusRequester(urlFocus),
                    value = inputSourceUrl,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { addButtonFocus.requestFocus() }),
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
                        .fillMaxWidth()
                        .focusRequester(addButtonFocus)
                        .focusProperties { if (filePicker.isAvailable) down = fileButtonFocus },
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

            item("BtnImportFile") {
                if (filePicker.isAvailable) {
                    GrayButton(
                        text = stringResource(Res.string.import_from_file_title),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .focusRequester(fileButtonFocus)
                            .focusProperties { up = addButtonFocus },
                        onClick = {
                            if (!showProgress) filePicker.launch() else showCancelDialog = true
                        },
                    )
                } else {
                    // Most Android TVs ship without a document picker.
                    Text(
                        text = stringResource(Res.string.import_file_unavailable_tv),
                        color = TSColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                    )
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

/**
 * A text field keeps ▲ / ▼ for its cursor even when no keyboard is showing, so on a TV the
 * D-pad could not leave the name and link fields. Move focus explicitly instead; the fields
 * are single-line, so nothing is lost.
 */
private fun Modifier.dpadVertical(onUp: () -> Unit, onDown: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> { onUp(); true }
            Key.DirectionDown -> { onDown(); true }
            else -> false
        }
    }
