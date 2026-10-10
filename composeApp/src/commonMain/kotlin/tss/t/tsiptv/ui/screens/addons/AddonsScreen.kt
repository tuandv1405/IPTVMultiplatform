package tss.t.tsiptv.ui.screens.addons

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import tsiptv.composeapp.generated.resources.addons_help_expanded
import tsiptv.composeapp.generated.resources.addons_help_collapsed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tss.t.tsiptv.core.stremio.AddonPreview
import tss.t.tsiptv.core.stremio.AddonStatus
import tss.t.tsiptv.core.stremio.InstalledAddon
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.core.language.LocalAppLocale
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvDefaults
import tss.t.tsiptv.ui.tv.TvFocusableSurface
import tss.t.tsiptv.ui.tv.TvMenuItem
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.ui.widgets.TSTextField
import tss.t.tsiptv.utils.getUrlOpener
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.addon_add
import tsiptv.composeapp.generated.resources.addon_added
import tsiptv.composeapp.generated.resources.addon_added_by_you
import tsiptv.composeapp.generated.resources.addon_blocked
import tsiptv.composeapp.generated.resources.addon_catalogs
import tsiptv.composeapp.generated.resources.addon_configure
import tsiptv.composeapp.generated.resources.addon_configured_url_label
import tsiptv.composeapp.generated.resources.addon_confirm_adult
import tsiptv.composeapp.generated.resources.addons_from_sources
import tsiptv.composeapp.generated.resources.addon_continue
import tsiptv.composeapp.generated.resources.addon_enabled
import tsiptv.composeapp.generated.resources.addon_error_invalid_manifest
import tsiptv.composeapp.generated.resources.addon_error_legacy
import tsiptv.composeapp.generated.resources.addon_error_local_server
import tsiptv.composeapp.generated.resources.addon_error_missing_field
import tsiptv.composeapp.generated.resources.addon_error_not_manifest
import tsiptv.composeapp.generated.resources.addon_error_unreachable
import tsiptv.composeapp.generated.resources.addon_more
import tsiptv.composeapp.generated.resources.addon_move_down
import tsiptv.composeapp.generated.resources.addon_move_up
import tsiptv.composeapp.generated.resources.addon_needs_configuration
import tsiptv.composeapp.generated.resources.addon_no_streams
import tsiptv.composeapp.generated.resources.addon_notice
import tsiptv.composeapp.generated.resources.addon_paste
import tsiptv.composeapp.generated.resources.addon_provides_streams
import tsiptv.composeapp.generated.resources.addon_remove
import tsiptv.composeapp.generated.resources.addon_remove_confirm
import tsiptv.composeapp.generated.resources.addon_replace
import tsiptv.composeapp.generated.resources.addon_replace_existing
import tsiptv.composeapp.generated.resources.addon_retry
import tsiptv.composeapp.generated.resources.addon_status_blocked
import tsiptv.composeapp.generated.resources.addon_status_ok
import tsiptv.composeapp.generated.resources.addon_status_off
import tsiptv.composeapp.generated.resources.addon_status_secret_lost
import tsiptv.composeapp.generated.resources.error_occurred
import tsiptv.composeapp.generated.resources.addon_status_unreachable
import tsiptv.composeapp.generated.resources.addon_toggle_hint_tv
import tsiptv.composeapp.generated.resources.addon_types
import tsiptv.composeapp.generated.resources.addon_update_now
import tsiptv.composeapp.generated.resources.addon_updated
import tsiptv.composeapp.generated.resources.addon_url_label
import tsiptv.composeapp.generated.resources.addon_version
import tsiptv.composeapp.generated.resources.addon_warn_adult
import tsiptv.composeapp.generated.resources.addon_warn_http
import tsiptv.composeapp.generated.resources.addon_warn_p2p
import tsiptv.composeapp.generated.resources.addons_empty
import tsiptv.composeapp.generated.resources.addons_help_body
import tsiptv.composeapp.generated.resources.addons_help_guide
import tsiptv.composeapp.generated.resources.addons_help_title
import tsiptv.composeapp.generated.resources.addons_title
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.open_link_failed
import tsiptv.composeapp.generated.resources.stream_external_tv

/**
 * Addon manager (PRD §2): Profile → Addons on phone, Settings → Addons on TV. The add flow
 * (PRD §1) is a dialog so it works with the D-pad and the on-screen keyboard.
 */
@Composable
fun AddonsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<AddonsViewModel>()
    val addons by viewModel.addons.collectAsState()
    val addState by viewModel.addState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val isTv = LocalIsTvMode.current
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<InstalledAddon?>(null) }
    var removeFor by remember { mutableStateOf<InstalledAddon?>(null) }
    var externalUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    AddonsMessage.Added -> getString(Res.string.addon_added)
                    is AddonsMessage.Updated -> getString(Res.string.addon_updated, message.version)
                    AddonsMessage.UpdateFailed -> getString(Res.string.addon_error_unreachable)
                    AddonsMessage.Blocked -> getString(Res.string.addon_blocked)
                    AddonsMessage.InstallFailed -> getString(Res.string.error_occurred)
                }
            )
        }
    }
    LaunchedEffect(isTv) { if (isTv) firstFocus.requestFocusAfterLayout() }
    // TV: after Remove the focused row is gone; put focus back on the list (or the Add button).
    var lastCount by remember { mutableStateOf(addons.size) }
    LaunchedEffect(addons.size) {
        if (isTv && addons.size < lastCount) firstFocus.requestFocusAfterLayout()
        lastCount = addons.size
    }

    fun openConfigure(url: String) {
        scope.launch {
            val opener = getUrlOpener()
            // TV often has no browser: show the link as text instead.
            if (!opener.openUrl(url)) externalUrl = url
        }
    }

    val guideUrl = addonsGuideUrl(LocalAppLocale.current)
    fun openGuide() = openConfigure(guideUrl)

    Box(Modifier.fillMaxSize().background(TSColors.backgroundGradientMain)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .padding(horizontal = if (isTv) TvDefaults.overscanHorizontal else 0.dp, vertical = if (isTv) TvDefaults.overscanVertical else 0.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isTv) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TSColors.TextPrimary)
                    }
                }
                Text(
                    stringResource(Res.string.addons_title),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = TSColors.TextPrimary,
                    fontSize = if (isTv) 26.sp else 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PillButton(
                    text = stringResource(Res.string.addon_add),
                    onClick = { viewModel.resetAdd(); showAdd = true },
                    icon = Icons.Rounded.Add,
                    modifier = if (addons.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            if (isTv && addons.isNotEmpty()) {
                Text(
                    stringResource(Res.string.addon_toggle_hint_tv),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = TSColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
            if (addons.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(Res.string.addons_empty), color = TSColors.TextSecondaryLight, fontSize = 16.sp)
                    // Empty state: the help is open from the start.
                    AddonsHelpCard(initiallyExpanded = true, onOpenGuide = ::openGuide)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "help") { AddonsHelpCard(initiallyExpanded = false, onOpenGuide = ::openGuide) }
                    // F3: addons a TS IPTV Source brought in are listed apart, read-only (switch only).
                    val fromSources = addons.filter { it.isFromSource }
                    itemsIndexed(addons.filterNot { it.isFromSource }, key = { _, it -> it.id }) { index, addon ->
                        AddonRow(
                            addon = addon,
                            isTv = isTv,
                            refreshing = addon.id in refreshing,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onToggle = { viewModel.setEnabled(addon, it) },
                            onOpenMenu = { menuFor = addon },
                            menu = { expanded, dismiss ->
                                AddonMenuItems(
                                    addon = addon,
                                    index = index,
                                    count = addons.size,
                                    asDropdown = true,
                                    expanded = expanded,
                                    onDismiss = dismiss,
                                    onMove = { viewModel.move(addon, it) },
                                    onUpdate = { viewModel.refresh(addon) },
                                    onConfigure = { addon.transport?.let { openConfigure(it.configureUrl) } },
                                    onRemove = { removeFor = addon },
                                    onReAdd = { viewModel.resetAdd(); showAdd = true },
                                )
                            },
                        )
                    }
                    if (fromSources.isNotEmpty()) {
                        item(key = "from_sources") {
                            AddonSectionTitle(stringResource(Res.string.addons_from_sources), Modifier.padding(top = 8.dp))
                        }
                        val noUserAddons = fromSources.size == addons.size
                        itemsIndexed(fromSources, key = { _, it -> it.id }) { index, addon ->
                            AddonRow(
                                addon = addon,
                                isTv = isTv,
                                refreshing = addon.id in refreshing,
                                modifier = if (index == 0 && noUserAddons) Modifier.focusRequester(firstFocus) else Modifier,
                                onToggle = { viewModel.setEnabled(addon, it) },
                                onOpenMenu = { menuFor = addon },
                                menu = { expanded, dismiss ->
                                    AddonMenuItems(
                                        addon = addon, index = -1, count = 0, asDropdown = true, expanded = expanded, onDismiss = dismiss,
                                        onMove = {}, onUpdate = { viewModel.refresh(addon) }, onConfigure = {}, onRemove = {},
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    // TV row menu (OK on a row).
    menuFor?.takeIf { isTv }?.let { addon ->
        val userAddons = addons.filterNot { it.isFromSource }
        val index = if (addon.isFromSource) -1 else userAddons.indexOfFirst { it.id == addon.id }
        AddonDialog(title = addon.name, onDismissRequest = { menuFor = null }) {
            AddonMenuItems(
                addon = addon, index = index, count = if (addon.isFromSource) 0 else userAddons.size, asDropdown = false, expanded = true,
                onDismiss = { menuFor = null },
                onMove = { viewModel.move(addon, it) },
                onUpdate = { viewModel.refresh(addon) },
                onConfigure = { addon.transport?.let { openConfigure(it.configureUrl) } },
                onRemove = { removeFor = addon },
                onReAdd = { viewModel.resetAdd(); showAdd = true },
            )
        }
    }

    removeFor?.let { addon ->
        ConfirmDialog(
            message = stringResource(Res.string.addon_remove_confirm, addon.name),
            confirm = stringResource(Res.string.addon_remove),
            onConfirm = { viewModel.remove(addon); removeFor = null },
            onDismiss = { removeFor = null },
        )
    }

    if (showAdd) {
        AddAddonDialog(
            state = addState,
            onContinue = viewModel::preview,
            onInstall = { viewModel.install(it); showAdd = false },
            onConfigure = ::openConfigure,
            onDismiss = { viewModel.resetAdd(); showAdd = false },
        )
    }

    externalUrl?.let { url ->
        AddonDialog(title = null, onDismissRequest = { externalUrl = null }) {
            Text(
                stringResource(if (isTv) Res.string.stream_external_tv else Res.string.open_link_failed),
                color = TSColors.TextSecondaryLight,
            )
            SelectionContainer { Text(url, color = TSColors.TextPrimary, fontSize = 14.sp) }
            val focus = remember { FocusRequester() }
            PillButton(stringResource(Res.string.addon_continue), onClick = { externalUrl = null }, modifier = Modifier.focusRequester(focus))
            LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
        }
    }
}

/** The web guide; Vietnamese is its default language, every other UI language gets the English page. */
internal fun addonsGuideUrl(appLocale: String?): String {
    val base = "https://tsiptv-8bdd6.web.app/guides/stremio-addons/"
    return if (appLocale.orEmpty().lowercase().startsWith("vi")) base else "$base?lang=en"
}

/**
 * "What are addons?" (PRD §1, neutral wording: no addon is named or suggested). A focusable card
 * that opens on OK / tap, with a link to the full web guide.
 */
@Composable
private fun AddonsHelpCard(initiallyExpanded: Boolean, onOpenGuide: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val stateText = stringResource(if (expanded) Res.string.addons_help_expanded else Res.string.addons_help_collapsed)
    Column(
        Modifier.fillMaxWidth().background(TSColors.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
    ) {
        TvFocusableSurface(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = stateText },
            shape = RoundedCornerShape(12.dp),
            focusedScale = 1.02f,
            color = TSColors.Transparent,
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = TSColors.AccentCyan, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(Res.string.addons_help_title),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    color = TSColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = TSColors.TextSecondaryLight,
                )
            }
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(Res.string.addons_help_body), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
                PillButton(stringResource(Res.string.addons_help_guide), onClick = onOpenGuide, primary = false)
            }
        }
    }
}

@Composable
private fun AddonRow(
    addon: InstalledAddon,
    isTv: Boolean,
    refreshing: Boolean,
    modifier: Modifier,
    onToggle: (Boolean) -> Unit,
    onOpenMenu: () -> Unit,
    menu: @Composable (expanded: Boolean, dismiss: () -> Unit) -> Unit,
) {
    var dropdown by remember { mutableStateOf(false) }
    // Blocked or unreadable addons cannot be switched on.
    val blocked = addon.status == AddonStatus.BLOCKED || addon.status == AddonStatus.SECRET_LOST
    TvFocusableSurface(
        onClick = { if (isTv) onOpenMenu() else dropdown = true },
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            // TV: ◀ / ▶ on a focused row toggles enabled (PRD "TV").
            if (!isTv || event.type != KeyEventType.KeyDown || blocked) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { if (addon.enabled) onToggle(false); addon.enabled }
                Key.DirectionRight -> { if (!addon.enabled) onToggle(true); !addon.enabled }
                else -> false
            }
        },
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.02f,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AddonLogo(addon.stored.logoUrl)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(addon.name, color = TSColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(Res.string.addon_version, addon.stored.version) + " · " + addon.host,
                    color = TSColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(if (addon.isFromSource) Res.string.addons_from_sources else Res.string.addon_added_by_you),
                    color = TSColors.TextSecondary, fontSize = 12.sp,
                )
                val (statusText, statusColor) = when (addon.status) {
                    AddonStatus.OK -> stringResource(Res.string.addon_status_ok) to TSColors.AccentGreen
                    AddonStatus.OFF -> stringResource(Res.string.addon_status_off) to TSColors.TextSecondary
                    AddonStatus.SECRET_LOST -> stringResource(Res.string.addon_status_secret_lost) to TSColors.ErrorRed
                    AddonStatus.UNREACHABLE -> stringResource(Res.string.addon_status_unreachable) to TSColors.LoadingYellow
                    AddonStatus.BLOCKED -> stringResource(Res.string.addon_status_blocked) to TSColors.ErrorRed
                }
                Text(statusText, color = statusColor, fontSize = 12.sp)
            }
            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = TSColors.AccentCyan)
            Switch(
                checked = addon.enabled,
                onCheckedChange = if (isTv || blocked) null else onToggle,
                enabled = !blocked,
                colors = SwitchDefaults.colors(checkedTrackColor = TSColors.AccentCyan),
            )
            if (!isTv) {
                Box {
                    IconButton(onClick = { dropdown = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.addon_more), tint = TSColors.TextPrimary)
                    }
                    menu(dropdown) { dropdown = false }
                }
            }
        }
    }
}

@Composable
private fun AddonMenuItems(
    addon: InstalledAddon,
    index: Int,
    count: Int,
    asDropdown: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit,
    onUpdate: () -> Unit,
    onConfigure: () -> Unit,
    onRemove: () -> Unit,
    onReAdd: () -> Unit = {},
) {
    val items = buildList {
        // The saved link is unreadable: offer to add the addon again (same id replaces this row).
        if (addon.status == AddonStatus.SECRET_LOST) add(stringResource(Res.string.addon_add) to onReAdd)
        if (index > 0) add(stringResource(Res.string.addon_move_up) to { onMove(-1) })
        if (index in 0 until count - 1) add(stringResource(Res.string.addon_move_down) to { onMove(1) })
        if (addon.status != AddonStatus.BLOCKED && addon.status != AddonStatus.SECRET_LOST) add(stringResource(Res.string.addon_update_now) to onUpdate)
        if (!addon.isFromSource) {
            if (addon.manifest.hints.isConfigurable && addon.transport != null) add(stringResource(Res.string.addon_configure) to onConfigure)
            // A source's addon goes away with the source (About this source > Remove source).
            add(stringResource(Res.string.addon_remove) to onRemove)
        }
    }
    if (asDropdown) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onDismiss(); action() })
            }
        }
    } else {
        val first = remember { FocusRequester() }
        items.forEachIndexed { i, (label, action) ->
            TvMenuItem(
                title = label,
                onClick = { onDismiss(); action() },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        LaunchedEffect(Unit) { first.requestFocusAfterLayout() }
    }
}

@Composable
fun ConfirmDialog(message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AddonDialog(title = null, onDismissRequest = onDismiss) {
        Text(message, color = TSColors.TextPrimary, fontSize = 16.sp)
        val focus = remember { FocusRequester() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton(stringResource(Res.string.cancel), onClick = onDismiss, primary = false, modifier = Modifier.focusRequester(focus))
            PillButton(confirm, onClick = onConfirm)
        }
        LaunchedEffect(Unit) { focus.requestFocusAfterLayout() }
    }
}

/** Add addon: URL → Continue → preview (warnings, notice, 18+) → Add. Focus: field → Continue → Cancel. */
@Composable
private fun AddAddonDialog(
    state: AddAddonState,
    onContinue: (String) -> Unit,
    onInstall: (AddonPreview) -> Unit,
    onConfigure: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is AddAddonState.Preview -> PreviewDialog(state.preview, onInstall = onInstall, onDismiss = onDismiss)
        else -> UrlDialog(state, onContinue, onConfigure, onDismiss)
    }
}

@Composable
private fun UrlDialog(
    state: AddAddonState,
    onContinue: (String) -> Unit,
    onConfigure: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val isTv = LocalIsTvMode.current
    val clipboard = LocalClipboardManager.current
    val needsConfig = state as? AddAddonState.NeedsConfiguration
    AddonDialog(title = stringResource(Res.string.addon_add), onDismissRequest = onDismiss) {
        if (needsConfig != null) {
            Text(stringResource(Res.string.addon_needs_configuration), color = TSColors.TextSecondaryLight, fontSize = 15.sp)
            PillButton(stringResource(Res.string.addon_configure), onClick = { onConfigure(needsConfig.configureUrl) })
            if (isTv) SelectionContainer { Text(needsConfig.configureUrl, color = TSColors.TextSecondary, fontSize = 13.sp) }
        }
        TSTextField(
            modifier = Modifier.focusRequester(fieldFocus).onPreviewKeyEvent { event ->
                // A text field keeps ▲/▼ for its cursor; let the D-pad leave it.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> { continueFocus.requestFocus(); true }
                    else -> false
                }
            },
            value = url,
            onValueChange = { url = it },
            label = stringResource(if (needsConfig != null) Res.string.addon_configured_url_label else Res.string.addon_url_label),
            isError = state is AddAddonState.Failed,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onContinue(url) else continueFocus.requestFocus() }),
            trailingIcon = if (isTv) null else {
                {
                    IconButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(Res.string.addon_paste), tint = TSColors.TextSecondaryLight)
                    }
                }
            },
        )
        (state as? AddAddonState.Failed)?.let { ErrorText(it.error) }
        if (state == AddAddonState.Loading) {
            CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally), color = TSColors.AccentCyan)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton(
                stringResource(if (state is AddAddonState.Failed && (state.error == AddAddonError.Unreachable)) Res.string.addon_retry else Res.string.addon_continue),
                onClick = { if (url.isNotBlank()) onContinue(url) },
                enabled = url.isNotBlank() && state != AddAddonState.Loading,
                modifier = Modifier.focusRequester(continueFocus),
            )
            PillButton(stringResource(Res.string.cancel), onClick = onDismiss, primary = false, modifier = Modifier.focusRequester(cancelFocus))
        }
    }
    LaunchedEffect(Unit) { fieldFocus.requestFocusAfterLayout() }
}

@Composable
private fun ErrorText(error: AddAddonError) {
    val text = when (error) {
        is AddAddonError.InvalidUrl -> when (error.error) {
            tss.t.tsiptv.core.stremio.ManifestUrlError.NOT_MANIFEST -> stringResource(Res.string.addon_error_not_manifest)
            tss.t.tsiptv.core.stremio.ManifestUrlError.LEGACY -> stringResource(Res.string.addon_error_legacy)
            tss.t.tsiptv.core.stremio.ManifestUrlError.LOCAL_SERVER -> stringResource(Res.string.addon_error_local_server)
        }
        AddAddonError.Unreachable -> stringResource(Res.string.addon_error_unreachable)
        is AddAddonError.InvalidManifest -> stringResource(Res.string.addon_error_invalid_manifest) +
            (error.field?.let { "\n" + stringResource(Res.string.addon_error_missing_field, it) } ?: "")
        AddAddonError.Blocked -> stringResource(Res.string.addon_blocked)
    }
    Text(text, color = TSColors.ErrorRed, fontSize = 14.sp)
}

@Composable
private fun PreviewDialog(preview: AddonPreview, onInstall: (AddonPreview) -> Unit, onDismiss: () -> Unit) {
    var adultConfirmed by remember(preview) { mutableStateOf(false) }
    val addFocus = remember { FocusRequester() }
    val manifest = preview.manifest
    AddonDialog(title = null, onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AddonLogo(manifest.logoUrl, Modifier.size(56.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(manifest.name, color = TSColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.addon_version, manifest.versionText) + " · " + preview.transport.displayHost,
                    color = TSColors.TextSecondary, fontSize = 13.sp,
                )
            }
        }
        manifest.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = TSColors.TextSecondaryLight, fontSize = 14.sp) }
        Text(
            stringResource(Res.string.addon_types, manifest.typeList.map { addonTypeLabel(it) }.joinToString(", ")),
            color = TSColors.TextSecondaryLight, fontSize = 14.sp,
        )
        Text(stringResource(Res.string.addon_catalogs, preview.catalogCount), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
        Text(
            stringResource(if (preview.providesStreams) Res.string.addon_provides_streams else Res.string.addon_no_streams),
            color = TSColors.TextSecondaryLight, fontSize = 14.sp,
        )
        if (preview.isHttp) Warning(stringResource(Res.string.addon_warn_http))
        if (preview.isP2p) Warning(stringResource(Res.string.addon_warn_p2p))
        if (preview.isAdult) Warning(stringResource(Res.string.addon_warn_adult))
        Box(Modifier.fillMaxWidth().background(TSColors.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(12.dp)) {
            Text(stringResource(Res.string.addon_notice), color = TSColors.TextPrimary, fontSize = 14.sp)
        }
        if (preview.isAdult) {
            TvFocusableSurface(
                onClick = { adultConfirmed = !adultConfirmed },
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.02f,
                color = TSColors.Transparent,
            ) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = adultConfirmed,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = TSColors.AccentCyan),
                    )
                    Text(stringResource(Res.string.addon_confirm_adult), color = TSColors.TextPrimary, fontSize = 15.sp)
                }
            }
        }
        preview.existing?.let {
            Text(stringResource(Res.string.addon_replace_existing, it.name), color = TSColors.LoadingYellow, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton(
                stringResource(if (preview.existing != null) Res.string.addon_replace else Res.string.addon_add),
                onClick = { onInstall(preview) },
                enabled = !preview.isAdult || adultConfirmed,
                modifier = Modifier.focusRequester(addFocus).widthIn(min = 96.dp),
            )
            PillButton(stringResource(Res.string.cancel), onClick = onDismiss, primary = false)
        }
    }
    LaunchedEffect(preview) { addFocus.requestFocusAfterLayout() }
}

@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = TSColors.LoadingYellow, modifier = Modifier.size(18.dp))
        Text(text, color = TSColors.LoadingYellow, fontSize = 14.sp)
    }
}
