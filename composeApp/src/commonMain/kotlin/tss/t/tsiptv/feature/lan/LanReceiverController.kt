package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.tsiptv.feature.account.LocalDevice

sealed interface ReceiverState {
    data object Stopped : ReceiverState
    data class Running(val name: String, val port: Int, val addresses: List<String>) : ReceiverState
    data object Failed : ReceiverState
    data object Unsupported : ReceiverState
}

/**
 * Runs the TV receiver while the app is in the foreground (PRD §2.4): [start] from `onResume`,
 * [stop] from `onPause`. Holds the engine, so pairing state survives a quick pause.
 */
class LanReceiverController(
    private val server: LanServer,
    private val local: LocalDevice,
    private val store: PairingStore,
    keyAgreement: LanKeyAgreementFactory,
    clock: () -> Long,
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

    suspend fun start() = mutex.withLock {
        if (!server.isSupported || _state.value is ReceiverState.Running) return@withLock
        store.load()
        _state.value = try {
            val name = local.name()
            val port = server.start(name, local.installationId()) { line -> engine.handle(line) }
            ReceiverState.Running(name, port, server.localAddresses())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ReceiverState.Failed
        }
    }

    suspend fun stop() = mutex.withLock {
        if (_state.value !is ReceiverState.Running) return@withLock
        engine.cancelPairing()
        server.stop()
        _state.value = ReceiverState.Stopped
    }

    suspend fun unpair(id: String) = store.remove(id)
}
