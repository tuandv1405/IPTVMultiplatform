package tss.t.tsiptv.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.feature.account.ApplyResult
import tss.t.tsiptv.feature.account.DeviceSessionManager
import tss.t.tsiptv.feature.account.PushResult
import tss.t.tsiptv.feature.account.QuotaService
import tss.t.tsiptv.feature.account.QuotaState
import tss.t.tsiptv.feature.account.RegisteredDevice
import tss.t.tsiptv.feature.account.RewardPlacement
import tss.t.tsiptv.feature.account.RewardResult
import tss.t.tsiptv.feature.account.SyncDocument
import tss.t.tsiptv.feature.account.SyncMode
import tss.t.tsiptv.feature.account.SyncPlan
import tss.t.tsiptv.feature.account.SyncPlanner
import tss.t.tsiptv.feature.account.SyncService
import tss.t.tsiptv.feature.lan.PairedPeer
import tss.t.tsiptv.feature.lan.PairingStore

/** One-shot results shown as a dialog / snackbar. */
sealed interface ConnectMessage {
    data class Pushed(val count: Int, val skippedFiles: Int) : ConnectMessage
    data object SyncNoneLeft : ConnectMessage
    data object SyncTooLarge : ConnectMessage
    data object SyncFailed : ConnectMessage
    data class Applied(val result: ApplyResult) : ConnectMessage
    data object RewardGranted : ConnectMessage
    data class RewardProgress(val watched: Long) : ConnectMessage
    data object RewardUnavailable : ConnectMessage
    data object RewardCapped : ConnectMessage
    data object DevicesFailed : ConnectMessage
}

data class PlanPreview(val doc: SyncDocument, val mode: SyncMode, val plan: SyncPlan)

data class ConnectUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val myDeviceId: String = "",
    val devices: List<RegisteredDevice> = emptyList(),
    val quota: QuotaState? = null,
    val remainingSyncs: Long? = null,
    val canEarnSync: Boolean = false,
    /** Unlimited plan: no counter, no rewarded task (docs/prd-subscriptions.md §2.2). */
    val unlimitedSyncs: Boolean = false,
    val rewards: tss.t.tsiptv.feature.account.RewardAvailability = tss.t.tsiptv.feature.account.RewardAvailability.UNSUPPORTED,
    val currentSync: SyncDocument? = null,
    val incoming: SyncDocument? = null,
    val pushableCount: Int = 0,
    val skippedFileCount: Int = 0,
    val preview: PlanPreview? = null,
    val applying: Pair<Int, Int>? = null,
    val busy: Boolean = false,
    val pairedTvs: List<PairedPeer> = emptyList(),
    val message: ConnectMessage? = null,
)

/** "TV & thiết bị": devices (C), sync (D) and paired TVs. */
class ConnectViewModel(
    private val sessions: DeviceSessionManager,
    private val quotas: QuotaService,
    private val sync: SyncService,
    private val senderStore: PairingStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state

    init {
        viewModelScope.launch { sync.incoming.collect { doc -> _state.update { it.copy(incoming = doc) } } }
        viewModelScope.launch { senderStore.peers.collect { peers -> _state.update { it.copy(pairedTvs = peers) } } }
        viewModelScope.launch { senderStore.load() }
    }

    fun refresh() {
        viewModelScope.launch {
            val signedIn = sessions.uid.value != null
            val build = SyncPlanner.build(sync.localPlaylists())
            _state.update {
                it.copy(
                    signedIn = signedIn,
                    loading = signedIn,
                    myDeviceId = sessions.myDeviceId(),
                    pushableCount = build.payload.playlists.size,
                    skippedFileCount = build.skippedFiles.size,
                    rewards = quotas.rewardAvailability(),
                )
            }
            if (!signedIn) return@launch
            quotas.preloadReward(RewardPlacement.EXTRA_SYNC)
            sessions.check()
            val devices = sessions.listDevices()
            val quota = runCatching { quotas.current() }.getOrNull()
            sync.refresh()
            val current = sync.current()
            val policy = quotas.policy
            _state.update {
                it.copy(
                    loading = false,
                    devices = devices ?: it.devices,
                    quota = quota,
                    remainingSyncs = quota?.let(policy::remainingSyncs),
                    canEarnSync = quota?.let(policy::canEarnSyncReward) ?: false,
                    unlimitedSyncs = policy.unlimited,
                    currentSync = current,
                    message = if (devices == null) ConnectMessage.DevicesFailed else it.message,
                )
            }
        }
    }

    fun signOutRemote(device: RegisteredDevice) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val ok = sessions.signOutRemote(device.id)
            _state.update { it.copy(busy = false, message = if (ok) it.message else ConnectMessage.DevicesFailed) }
            refresh()
        }
    }

    fun push() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val message = when (val result = sync.push()) {
                is PushResult.Pushed -> ConnectMessage.Pushed(result.count, result.skippedFiles)
                PushResult.NoneLeft -> ConnectMessage.SyncNoneLeft
                PushResult.TooLarge -> ConnectMessage.SyncTooLarge
                PushResult.SignedOut, PushResult.Failed -> ConnectMessage.SyncFailed
            }
            _state.update { it.copy(busy = false, message = message) }
            refresh()
        }
    }

    fun watchAdForSync() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val message = when (val r = quotas.watchAd(RewardPlacement.EXTRA_SYNC)) {
                RewardResult.Granted -> ConnectMessage.RewardGranted
                is RewardResult.Progress -> ConnectMessage.RewardProgress(r.watched)
                RewardResult.Capped -> ConnectMessage.RewardCapped
                RewardResult.Dismissed -> null
                RewardResult.Unavailable, RewardResult.Failed, RewardResult.SignedOut -> ConnectMessage.RewardUnavailable
            }
            _state.update { it.copy(busy = false, message = message) }
            refresh()
        }
    }

    /** Merge or Replace on the incoming sync: computes the plan; Replace waits for confirmation. */
    fun preview(doc: SyncDocument, mode: SyncMode) {
        viewModelScope.launch {
            val plan = sync.plan(doc, mode) ?: run {
                _state.update { it.copy(message = ConnectMessage.SyncFailed) }
                return@launch
            }
            if (mode == SyncMode.MERGE) apply(PlanPreview(doc, mode, plan)) else _state.update { it.copy(preview = PlanPreview(doc, mode, plan)) }
        }
    }

    fun confirmPreview() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(preview = null) }
        viewModelScope.launch { apply(preview) }
    }

    fun cancelPreview() = _state.update { it.copy(preview = null) }

    /** Called with the applied result so Home can pick a playlist if the current one went away. */
    var onApplied: ((ApplyResult) -> Unit)? = null

    private suspend fun apply(preview: PlanPreview) {
        _state.update { it.copy(applying = 0 to 1) }
        val result = try {
            sync.apply(preview.doc, preview.plan) { done, total -> _state.update { it.copy(applying = done to total) } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        _state.update { it.copy(applying = null, message = result?.let(ConnectMessage::Applied) ?: ConnectMessage.SyncFailed) }
        result?.let { onApplied?.invoke(it) }
        refresh()
    }

    fun dismissIncoming(doc: SyncDocument) {
        viewModelScope.launch { sync.dismiss(doc) }
    }

    fun forgetTv(id: String) {
        viewModelScope.launch { senderStore.remove(id) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
