package tss.t.tsiptv.ui.screens.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.import_details_action
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.source_about
import tsiptv.composeapp.generated.resources.source_adult_pending
import tsiptv.composeapp.generated.resources.source_by_author
import tsiptv.composeapp.generated.resources.source_confirm_adult_action
import tsiptv.composeapp.generated.resources.source_homepage
import tsiptv.composeapp.generated.resources.source_include_failed
import tsiptv.composeapp.generated.resources.source_include_ok
import tsiptv.composeapp.generated.resources.source_include_stale
import tsiptv.composeapp.generated.resources.source_include_type_m3u
import tsiptv.composeapp.generated.resources.source_include_type_source
import tsiptv.composeapp.generated.resources.source_include_type_stremio
import tsiptv.composeapp.generated.resources.source_include_type_xmltv
import tsiptv.composeapp.generated.resources.source_include_withheld
import tsiptv.composeapp.generated.resources.source_includes_title
import tsiptv.composeapp.generated.resources.source_issues_count
import tsiptv.composeapp.generated.resources.source_languages
import tsiptv.composeapp.generated.resources.source_last_refresh
import tsiptv.composeapp.generated.resources.source_loaded_from
import tsiptv.composeapp.generated.resources.source_loaded_from_file
import tsiptv.composeapp.generated.resources.source_no_issues
import tsiptv.composeapp.generated.resources.source_open_homepage
import tsiptv.composeapp.generated.resources.source_refresh
import tsiptv.composeapp.generated.resources.source_refresh_failed
import tsiptv.composeapp.generated.resources.source_refreshing
import tsiptv.composeapp.generated.resources.source_remove
import tsiptv.composeapp.generated.resources.source_remove_confirm
import tsiptv.composeapp.generated.resources.source_revision
import tsiptv.composeapp.generated.resources.source_updated_at
import tsiptv.composeapp.generated.resources.source_details_title
import tsiptv.composeapp.generated.resources.source_include_guide
import tsiptv.composeapp.generated.resources.source_last_refresh_failed
import tsiptv.composeapp.generated.resources.stream_external_tv
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.language.LocalAppLocale
import tss.t.tsiptv.core.parser.tsiptv.LocalizedText
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIncludeType
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssue
import tss.t.tsiptv.core.parser.tsiptv.TsiptvMeta
import tss.t.tsiptv.core.tsiptv.TsiptvIncludeRecord
import tss.t.tsiptv.core.tsiptv.TsiptvIncludeStatus
import tss.t.tsiptv.core.tsiptv.TsiptvRefreshResult
import tss.t.tsiptv.core.tsiptv.TsiptvSourceRecord
import tss.t.tsiptv.core.tsiptv.TsiptvSourceService
import tss.t.tsiptv.core.tsiptv.TsiptvStorageJson
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.ui.screens.addons.AddonDialog
import tss.t.tsiptv.ui.screens.addons.AddonLogo
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvDefaults
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.utils.formatDateTime
import tss.t.tsiptv.utils.getUrlOpener

data class SourceAboutUiState(
    val loading: Boolean = true,
    val source: TsiptvSourceRecord? = null,
    val meta: TsiptvMeta? = null,
    val includes: List<TsiptvIncludeRecord> = emptyList(),
    val issues: List<TsiptvIssue> = emptyList(),
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    val needsAdultConfirmation: Boolean get() = source?.needsAdultConfirmation(includes) == true
}

/** "About this source" (PRD F3 §5): what the source is, where it comes from and how its parts are doing. */
class SourceAboutViewModel(
    private val database: IPTVDatabase,
    private val service: TsiptvSourceService,
) : ViewModel() {
    private val _state = MutableStateFlow(SourceAboutUiState())
    val state: StateFlow<SourceAboutUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var playlistId: String? = null

    fun open(playlistId: String) {
        if (this.playlistId == playlistId) return
        this.playlistId = playlistId
        job?.cancel()
        val store = database.tsiptvStore
        job = viewModelScope.launch {
            combine(store.observeSource(playlistId), store.observeIncludes(playlistId)) { s, i -> s to i }.collect { (source, includes) ->
                val meta = source?.metaJson?.let { runCatching { TsiptvStorageJson.decodeFromString(TsiptvMeta.serializer(), it) }.getOrNull() }
                val issues = source?.reportJson?.let {
                    runCatching { TsiptvStorageJson.decodeFromString(ListSerializer(TsiptvIssue.serializer()), it) }.getOrNull()
                }.orEmpty()
                _state.update {
                    it.copy(
                        loading = false,
                        source = source,
                        meta = meta,
                        includes = includes.sortedWith(compareBy({ it.includePath.count { c -> c == ':' } }, { it.includePath })),
                        issues = issues,
                    )
                }
            }
        }
    }

    /** Refresh: the root (links only) and every include, ignoring `refreshHours`. */
    fun refresh() {
        val id = playlistId ?: return
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, refreshFailed = false) }
        viewModelScope.launch {
            val result = try {
                service.refresh(id, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                TsiptvRefreshResult.Failed("error")
            }
            val failed = result is TsiptvRefreshResult.Failed ||
                (result is TsiptvRefreshResult.Stored && result.rootError != null)
            _state.update { it.copy(refreshing = false, refreshFailed = failed) }
        }
    }
}

/**
 * A readable include name (QC #22): its own `name`; guide links of the source as "Guide N"; an
 * M3U's `x-tvg-url` guides as "<that include> · guide N"; the id otherwise.
 */
@Composable
private fun includeLabel(include: TsiptvIncludeRecord, all: List<TsiptvIncludeRecord>, uiLanguage: String?): String {
    fun nameOf(record: TsiptvIncludeRecord?): String? = record?.nameJson?.let {
        runCatching { TsiptvStorageJson.decodeFromString(LocalizedText.serializer(), it) }.getOrNull()?.resolve(uiLanguage)
    }?.takeIf { it.isNotBlank() }
    nameOf(include)?.let { return it }
    val path = include.includePath
    val last = path.substringAfterLast(':')
    val parentPath = path.substringBeforeLast(':', "")
    val parentLabel = parentPath.takeIf { it.isNotEmpty() }?.let { p -> nameOf(all.firstOrNull { it.includePath == p }) ?: p }
    val guideNumber = Regex("^(?:epg|tvg)-(\\d+)$").find(last)?.groupValues?.get(1)?.toIntOrNull()
    if (guideNumber != null) {
        val guide = stringResource(Res.string.source_include_guide, guideNumber + 1)
        return if (parentLabel != null) "$parentLabel · $guide" else guide
    }
    return if (parentLabel != null) "$parentLabel · $last" else path
}

/** A link's host only (links can carry tokens in their path or query). */
internal fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')

@Composable
private fun includeTypeLabel(type: String): String = when (type) {
    TsiptvIncludeType.M3U.wireName -> stringResource(Res.string.source_include_type_m3u)
    TsiptvIncludeType.STREMIO.wireName -> stringResource(Res.string.source_include_type_stremio)
    TsiptvIncludeType.TSIPTV_SOURCE.wireName -> stringResource(Res.string.source_include_type_source)
    else -> stringResource(Res.string.source_include_type_xmltv)
}

@Composable
private fun includeStatusText(include: TsiptvIncludeRecord): String = when {
    include.adultWithheld -> stringResource(Res.string.source_include_withheld)
    include.status == TsiptvIncludeStatus.OK.name -> stringResource(Res.string.source_include_ok)
    include.status == TsiptvIncludeStatus.STALE.name && include.lastSuccessAt != null ->
        stringResource(Res.string.source_include_stale, include.lastSuccessAt.formatDateTime())
    else -> stringResource(Res.string.source_include_failed)
}

/**
 * "About this source": logo, name, author, description, homepage (opened after a confirmation),
 * languages, last update, revision, where it was loaded from, its includes with their status,
 * the last refresh with its problems (Details), Refresh, 18+ confirmation and Remove source.
 */
@Composable
fun SourceAboutScreen(
    playlistId: String,
    viewModel: SourceAboutViewModel,
    onConfirmAdult: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(playlistId) { viewModel.open(playlistId) }
    val state by viewModel.state.collectAsState()
    val uiLanguage = LocalAppLocale.current
    val isTv = LocalIsTvMode.current
    val horizontal = if (isTv) TvDefaults.overscanHorizontal else 16.dp
    val scope = rememberCoroutineScope()
    var confirmHomepage by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf<String?>(null) }
    val refreshFocus = remember { FocusRequester() }
    val source = state.source
    val meta = state.meta
    val name = meta?.name?.resolve(uiLanguage).orEmpty()

    LaunchedEffect(state.loading) { if (isTv && !state.loading) refreshFocus.requestFocusAfterLayout() }

    Box(Modifier.fillMaxSize().background(TSColors.BackgroundColor)) {
        LazyColumn(
            Modifier.fillMaxSize().then(if (isTv) Modifier else Modifier.statusBarsPadding()),
            contentPadding = PaddingValues(horizontal = horizontal, vertical = if (isTv) TvDefaults.overscanVertical else 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "bar") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isTv) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TSColors.TextPrimary)
                        }
                    }
                    Text(stringResource(Res.string.source_about), color = TSColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (state.loading) {
                item(key = "loading") { CircularProgressIndicator(color = TSColors.AccentCyan) }
                return@LazyColumn
            }
            if (source == null || meta == null) return@LazyColumn
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AddonLogo(meta.logo, Modifier.size(64.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(name, color = TSColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        meta.author?.name?.let { Text(stringResource(Res.string.source_by_author, it), color = TSColors.TextSecondary, fontSize = 14.sp) }
                    }
                }
            }
            meta.description?.resolve(uiLanguage)?.takeIf { it.isNotBlank() }?.let { text ->
                item(key = "desc") { Text(text, color = TSColors.TextSecondaryLight, fontSize = 15.sp) }
            }
            item(key = "facts") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val facts = buildList {
                        if (meta.languages.isNotEmpty()) add(stringResource(Res.string.source_languages, meta.languages.joinToString(", ")))
                        meta.updatedAt?.let { add(stringResource(Res.string.source_updated_at, formatSourceDate(it))) }
                        add(stringResource(Res.string.source_revision, source.revision))
                        add(source.rootUrl?.let { stringResource(Res.string.source_loaded_from, hostOf(it)) } ?: stringResource(Res.string.source_loaded_from_file))
                        add(stringResource(Res.string.source_last_refresh, source.fetchedAt.formatDateTime()))
                    }
                    facts.forEach { Text(it, color = TSColors.TextSecondaryLight, fontSize = 14.sp) }
                }
            }
            if (state.needsAdultConfirmation) {
                item(key = "adult") {
                    Column(
                        Modifier.fillMaxWidth().background(TSColors.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WarningLine(stringResource(Res.string.source_adult_pending))
                        var checked by remember { mutableStateOf(false) }
                        AdultCheckbox(checked) { checked = it }
                        PillButton(stringResource(Res.string.source_confirm_adult_action), onClick = onConfirmAdult, enabled = checked)
                    }
                }
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (source.rootUrl != null || state.includes.isNotEmpty()) {
                        PillButton(
                            stringResource(if (state.refreshing) Res.string.source_refreshing else Res.string.source_refresh),
                            onClick = viewModel::refresh,
                            enabled = !state.refreshing,
                            modifier = Modifier.focusRequester(refreshFocus),
                        )
                    }
                    meta.homepage?.let { url ->
                        PillButton(stringResource(Res.string.source_homepage), onClick = { confirmHomepage = url }, primary = false)
                    }
                    PillButton(stringResource(Res.string.source_remove), onClick = { confirmRemove = true }, primary = false)
                }
            }
            val lastError = source.lastErrorAt
            if (state.refreshFailed || lastError != null) {
                item(key = "refresh_failed") {
                    Column {
                        Text(stringResource(Res.string.source_refresh_failed), color = TSColors.ErrorRed, fontSize = 14.sp)
                        lastError?.let {
                            Text(stringResource(Res.string.source_last_refresh_failed, it.formatDateTime()), color = TSColors.TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (state.includes.isNotEmpty()) {
                item(key = "includes_title") {
                    Text(stringResource(Res.string.source_includes_title), color = TSColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                items(state.includes, key = { it.includePath }) { include ->
                    val label = includeLabel(include, state.includes, uiLanguage)
                    Column(Modifier.fillMaxWidth().background(TSColors.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Text(label, color = TSColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            includeTypeLabel(include.type) + " · " + hostOf(include.url),
                            color = TSColors.TextSecondary, fontSize = 13.sp,
                        )
                        val status = includeStatusText(include)
                        Text(
                            status,
                            color = if (include.status == TsiptvIncludeStatus.OK.name && !include.adultWithheld) TSColors.AccentGreen else TSColors.LoadingYellow,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item(key = "issues") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (state.issues.isEmpty()) stringResource(Res.string.source_no_issues)
                        else stringResource(Res.string.source_issues_count, state.issues.size),
                        color = TSColors.TextSecondaryLight, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    if (state.issues.isNotEmpty()) {
                        PillButton(stringResource(Res.string.import_details_action), onClick = { details = true }, primary = false)
                    }
                }
            }
        }
    }

    if (details) SourceDetailsDialog(state.issues, onDismiss = { details = false }, title = stringResource(Res.string.source_details_title))
    confirmHomepage?.let { url ->
        ConfirmDialog(
            message = stringResource(Res.string.source_open_homepage, hostOf(url)),
            confirm = stringResource(Res.string.ok),
            onConfirm = {
                confirmHomepage = null
                // TV without a browser: show the link as text (as F2 does for external streams).
                scope.launch { if (!getUrlOpener().openUrl(url)) showUrl = url }
            },
            onDismiss = { confirmHomepage = null },
        )
    }
    showUrl?.let { url ->
        val focus = remember { FocusRequester() }
        AddonDialog(title = null, onDismissRequest = { showUrl = null }) {
            Text(stringResource(Res.string.stream_external_tv), color = TSColors.TextSecondaryLight)
            androidx.compose.foundation.text.selection.SelectionContainer { Text(url, color = TSColors.TextPrimary, fontSize = 14.sp) }
            PillButton(stringResource(Res.string.ok), onClick = { showUrl = null }, modifier = Modifier.focusRequester(focus))
        }
        LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
    }
    if (confirmRemove) {
        ConfirmDialog(
            message = stringResource(Res.string.source_remove_confirm, name),
            confirm = stringResource(Res.string.source_remove),
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun ConfirmDialog(message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    AddonDialog(title = null, onDismissRequest = onDismiss) {
        Text(message, color = TSColors.TextPrimary, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton(confirm, onClick = onConfirm, modifier = Modifier.widthIn(min = 96.dp))
            PillButton(stringResource(Res.string.cancel), onClick = onDismiss, primary = false, modifier = Modifier.focusRequester(focus))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
}
