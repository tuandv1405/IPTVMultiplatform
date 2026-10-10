package tss.t.tsiptv.feature.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.feature.auth.data.repository.SignOutHooks
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository

/** What the device-limit UI shows (PRD §4.2). */
sealed interface DeviceGate {
    data object None : DeviceGate

    /** 4 other devices are registered: pick one to sign out remotely, or cancel sign-in. */
    data class LimitReached(val devices: List<RegisteredDevice>, val busy: Boolean = false) : DeviceGate

    /** This device was removed from another device and has been signed out. */
    data object RemovedElsewhere : DeviceGate
}

/**
 * Keeps this installation inside the account's device limit: registers it at sign-in, refreshes
 * `lastSeen`, signs it out when another device removed it, and removes its own entry at sign-out.
 * Network failures leave everything as it is (no lock-out while offline).
 */
class DeviceSessionManager(
    private val auth: AuthRepository,
    private val cloud: AccountCloud,
    private val local: LocalDevice,
    private val storage: KeyValueStorage,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val _gate = MutableStateFlow<DeviceGate>(DeviceGate.None)
    val gate: StateFlow<DeviceGate> = _gate

    private val _uid = MutableStateFlow<String?>(null)
    val uid: StateFlow<String?> = _uid

    private val mutex = Mutex()

    fun start() {
        SignOutHooks.register { beforeSignOut() }
        scope.launch {
            auth.authState
                .map { if (it.isAuthenticated) it.user?.uid else null }
                .distinctUntilChanged()
                .collect { uid ->
                    _uid.value = uid
                    if (uid != null) check()
                    else if (_gate.value is DeviceGate.LimitReached) _gate.value = DeviceGate.None
                }
        }
    }

    private suspend fun myDevice(createdAt: Long? = null): RegisteredDevice {
        val now = clock()
        return RegisteredDevice(
            id = local.installationId(),
            platform = local.platform,
            name = local.name(),
            appVersion = local.appVersion,
            createdAt = createdAt ?: now,
            lastSeen = now,
        )
    }

    /** Runs the PRD §4.2 decision for the signed-in account. Safe to call any time. */
    suspend fun check() = mutex.withLock {
        val uid = _uid.value ?: return@withLock
        try {
            val myId = local.installationId()
            val devices = cloud.listDevices(uid)
            val wasRegistered = storage.getString(KEY_REGISTERED_UID) == uid
            when (val decision = DeviceLimitPolicy.check(devices, myId, wasRegistered, clock())) {
                is DeviceCheck.Registered -> {
                    if (decision.touch) cloud.touchDevice(uid, myDevice(devices.first { it.id == myId }.createdAt))
                    if (!wasRegistered) storage.putString(KEY_REGISTERED_UID, uid)
                    _gate.value = DeviceGate.None
                }
                DeviceCheck.Register -> register(uid)
                is DeviceCheck.LimitReached -> _gate.value = DeviceGate.LimitReached(decision.devices)
                DeviceCheck.RemovedElsewhere -> {
                    storage.remove(KEY_REGISTERED_UID)
                    auth.signOut()
                    _gate.value = DeviceGate.RemovedElsewhere
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline or Firestore unavailable: try again at the next start.
        }
    }

    private suspend fun register(uid: String) {
        when (cloud.registerDevice(uid, myDevice())) {
            RegisterResult.Registered -> {
                storage.putString(KEY_REGISTERED_UID, uid)
                _gate.value = DeviceGate.None
            }
            // Another device took the last slot meanwhile.
            RegisterResult.LimitReached -> _gate.value = DeviceGate.LimitReached(cloud.listDevices(uid).sortedByDescending { it.lastSeen })
        }
    }

    suspend fun listDevices(): List<RegisteredDevice>? {
        val uid = _uid.value ?: return null
        return try {
            cloud.listDevices(uid).sortedByDescending { it.lastSeen }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** "Đăng xuất từ xa". From the limit dialog, this device then registers in the freed slot. */
    suspend fun signOutRemote(deviceId: String): Boolean {
        val uid = _uid.value ?: return false
        val gate = _gate.value
        if (gate is DeviceGate.LimitReached) _gate.value = gate.copy(busy = true)
        return try {
            cloud.removeDevice(uid, deviceId)
            if (gate is DeviceGate.LimitReached) mutex.withLock { register(uid) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (gate is DeviceGate.LimitReached) _gate.value = gate.copy(busy = false)
            false
        }
    }

    /** "Huỷ đăng nhập" in the limit dialog. */
    suspend fun cancelSignIn() {
        _gate.value = DeviceGate.None
        auth.signOut()
    }

    fun dismissNotice() {
        if (_gate.value == DeviceGate.RemovedElsewhere) _gate.value = DeviceGate.None
    }

    /** Frees this device's slot while the account can still write. */
    private suspend fun beforeSignOut() {
        val uid = _uid.value ?: return
        if (storage.getString(KEY_REGISTERED_UID) != uid) return
        try {
            cloud.removeDevice(uid, local.installationId())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        storage.remove(KEY_REGISTERED_UID)
    }

    suspend fun myDeviceId(): String = local.installationId()

    companion object {
        const val KEY_REGISTERED_UID = "device_registered_uid"
    }
}
