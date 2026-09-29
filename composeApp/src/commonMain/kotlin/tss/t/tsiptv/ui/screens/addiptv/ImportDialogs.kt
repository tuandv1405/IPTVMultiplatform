package tss.t.tsiptv.ui.screens.addiptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.import_details_action
import tsiptv.composeapp.generated.resources.import_details_title
import tsiptv.composeapp.generated.resources.import_error_file_read
import tsiptv.composeapp.generated.resources.import_error_file_too_large
import tsiptv.composeapp.generated.resources.import_replace_title
import tsiptv.composeapp.generated.resources.import_error_html_page
import tsiptv.composeapp.generated.resources.import_error_needs_update
import tsiptv.composeapp.generated.resources.import_error_unknown_format
import tsiptv.composeapp.generated.resources.import_hls_single_action
import tsiptv.composeapp.generated.resources.import_hls_single_message
import tsiptv.composeapp.generated.resources.import_hls_single_title
import tsiptv.composeapp.generated.resources.import_replace_action
import tsiptv.composeapp.generated.resources.import_replace_existing
import tsiptv.composeapp.generated.resources.import_skipped_kodi_addon
import tsiptv.composeapp.generated.resources.import_skipped_no_url
import tsiptv.composeapp.generated.resources.import_skipped_web_scraping
import tsiptv.composeapp.generated.resources.import_summary_skipped
import org.jetbrains.compose.resources.ExperimentalResourceApi
import tsiptv.composeapp.generated.resources.iptv_import_success_msg
import tsiptv.composeapp.generated.resources.iptv_import_success_title
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.playlist_file_refresh_hint
import tsiptv.composeapp.generated.resources.strm_error_kodi_addon
import tss.t.tsiptv.core.parser.model.SkipReason
import tss.t.tsiptv.ui.screens.home.ImportSummary
import tss.t.tsiptv.ui.screens.source.SourceDetailsDialog
import tsiptv.composeapp.generated.resources.source_added
import tsiptv.composeapp.generated.resources.source_n_channels
import tsiptv.composeapp.generated.resources.source_n_movies
import tsiptv.composeapp.generated.resources.source_n_series
import tsiptv.composeapp.generated.resources.source_items_were_skipped
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.ui.widgets.GradientButton2
import tss.t.tsiptv.ui.widgets.GrayButton
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.usecase.playlist.ImportError
import tss.t.tsiptv.usecase.playlist.PlaylistImportException
import tss.t.tsiptv.utils.customShadow

/** The message for an import failure: a translated one when the cause is known. */
@Composable
fun importErrorMessage(error: Throwable): String = when ((error as? PlaylistImportException)?.error) {
    ImportError.HTML_PAGE -> stringResource(Res.string.import_error_html_page)
    ImportError.UNKNOWN_FORMAT -> stringResource(Res.string.import_error_unknown_format)
    ImportError.NEEDS_UPDATE -> stringResource(Res.string.import_error_needs_update)
    ImportError.FILE_TOO_LARGE -> stringResource(Res.string.import_error_file_too_large)
    ImportError.FILE_READ -> stringResource(Res.string.import_error_file_read)
    ImportError.STRM_KODI_ADDON -> stringResource(Res.string.strm_error_kodi_addon)
    ImportError.FILE_REFRESH -> stringResource(Res.string.playlist_file_refresh_hint)
    null -> error.message ?: error.toString()
}

/**
 * The success dialog, followed by what was skipped when anything was.
 *
 * OK takes focus first; on a TV, Details sits to its right so ▶ reaches it. Back dismisses.
 */
@Composable
fun ImportSummaryDialog(
    summary: ImportSummary,
    onDismiss: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    val source = summary.source
    if (showDetails) {
        if (source != null) SourceDetailsDialog(source.report.details(), onDismiss = { showDetails = false })
        else ImportDetailsDialog(summary.skipped, onDismiss = { showDetails = false })
        return
    }
    val okFocus = remember { FocusRequester() }
    val message = if (source != null) buildString {
        val parts = listOf(
            pluralStringResource(Res.plurals.source_n_channels, source.channelCount, source.channelCount),
            pluralStringResource(Res.plurals.source_n_movies, source.movieCount, source.movieCount),
            pluralStringResource(Res.plurals.source_n_series, source.seriesCount, source.seriesCount),
        )
        append(stringResource(Res.string.source_added, parts.joinToString(", ")))
        if (source.skippedCount > 0) {
            append('\n')
            append(pluralStringResource(Res.plurals.source_items_were_skipped, source.skippedCount, source.skippedCount))
        }
    } else buildString {
        append(stringResource(Res.string.iptv_import_success_msg, summary.channelCount))
        if (summary.skippedTotal > 0) {
            append('\n')
            append(pluralStringResource(Res.plurals.import_summary_skipped, summary.skippedTotal, summary.skippedTotal))
        }
    }
    DialogCard(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.iptv_import_success_title),
        message = message,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            GradientButton2(
                text = stringResource(Res.string.ok),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
                    .defaultMinSize(minHeight = 52.dp)
                    .focusRequester(okFocus),
            )
            if (summary.skippedTotal > 0) {
                Spacer(Modifier.width(12.dp))
                GrayButton(
                    text = stringResource(Res.string.import_details_action),
                    onClick = { showDetails = true },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 52.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { okFocus.requestFocusAfterLayout() }
}

/** One row per skip reason, with its count. */
@Composable
fun ImportDetailsDialog(
    skipped: Map<SkipReason, Int>,
    onDismiss: () -> Unit,
) {
    val okFocus = remember { FocusRequester() }
    val rows = SkipReason.entries.mapNotNull { reason ->
        val count = skipped[reason]?.takeIf { it > 0 } ?: return@mapNotNull null
        val resource = when (reason) {
            SkipReason.KODI_ADDON -> Res.plurals.import_skipped_kodi_addon
            SkipReason.WEB_SCRAPING -> Res.plurals.import_skipped_web_scraping
            SkipReason.NO_URL -> Res.plurals.import_skipped_no_url
        }
        pluralStringResource(resource, count, count)
    }
    DialogCard(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.import_details_title),
        message = rows.joinToString("\n") { "• $it" },
        messageAlign = TextAlign.Start,
    ) {
        GradientButton2(
            text = stringResource(Res.string.ok),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .focusRequester(okFocus),
        )
    }
    LaunchedEffect(Unit) { okFocus.requestFocusAfterLayout() }
}

@Composable
fun SingleStreamDialog(onAdd: () -> Unit, onCancel: () -> Unit) {
    TSDialog(
        title = stringResource(Res.string.import_hls_single_title),
        message = stringResource(Res.string.import_hls_single_message),
        positiveButtonText = stringResource(Res.string.import_hls_single_action),
        negativeButtonText = stringResource(Res.string.cancel),
        onPositiveClick = onAdd,
        onNegativeClick = onCancel,
        onDismissRequest = onCancel,
    )
}

@Composable
fun ReplacePlaylistDialog(displayName: String, onReplace: () -> Unit, onCancel: () -> Unit) {
    TSDialog(
        title = stringResource(Res.string.import_replace_title),
        message = stringResource(Res.string.import_replace_existing, displayName),
        positiveButtonText = stringResource(Res.string.import_replace_action),
        negativeButtonText = stringResource(Res.string.cancel),
        onPositiveClick = onReplace,
        onNegativeClick = onCancel,
        onDismissRequest = onCancel,
    )
}

/** Same card as [TSDialog], with the buttons left to the caller. */
@Composable
private fun DialogCard(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    messageAlign: TextAlign = TextAlign.Center,
    buttons: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .customShadow(borderRadius = 20.dp, blurRadius = 50.dp, color = Color.Black.copy(0.25f))
                .clip(TSShapes.roundedShape20)
                .background(Color.White, TSShapes.roundedShape20)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = TSColors.TextTitlePrimaryDart,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 28.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                color = TSColors.TextBodyPrimaryDart,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 23.sp
                ),
                textAlign = messageAlign,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(20.dp))
            buttons()
        }
    }
}
