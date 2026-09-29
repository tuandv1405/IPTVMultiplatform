package tss.t.tsiptv.ui.screens.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.firebase.analystics.AnalyticsConstants
import tss.t.tsiptv.core.stremio.AddonPreview
import tss.t.tsiptv.core.stremio.AddonPreviewResult
import tss.t.tsiptv.core.stremio.AddonRefreshResult
import tss.t.tsiptv.core.stremio.AddonRepository
import tss.t.tsiptv.core.stremio.InstalledAddon
import tss.t.tsiptv.core.stremio.ManifestUrlError

/** The add-addon flow (PRD §1): URL → preview (warnings, notice) → saved. */
sealed interface AddAddonState {
    data object Idle : AddAddonState
    data object Loading : AddAddonState
    data class Failed(val error: AddAddonError) : AddAddonState
    data class NeedsConfiguration(val configureUrl: String, val host: String) : AddAddonState
    data class Preview(val preview: AddonPreview) : AddAddonState
}

sealed interface AddAddonError {
    data class InvalidUrl(val error: ManifestUrlError) : AddAddonError
    data object Unreachable : AddAddonError
    data class InvalidManifest(val field: String?) : AddAddonError
    data object Blocked : AddAddonError
}

sealed interface AddonsMessage {
    data object Added : AddonsMessage
    data class Updated(val version: String) : AddonsMessage
    data object UpdateFailed : AddonsMessage
    data object Blocked : AddonsMessage
    /** Saving failed (e.g. the secure key store is unavailable). */
    data object InstallFailed : AddonsMessage
}

class AddonsViewModel(private val repository: AddonRepository) : ViewModel() {
    val addons: StateFlow<List<InstalledAddon>> = repository.addons

    private val _addState = MutableStateFlow<AddAddonState>(AddAddonState.Idle)
    val addState: StateFlow<AddAddonState> = _addState.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    private val _messages = Channel<AddonsMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var previewJob: Job? = null

    init {
        // The kill switch is also checked when the manager opens (still at most once a day).
        viewModelScope.launch { repository.checkBlocklistIfDue() }
    }

    /** Continue: normalise, fetch and validate the manifest. */
    fun preview(input: String) {
        previewJob?.cancel()
        _addState.value = AddAddonState.Loading
        previewJob = viewModelScope.launch {
            _addState.value = try {
                when (val r = repository.preview(input)) {
                    is AddonPreviewResult.Ready -> AddAddonState.Preview(r.preview)
                    is AddonPreviewResult.InvalidUrl -> AddAddonState.Failed(AddAddonError.InvalidUrl(r.error))
                    AddonPreviewResult.Unreachable -> AddAddonState.Failed(AddAddonError.Unreachable)
                    is AddonPreviewResult.InvalidManifest -> AddAddonState.Failed(AddAddonError.InvalidManifest(r.field))
                    is AddonPreviewResult.NeedsConfiguration -> AddAddonState.NeedsConfiguration(r.configureUrl, r.host)
                    AddonPreviewResult.Blocked -> AddAddonState.Failed(AddAddonError.Blocked)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AddAddonState.Failed(AddAddonError.Unreachable)
            }
        }
    }

    /** Add (after the notice and, for adult addons, the 18+ confirmation). */
    fun install(preview: AddonPreview) {
        viewModelScope.launch {
            try {
                repository.install(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _addState.value = AddAddonState.Idle
                _messages.send(AddonsMessage.InstallFailed)
                return@launch
            }
            _addState.value = AddAddonState.Idle
            Firebase.analytics.logEvent(AnalyticsConstants.EVENT_ADD_ADDON, emptyMap())
            _messages.send(AddonsMessage.Added)
        }
    }

    fun resetAdd() {
        previewJob?.cancel()
        _addState.value = AddAddonState.Idle
    }

    fun setEnabled(addon: InstalledAddon, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(addon.id, enabled) }
    }

    fun move(addon: InstalledAddon, delta: Int) {
        viewModelScope.launch { repository.move(addon.id, delta) }
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch { repository.reorder(ids) }
    }

    fun remove(addon: InstalledAddon) {
        viewModelScope.launch { repository.remove(addon.id) }
    }

    fun refresh(addon: InstalledAddon) {
        if (addon.id in _refreshing.value) return
        _refreshing.value = _refreshing.value + addon.id
        viewModelScope.launch {
            try {
                when (val r = repository.refresh(addon.id)) {
                    is AddonRefreshResult.Updated -> _messages.send(AddonsMessage.Updated(r.version))
                    AddonRefreshResult.Unchanged -> Unit
                    AddonRefreshResult.Failed -> _messages.send(AddonsMessage.UpdateFailed)
                    AddonRefreshResult.Blocked -> {
                        repository.checkBlocklistIfDue(force = true)
                        _messages.send(AddonsMessage.Blocked)
                    }
                }
            } finally {
                _refreshing.value = _refreshing.value - addon.id
            }
        }
    }
}
