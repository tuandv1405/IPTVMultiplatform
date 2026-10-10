package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.feature.account.LocalDevice

sealed interface ReceiverState {
    data object Stopped : ReceiverState
    data class Running(val name: String, val port: Int, val addresses: List<String>) : ReceiverState
    data object Failed : ReceiverState
    data object Unsupported : ReceiverState
}

/**
 * Runs the TV receiver while the app is in the foreground (PRD §2.4). The host calls
 * [setForeground] from `onResume` / `onPause`; one worker applies the latest wish in order, so a
 * quick resume/pause/resume can never leave the server running in the background, nor stopped in
 * the foreground. Holds the engine, so pairing state survives a quick pause.
 */
class LanReceiverController(
    private val server: LanServer,
    private val local: LocalDevice,
    private val store: PairingStore,
    keyAgreement: LanKeyAgreementFactory,
    private val storage: KeyValueStorage,
    clock: () -> Long,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val engine = LanReceiverEngine(
        identity = { local.installationId() to local.name() },
        store = store,
        keyAgreement = keyAgreement,
        clock = clock,
    )

    private val _state = MutableStateFlow<ReceiverState>(if (server.isSupported) ReceiverState.Stopped else ReceiverState.Unsupported)
    val state: StateFlow<ReceiverState> = _state
    val pairingPrompt: StateFlow<PairingPrompt?> = engine.pairingPrompt
    val events: SharedFlow<LanReceiverEvent> = engine.events
    val pairedPhones: StateFlow<List<PairedPeer>> = store.peers

    private val mutex = Mutex()
    private val foreground = MutableStateFlow(false)

    init {
        if (server.isSupported) {
            scope.launch {
                // Sequential: each change waits for the previous start/stop to finish; StateFlow
                // keeps only the latest wish.
                foreground.collect { on -> if (on) start() else stop() }
            }
        }
    }

    /** `onResume` → true, `onPause` / dispose → false. Never blocks. */
    fun setForeground(on: Boolean) {
        foreground.value = on
    }

    suspend fun start() = mutex.withLock {
        if (!server.isSupported || _state.value is ReceiverState.Running) return@withLock
        if (!foreground.value) return@withLock
        store.load()
        _state.value = try {
            val name = local.name()
            val preferred = storage.getInt(KEY_PORT, 0)
            val port = server.start(
                serviceName = name,
                deviceId = local.installationId(),
                preferredPort = preferred,
                largeRequestAllowed = engine::allowsLargeRequest,
            ) { line -> engine.handle(line) }
            // A stable port per install ("connect by IP" keeps working); a new one when it was busy.
            if (port != preferred) storage.putInt(KEY_PORT, port)
            ReceiverState.Running(name, port, server.localAddresses())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ReceiverState.Failed
        }
    }

    suspend fun stop() = mutex.withLock {
        if (_state.value !is ReceiverState.Running) return@withLock
        engine.abortPairing()
        server.stop()
        _state.value = ReceiverState.Stopped
    }

    suspend fun unpair(id: String) = store.remove(id)

    companion object {
        const val KEY_PORT = "lan_receiver_port"
    }
}
