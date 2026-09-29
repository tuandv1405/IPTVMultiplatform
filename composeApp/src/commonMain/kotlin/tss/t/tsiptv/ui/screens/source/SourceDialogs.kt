package tss.t.tsiptv.ui.screens.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.addon_confirm_adult
import tsiptv.composeapp.generated.resources.addon_replace
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.import_details_action
import tsiptv.composeapp.generated.resources.import_details_title
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.source_by_author
import tsiptv.composeapp.generated.resources.source_counts
import tsiptv.composeapp.generated.resources.source_counts_extra
import tsiptv.composeapp.generated.resources.source_error_empty
import tsiptv.composeapp.generated.resources.source_error_missing_fields
import tsiptv.composeapp.generated.resources.source_error_not_json
import tsiptv.composeapp.generated.resources.source_error_not_source
import tsiptv.composeapp.generated.resources.source_error_nothing_loaded
import tsiptv.composeapp.generated.resources.source_error_title
import tsiptv.composeapp.generated.resources.source_error_too_large
import tsiptv.composeapp.generated.resources.source_error_version
import tsiptv.composeapp.generated.resources.source_fetching_includes
import tsiptv.composeapp.generated.resources.source_hosts_title
import tsiptv.composeapp.generated.resources.source_import
import tsiptv.composeapp.generated.resources.source_issue_e_drm
import tsiptv.composeapp.generated.resources.source_issue_e_duplicate_id
import tsiptv.composeapp.generated.resources.source_issue_e_empty
import tsiptv.composeapp.generated.resources.source_issue_e_header_forbidden
import tsiptv.composeapp.generated.resources.source_issue_e_id
import tsiptv.composeapp.generated.resources.source_issue_e_include
import tsiptv.composeapp.generated.resources.source_issue_e_include_cycle
import tsiptv.composeapp.generated.resources.source_issue_e_item_id
import tsiptv.composeapp.generated.resources.source_issue_e_item_name
import tsiptv.composeapp.generated.resources.source_issue_e_meta
import tsiptv.composeapp.generated.resources.source_issue_e_no_stream
import tsiptv.composeapp.generated.resources.source_issue_e_not_json
import tsiptv.composeapp.generated.resources.source_issue_e_not_source
import tsiptv.composeapp.generated.resources.source_issue_e_too_large
import tsiptv.composeapp.generated.resources.source_issue_e_url
import tsiptv.composeapp.generated.resources.source_issue_e_version
import tsiptv.composeapp.generated.resources.source_issue_w_contrast
import tsiptv.composeapp.generated.resources.source_issue_w_field
import tsiptv.composeapp.generated.resources.source_issue_w_limit
import tsiptv.composeapp.generated.resources.source_issue_w_query_ref
import tsiptv.composeapp.generated.resources.source_issue_w_text
import tsiptv.composeapp.generated.resources.source_issue_w_unknown_type
import tsiptv.composeapp.generated.resources.source_items_skipped
import tsiptv.composeapp.generated.resources.source_notice
import tsiptv.composeapp.generated.resources.source_preview_title
import tsiptv.composeapp.generated.resources.source_replace_existing
import tsiptv.composeapp.generated.resources.source_warn_adult
import tsiptv.composeapp.generated.resources.source_warn_adult_include
import tsiptv.composeapp.generated.resources.source_updated_at
import tss.t.tsiptv.core.language.LocalAppLocale
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssue
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode
import tss.t.tsiptv.core.parser.tsiptv.TsiptvValidationReport
import tss.t.tsiptv.core.tsiptv.TsiptvSourcePreview
import tss.t.tsiptv.ui.screens.addons.AddonDialog
import tss.t.tsiptv.ui.screens.addons.AddonLogo
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.ui.tv.TvFocusableSurface
import tss.t.tsiptv.utils.formatDateTime

/** The translated one-line explanation of a validation code (the 22 `source_issue_*` keys). */
fun issueText(code: TsiptvIssueCode): StringResource = when (code) {
    TsiptvIssueCode.E_NOT_JSON -> Res.string.source_issue_e_not_json
    TsiptvIssueCode.E_NOT_SOURCE -> Res.string.source_issue_e_not_source
    TsiptvIssueCode.E_VERSION -> Res.string.source_issue_e_version
    TsiptvIssueCode.E_TOO_LARGE -> Res.string.source_issue_e_too_large
    TsiptvIssueCode.E_ID -> Res.string.source_issue_e_id
    TsiptvIssueCode.E_META -> Res.string.source_issue_e_meta
    TsiptvIssueCode.E_EMPTY -> Res.string.source_issue_e_empty
    TsiptvIssueCode.E_ITEM_ID -> Res.string.source_issue_e_item_id
    TsiptvIssueCode.E_DUPLICATE_ID -> Res.string.source_issue_e_duplicate_id
    TsiptvIssueCode.E_ITEM_NAME -> Res.string.source_issue_e_item_name
    TsiptvIssueCode.E_NO_STREAM -> Res.string.source_issue_e_no_stream
    TsiptvIssueCode.E_URL -> Res.string.source_issue_e_url
    TsiptvIssueCode.E_HEADER_FORBIDDEN -> Res.string.source_issue_e_header_forbidden
    TsiptvIssueCode.E_DRM -> Res.string.source_issue_e_drm
    TsiptvIssueCode.E_INCLUDE -> Res.string.source_issue_e_include
    TsiptvIssueCode.E_INCLUDE_CYCLE -> Res.string.source_issue_e_include_cycle
    TsiptvIssueCode.W_UNKNOWN_TYPE -> Res.string.source_issue_w_unknown_type
    TsiptvIssueCode.W_QUERY_REF -> Res.string.source_issue_w_query_ref
    TsiptvIssueCode.W_LIMIT -> Res.string.source_issue_w_limit
    TsiptvIssueCode.W_CONTRAST -> Res.string.source_issue_w_contrast
    TsiptvIssueCode.W_TEXT -> Res.string.source_issue_w_text
    TsiptvIssueCode.W_FIELD -> Res.string.source_issue_w_field
}

/** The error dialog message for a rejected document (PRD §1 "Errors"). */
fun documentErrorText(code: TsiptvIssueCode?): StringResource = when (code) {
    TsiptvIssueCode.E_NOT_JSON -> Res.string.source_error_not_json
    TsiptvIssueCode.E_VERSION -> Res.string.source_error_version
    TsiptvIssueCode.E_TOO_LARGE -> Res.string.source_error_too_large
    TsiptvIssueCode.E_ID, TsiptvIssueCode.E_META -> Res.string.source_error_missing_fields
    TsiptvIssueCode.E_EMPTY -> Res.string.source_error_empty
    else -> Res.string.source_error_not_source
}

/** One Details line: `path — CODE: explanation` (the path is a JSON path, never a URL; PRD §3). */
@Composable
fun issueLine(issue: TsiptvIssue): String {
    val text = "${issue.code.name}: " + stringResource(issueText(issue.code))
    return if (issue.path.isEmpty()) text else "${issue.path} — $text"
}

/**
 * `meta.updatedAt` for display: an RFC 3339 date-time in the device's time zone, a plain date as
 * written, anything else unchanged.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
fun formatSourceDate(value: String): String =
    runCatching { kotlin.time.Instant.parse(value.trim()).toEpochMilliseconds() }.getOrNull()
        ?.formatDateTime()
        ?: value

/** The Details list (at most 100 entries: document errors, then item errors, then warnings). */
@Composable
fun SourceDetailsDialog(
    issues: List<TsiptvIssue>,
    onDismiss: () -> Unit,
    title: String = stringResource(Res.string.import_details_title),
) {
    val okFocus = remember { FocusRequester() }
    AddonDialog(title = title, onDismissRequest = onDismiss) {
        issues.take(TsiptvValidationReport.MAX_DETAILS).forEach { issue ->
            Text("• " + issueLine(issue), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton(stringResource(Res.string.ok), onClick = onDismiss, modifier = Modifier.focusRequester(okFocus))
        }
    }
    LaunchedEffect(Unit) { okFocus.requestFocusAfterLayout() }
}

/** "Can't add this source": document errors, with Details. */
@Composable
fun SourceErrorDialog(report: TsiptvValidationReport?, onDismiss: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    if (details && report != null) {
        SourceDetailsDialog(report.details(), onDismiss = { details = false })
        return
    }
    val okFocus = remember { FocusRequester() }
    AddonDialog(title = stringResource(Res.string.source_error_title), onDismissRequest = onDismiss) {
        Text(
            stringResource(if (report == null) Res.string.source_error_nothing_loaded else documentErrorText(report.primaryDocumentError)),
            color = TSColors.TextSecondaryLight, fontSize = 15.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton(stringResource(Res.string.ok), onClick = onDismiss, modifier = Modifier.focusRequester(okFocus))
            if (report != null && report.issues.isNotEmpty()) {
                PillButton(stringResource(Res.string.import_details_action), onClick = { details = true }, primary = false)
            }
        }
    }
    LaunchedEffect(Unit) { okFocus.requestFocusAfterLayout() }
}

/**
 * The import preview (PRD §1 step 3): name, author, counts, the servers it contacts, what will be
 * skipped (with Details), the third-party notice and, for an adult root, the 18+ confirmation.
 * Initial focus is on Import (TV).
 */
@Composable
fun SourcePreviewDialog(
    preview: TsiptvSourcePreview,
    onImport: (adultConfirmed: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var details by remember(preview) { mutableStateOf(false) }
    if (details) {
        SourceDetailsDialog(preview.report.details(), onDismiss = { details = false })
        return
    }
    var adultConfirmed by remember(preview) { mutableStateOf(false) }
    val importFocus = remember { FocusRequester() }
    val uiLanguage = LocalAppLocale.current
    val meta = preview.document.meta
    AddonDialog(title = stringResource(Res.string.source_preview_title), onDismissRequest = onCancel) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AddonLogo(meta.logo, Modifier.size(56.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(meta.name.resolve(uiLanguage), color = TSColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                meta.author?.name?.let {
                    Text(stringResource(Res.string.source_by_author, it), color = TSColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
        meta.description?.resolve(uiLanguage)?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = TSColors.TextSecondaryLight, fontSize = 14.sp, maxLines = 6)
        }
        meta.updatedAt?.let {
            Text(stringResource(Res.string.source_updated_at, formatSourceDate(it)), color = TSColors.TextSecondary, fontSize = 13.sp)
        }
        Text(
            stringResource(Res.string.source_counts, preview.tvCount, preview.radioCount, preview.movieCount, preview.seriesCount),
            color = TSColors.TextSecondaryLight, fontSize = 14.sp,
        )
        if (preview.episodeCount > 0 || preview.includeCount > 0) {
            Text(
                stringResource(Res.string.source_counts_extra, preview.episodeCount, preview.includeCount),
                color = TSColors.TextSecondaryLight, fontSize = 14.sp,
            )
        }
        val hosts = preview.hosts
        if (hosts.isNotEmpty()) {
            Text(stringResource(Res.string.source_hosts_title), color = TSColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(hosts.joinToString("\n") { "• $it" }, color = TSColors.TextSecondaryLight, fontSize = 13.sp)
        }
        if (preview.skippedCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WarningLine(stringResource(Res.string.source_items_skipped, preview.skippedCount), Modifier.weight(1f))
                PillButton(stringResource(Res.string.import_details_action), onClick = { details = true }, primary = false)
            }
        }
        Box(Modifier.fillMaxWidth().background(TSColors.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(12.dp)) {
            Text(stringResource(Res.string.source_notice), color = TSColors.TextPrimary, fontSize = 14.sp)
        }
        if (preview.rootIsAdult) {
            WarningLine(stringResource(Res.string.source_warn_adult))
            AdultCheckbox(adultConfirmed) { adultConfirmed = it }
        }
        if (preview.needsReplaceConfirmation) {
            Text(
                stringResource(Res.string.source_replace_existing, preview.existingName ?: preview.document.meta.name.resolve(uiLanguage)),
                color = TSColors.LoadingYellow, fontSize = 14.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton(
                stringResource(if (preview.needsReplaceConfirmation) Res.string.addon_replace else Res.string.source_import),
                onClick = { onImport(adultConfirmed) },
                enabled = !preview.rootIsAdult || adultConfirmed,
                modifier = Modifier.focusRequester(importFocus).widthIn(min = 96.dp),
            )
            PillButton(stringResource(Res.string.cancel), onClick = onCancel, primary = false)
        }
    }
    LaunchedEffect(preview) { importFocus.requestFocusAfterLayout() }
}

/** "Loading i of n…" while the includes are fetched. */
@Composable
fun SourceFetchingDialog(done: Int, total: Int, onCancel: () -> Unit) {
    AddonDialog(title = null, onDismissRequest = onCancel) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), color = TSColors.AccentCyan)
            Text(
                stringResource(Res.string.source_fetching_includes, (done + 1).coerceAtMost(total.coerceAtLeast(1)), total.coerceAtLeast(1)),
                color = TSColors.TextPrimary, fontSize = 15.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton(stringResource(Res.string.cancel), onClick = onCancel, primary = false)
        }
    }
}

/** An include is adult (spec §9.3): the user confirms 18+ before anything is stored. */
@Composable
fun SourceAdultIncludeDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    AddonDialog(title = stringResource(Res.string.source_preview_title), onDismissRequest = onCancel) {
        WarningLine(stringResource(Res.string.source_warn_adult_include))
        AdultCheckbox(confirmed, Modifier.focusRequester(focus)) { confirmed = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton(stringResource(Res.string.source_import), onClick = onConfirm, enabled = confirmed)
            PillButton(stringResource(Res.string.cancel), onClick = onCancel, primary = false)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
}

@Composable
internal fun AdultCheckbox(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    TvFocusableSurface(
        onClick = { onChange(!checked) },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
        color = TSColors.Transparent,
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = TSColors.AccentCyan))
            Text(stringResource(Res.string.addon_confirm_adult), color = TSColors.TextPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
internal fun WarningLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = TSColors.LoadingYellow, modifier = Modifier.size(18.dp))
        Text(text, color = TSColors.LoadingYellow, fontSize = 14.sp)
    }
}
