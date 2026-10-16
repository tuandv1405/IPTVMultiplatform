package tss.t.tsiptv.feature.lan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tss.t.tsiptv.feature.account.DeviceSessionManager
import tss.t.tsiptv.feature.account.QuotaService
import tss.t.tsiptv.feature.account.RewardPlacement
import tss.t.tsiptv.feature.account.RewardResult

/**
 * A TV in the picker. [online]: it answers now (true), it was not found (false), or the phone is
 * still looking for it (null; only for a paired TV, which is listed before it is found).
 */
data class TvTarget(val device: LanDevice, val paired: Boolean, val online: Boolean? = true)

/** Daily send quota, for the playlist sheet. */
data class SendQuotaInfo(
    val signedIn: Boolean,
    val remaining: Long?,
    val canEarn: Boolean,
    val rewards: tss.t.tsiptv.feature.account.RewardAvailability,
    /** The quota side of the plan (docs/prd-subscriptions.md §2.2): wording, tasks and upgrade row. */
    val plan: tss.t.tsiptv.feature.account.QuotaPlan = tss.t.tsiptv.feature.account.QuotaPlan.FREE,
)

sealed interface TvSendStep {
    data object Pick : TvSendStep
    data object Connecting : TvSendStep
    data class EnterCode(val tvName: String, val error: TvSendMessage? = null) : TvSendStep
    data object Sending : TvSendStep
    data class Done(val tvName: String) : TvSendStep
}

/** Messages the UI turns into strings (PRD §9). */
enum class TvSendMessage {
    UNREACHABLE, WRONG_CODE, PAIR_FAILED, PAIR_BUSY, PAIR_LOCKED, CLOCK, REJECTED, NOT_SUPPORTED,
    IP_INVALID, QUOTA_REACHED, SIGN_IN, REWARD_GRANTED, REWARD_UNAVAILABLE, REWARD_CAPPED,

    /** The TV ended the pairing (3 wrong codes, or the 2 minutes passed): start again. */
    CODE_EXPIRED,

    /** NOT_ACCEPTING: the TV cannot take a playlist now (open TS IPTV home on the TV). */
    TV_BUSY,

    /** PAIRING_CLOSED: pairing needs the TV's "TV & devices" screen open. */
    OPEN_TV_SCREEN,

    /** The TV has the playlist, but today's send could not be counted (shown on the Done step). */
    SEND_NOT_COUNTED,
}

data class TvSendUiState(
    val supported: Boolean = false,
    val searching: Boolean = false,
    /** The address scan and the paired-TV checks are running ([TvSendViewModel.searchAgain]). */
    val scanning: Boolean = false,
    /** Paired TVs first (found or not), then other TVs found on this Wi-Fi. */
    val targets: List<TvTarget> = emptyList(),
    val step: TvSendStep = TvSendStep.Pick,
    val message: TvSendMessage? = null,
    val quota: SendQuotaInfo? = null,
    val busyReward: Boolean = false,
)

/**
 * Phone side of A (cast) and B (send playlist): browse TVs, pair on first use, send. A playlist send
 * needs the account's daily quota (PRD §3.2); a cast does not.
 */
class TvSendViewModel(
    private val sender: LanSender,
    private val discovery: LanDiscovery,
    private val quotas: QuotaService,
    private val sessions: DeviceSessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TvSendUiState(supported = discovery.isSupported && sender.isSupported))
    val state: StateFlow<TvSendUiState> = _state

    private var command: LanCommand? = null
    private var discoveryJob: Job? = null
    private var scanJob: Job? = null

    // Picker sources, merged by [publishTargets] (main thread only).
    private var paired: List<PairedPeer> = emptyList()
    private var browsed: List<LanDevice> = emptyList()
    private val answered = LinkedHashMap<String, LanDevice>()
    private var pairing: PairingHandle? = null
    private var wrongCodes = 0
    private var target: LanDevice? = null

    private val isPlaylist get() = command is PlaylistCommand

    /** Opens the sheet for [command]. */
    fun open(command: LanCommand) {
        this.command = command
        pairing = null
        target = null
        _state.update { it.copy(step = TvSendStep.Pick, message = null, quota = null) }
        startDiscovery()
        if (command is PlaylistCommand) {
            refreshQuota()
            quotas.preloadReward(RewardPlacement.EXTRA_SEND)
        }
    }

    fun close() {
        discoveryJob?.cancel()
        discoveryJob = null
        scanJob?.cancel()
        scanJob = null
        command = null
        _state.update { it.copy(searching = false, scanning = false) }
    }

    /**
     * Three sources, so a TV shows up even where mDNS never works (multicast blocked by the router
     * or filtered by the TV's Wi-Fi, while "connect by IP" works):
     * 1. TVs paired by code, listed at once and checked at the address they last answered on;
     * 2. mDNS (`_tsiptv._tcp`);
     * 3. a scan of this Wi-Fi's addresses on [LanProtocol.DEFAULT_PORT] ([LanScan]).
     */
    private fun startDiscovery() {
        if (!_state.value.supported) return
        discoveryJob?.cancel()
        scanJob?.cancel()
        scanJob = null
        browsed = emptyList()
        answered.clear()
        _state.update { it.copy(searching = true, scanning = false, targets = emptyList()) }
        discoveryJob = viewModelScope.launch {
            discovery.discover().collect { devices ->
                browsed = devices
                publishTargets()
            }
        }
        searchAgain()
    }

    /** Checks the paired TVs and scans this Wi-Fi again (mDNS keeps browsing meanwhile). */
    fun searchAgain() {
        if (!_state.value.supported || _state.value.scanning) return
        scanJob?.cancel()
        _state.update { it.copy(scanning = true) }
        // Lazy: the job is known as [scanJob] before its body runs (Main.immediate).
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                paired = sender.pairedTvs()
                publishTargets()
                coroutineScope {
                    // A paired TV where it answered last: usually the quickest way to show it ready.
                    paired.forEach { tv ->
                        val host = tv.host
                        val port = tv.port
                        if (host != null && port != null) launch { probe(host, port) }
                    }
                    // Every address of this Wi-Fi, on the default port and on the last ports of
                    // paired TVs (a TV with an older build, or whose default port was taken).
                    val ports = (listOf(LanProtocol.DEFAULT_PORT) + paired.mapNotNull { it.port }).distinct().take(MAX_SCAN_PORTS)
                    val gate = Semaphore(LanScan.PARALLEL)
                    val hosts = withContext(Dispatchers.Default) { LanScan.hosts(discovery.localNetworks()) }
                    hosts.forEach { host ->
                        ports.forEach { port -> launch { gate.withPermit { probe(host, port) } } }
                    }
                }
            } finally {
                // A scan cancelled by close() must not end the next sheet's scan.
                if (scanJob === coroutineContext[Job]) {
                    _state.update { it.copy(scanning = false) }
                    publishTargets()
                }
            }
        }
        scanJob = job
        job.start()
    }

    private suspend fun probe(host: String, port: Int) {
        val device = sender.hello(host, port, quick = true) ?: return
        answered[device.id] = device
        if (paired.any { it.id == device.id }) sender.remember(device)
        publishTargets()
    }

    private fun publishTargets() {
        // One entry per TV: an answer to hello is checked, so it wins over an mDNS record.
        val found = LinkedHashMap<String, LanDevice>()
        browsed.forEach { found[it.id.ifEmpty { "${it.host}:${it.port}" }] = it }
        answered.forEach { (id, device) -> found[id] = device }
        val scanning = _state.value.scanning
        val pairedIds = paired.map { it.id }.toSet()
        val mine = paired.sortedBy { it.name.lowercase() }.map { tv ->
            val device = found[tv.id]
            TvTarget(
                device = device ?: LanDevice(id = tv.id, name = tv.name, host = tv.host.orEmpty(), port = tv.port ?: 0),
                paired = true,
                online = if (device != null) true else if (scanning) null else false,
            )
        }
        val others = found.values.filter { it.id !in pairedIds }.sortedBy { it.name.lowercase() }.map { TvTarget(it, paired = false) }
        _state.update { it.copy(targets = mine + others) }
    }

    private fun refreshQuota() {
        viewModelScope.launch {
            val signedIn = sessions.uid.value != null
            val q = try {
                quotas.current()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val policy = quotas.policy
            _state.update {
                it.copy(
                    quota = SendQuotaInfo(
                        signedIn = signedIn,
                        remaining = q?.let(policy::remainingSends),
                        canEarn = q?.let(policy::canEarnSendReward) ?: false,
                        rewards = quotas.rewardAvailability(),
                        plan = policy.plan,
                    ),
                )
            }
        }
    }

    fun connectByIp(text: String) {
        val (host, port) = LanValidation.parseHostPort(text) ?: run {
            _state.update { it.copy(message = TvSendMessage.IP_INVALID) }
            return
        }
        _state.update { it.copy(step = TvSendStep.Connecting, message = null) }
        viewModelScope.launch {
            val device = sender.hello(host, port)
            if (device == null) {
                _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.UNREACHABLE) }
            } else {
                pick(device)
            }
        }
    }

    fun pick(device: LanDevice) {
        target = device
        _state.update { it.copy(message = null) }
        viewModelScope.launch {
            if (isPlaylist && !checkQuota()) return@launch
            if (sender.isPaired(device)) send(device) else beginPairing(device)
        }
    }

    private suspend fun checkQuota(): Boolean {
        if (sessions.uid.value == null) {
            _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.SIGN_IN) }
            return false
        }
        val q = try {
            quotas.current()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.UNREACHABLE) }
            return false
        }
        if (q == null || !quotas.policy.canSend(q)) {
            _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.QUOTA_REACHED) }
            refreshQuota()
            return false
        }
        return true
    }

    private suspend fun beginPairing(device: LanDevice) {
        _state.update { it.copy(step = TvSendStep.Connecting) }
        when (val result = sender.startPairing(device)) {
            is PairStartResult.Started -> {
                pairing = result.handle
                wrongCodes = 0
                target = result.handle.device
                _state.update { it.copy(step = TvSendStep.EnterCode(result.handle.device.name)) }
            }
            is PairStartResult.Failed -> _state.update { it.copy(step = TvSendStep.Pick, message = messageFor(result.result)) }
        }
    }

    fun submitCode(code: String) {
        val handle = pairing ?: return
        val digits = code.filter { it.isDigit() }
        if (digits.length != 6) {
            _state.update { it.copy(step = TvSendStep.EnterCode(handle.device.name, TvSendMessage.WRONG_CODE)) }
            return
        }
        _state.update { it.copy(step = TvSendStep.Connecting) }
        viewModelScope.launch {
            when (val result = sender.confirmPairing(handle, digits)) {
                LanResult.Ok -> {
                    pairing = null
                    send(handle.device)
                }
                is LanResult.Refused -> when (result.code) {
                    LanErrorCode.WRONG_CODE -> {
                        wrongCodes++
                        if (wrongCodes >= LanProtocol.PAIR_MAX_WRONG_CODES) {
                            // The TV closed this pairing after the 3rd wrong code.
                            pairing = null
                            _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.CODE_EXPIRED) }
                        } else {
                            _state.update { it.copy(step = TvSendStep.EnterCode(handle.device.name, TvSendMessage.WRONG_CODE)) }
                        }
                    }
                    LanErrorCode.EXPIRED -> {
                        pairing = null
                        _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.CODE_EXPIRED) }
                    }
                    else -> {
                        pairing = null
                        _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.PAIR_FAILED) }
                    }
                }
                else -> {
                    pairing = null
                    _state.update { it.copy(step = TvSendStep.Pick, message = TvSendMessage.UNREACHABLE) }
                }
            }
        }
    }

    fun cancelPairing() {
        pairing = null
        _state.update { it.copy(step = TvSendStep.Pick) }
    }

    private suspend fun send(device: LanDevice) {
        val cmd = command ?: return
        _state.update { it.copy(step = TvSendStep.Sending) }
        when (val result = sender.send(device, cmd)) {
            LanResult.Ok -> {
                answered[device.id] = device
                paired = sender.pairedTvs()
                var counted = true
                if (cmd is PlaylistCommand) {
                    counted = recordSendWithRetry(LanValidation.networkId(device.host))
                    refreshQuota()
                }
                _state.update { s ->
                    s.copy(
                        step = TvSendStep.Done(device.name),
                        message = if (counted) null else TvSendMessage.SEND_NOT_COUNTED,
                    )
                }
                publishTargets()
            }
            LanResult.Unpaired -> beginPairing(device)
            else -> _state.update { it.copy(step = TvSendStep.Pick, message = messageFor(result)) }
        }
    }

    /**
     * The TV already accepted the playlist, so the send is counted even on a flaky network: up to
     * [RECORD_ATTEMPTS] tries with a short back-off. false when it still could not be written.
     */
    private suspend fun recordSendWithRetry(networkId: String?): Boolean {
        repeat(RECORD_ATTEMPTS) { attempt ->
            if (quotas.recordSend(networkId)) return true
            if (attempt < RECORD_ATTEMPTS - 1) kotlinx.coroutines.delay(RECORD_BACKOFF_MS * (attempt + 1))
        }
        return false
    }

    fun watchAdForSend() {
        if (_state.value.busyReward) return
        _state.update { it.copy(busyReward = true, message = null) }
        viewModelScope.launch {
            val message = when (quotas.watchAd(RewardPlacement.EXTRA_SEND)) {
                RewardResult.Granted, is RewardResult.Progress -> TvSendMessage.REWARD_GRANTED
                RewardResult.Capped -> TvSendMessage.REWARD_CAPPED
                RewardResult.SignedOut -> TvSendMessage.SIGN_IN
                RewardResult.Dismissed -> null
                RewardResult.Unavailable, RewardResult.Failed -> TvSendMessage.REWARD_UNAVAILABLE
            }
            _state.update { it.copy(busyReward = false, message = message) }
            refreshQuota()
        }
    }

    fun forget(device: LanDevice) {
        viewModelScope.launch {
            sender.forget(device.id)
            paired = sender.pairedTvs()
            publishTargets()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun messageFor(result: LanResult): TvSendMessage = when (result) {
        LanResult.Ok -> TvSendMessage.REJECTED
        LanResult.Unpaired -> TvSendMessage.PAIR_FAILED
        LanResult.Unreachable -> TvSendMessage.UNREACHABLE
        is LanResult.Refused -> when (result.code) {
            LanErrorCode.BUSY -> TvSendMessage.PAIR_BUSY
            LanErrorCode.LOCKED -> TvSendMessage.PAIR_LOCKED
            LanErrorCode.EXPIRED -> TvSendMessage.CLOCK
            LanErrorCode.NOT_ACCEPTING -> TvSendMessage.TV_BUSY
            LanErrorCode.PAIRING_CLOSED -> TvSendMessage.OPEN_TV_SCREEN
            LanErrorCode.TOO_LARGE -> TvSendMessage.REJECTED
            else -> TvSendMessage.REJECTED
        }
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        scanJob?.cancel()
    }

    private companion object {
        /** Ports asked on each address: the default one and up to two of paired TVs. */
        const val MAX_SCAN_PORTS = 3

        const val RECORD_ATTEMPTS = 3
        const val RECORD_BACKOFF_MS = 1_000L
    }
}
