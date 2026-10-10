package tss.t.tsiptv.feature.account

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** Result of trying to add this device to the account. */
sealed interface RegisterResult {
    data object Registered : RegisterResult
    data object LimitReached : RegisterResult
}

/**
 * The account's documents in Firestore (PRD §4–§6). Every write that the rules tie together (device +
 * `meta/devices`, sync + quota) happens in one transaction here.
 */
interface AccountCloud {
    suspend fun listDevices(uid: String): List<RegisteredDevice>
    suspend fun isDeviceRegistered(uid: String, deviceId: String): Boolean
    suspend fun registerDevice(uid: String, device: RegisteredDevice, max: Int = DeviceLimitPolicy.MAX_DEVICES): RegisterResult
    suspend fun touchDevice(uid: String, device: RegisteredDevice)
    suspend fun removeDevice(uid: String, deviceId: String)

    suspend fun loadQuota(uid: String): QuotaState?

    /** Reads the quota, applies [transform] and writes the result (null = no write), atomically. */
    suspend fun updateQuota(uid: String, transform: (QuotaState?) -> QuotaState?): QuotaState?

    suspend fun loadSync(uid: String): SyncDocument?

    /**
     * Stores [doc] in the sync slot and the quota from [transform] in one transaction.
     * @return the new quota, or null when [transform] refused (no sync left): nothing was written.
     */
    suspend fun pushSync(uid: String, doc: SyncDocument, transform: (QuotaState?) -> QuotaState?): QuotaState?
}

@Serializable
internal data class DevicesMeta(val ids: List<String> = emptyList(), val updatedAt: Long = 0)

/** Firestore implementation (gitlive SDK, all platforms that have Firebase). */
class FirestoreAccountCloud(private val firestore: FirebaseFirestore) : AccountCloud {

    private fun user(uid: String) = firestore.collection("users").document(uid)
    private fun devices(uid: String) = user(uid).collection("devices")
    private fun meta(uid: String) = user(uid).collection("meta").document("devices")
    private fun quota(uid: String) = user(uid).collection("quota").document("daily")
    private fun sync(uid: String) = user(uid).collection("sync").document("current")

    override suspend fun listDevices(uid: String): List<RegisteredDevice> =
        devices(uid).get().documents.map { it.data(RegisteredDevice.serializer()).copy(id = it.id) }

    override suspend fun isDeviceRegistered(uid: String, deviceId: String): Boolean =
        devices(uid).document(deviceId).get().exists

    override suspend fun registerDevice(uid: String, device: RegisteredDevice, max: Int): RegisterResult =
        firestore.runTransaction {
            val metaSnap = get(meta(uid))
            val ids = if (metaSnap.exists) metaSnap.data(DevicesMeta.serializer()).ids else emptyList()
            when {
                device.id in ids -> {
                    set(devices(uid).document(device.id), RegisteredDevice.serializer(), device)
                    RegisterResult.Registered
                }
                ids.size >= max -> RegisterResult.LimitReached
                else -> {
                    set(devices(uid).document(device.id), RegisteredDevice.serializer(), device)
                    set(meta(uid), DevicesMeta.serializer(), DevicesMeta(ids + device.id, device.lastSeen))
                    RegisterResult.Registered
                }
            }
        }

    override suspend fun touchDevice(uid: String, device: RegisteredDevice) {
        devices(uid).document(device.id).update(
            "name" to device.name,
            "appVersion" to device.appVersion,
            "lastSeen" to device.lastSeen,
        )
    }

    override suspend fun removeDevice(uid: String, deviceId: String) {
        firestore.runTransaction {
            val metaSnap = get(meta(uid))
            if (metaSnap.exists) {
                val m = metaSnap.data(DevicesMeta.serializer())
                if (deviceId in m.ids) set(meta(uid), DevicesMeta.serializer(), DevicesMeta(m.ids - deviceId, m.updatedAt))
            }
            delete(devices(uid).document(deviceId))
        }
    }

    override suspend fun loadQuota(uid: String): QuotaState? =
        quota(uid).get().let { if (it.exists) it.data(QuotaState.serializer()) else null }

    override suspend fun updateQuota(uid: String, transform: (QuotaState?) -> QuotaState?): QuotaState? =
        firestore.runTransaction {
            val snap = get(quota(uid))
            val current = if (snap.exists) snap.data(QuotaState.serializer()) else null
            val next = transform(current)
            if (next != null) set(quota(uid), QuotaState.serializer(), next)
            next
        }

    override suspend fun loadSync(uid: String): SyncDocument? =
        sync(uid).get().let { if (it.exists) it.data(SyncDocument.serializer()) else null }

    override suspend fun pushSync(uid: String, doc: SyncDocument, transform: (QuotaState?) -> QuotaState?): QuotaState? =
        firestore.runTransaction {
            val snap = get(quota(uid))
            val current = if (snap.exists) snap.data(QuotaState.serializer()) else null
            val next = transform(current) ?: return@runTransaction null
            set(quota(uid), QuotaState.serializer(), next)
            set(sync(uid), SyncDocument.serializer(), doc)
            next
        }
}

/** In-memory [AccountCloud] for tests and for builds without Firebase. Same semantics, no rules. */
class InMemoryAccountCloud : AccountCloud {
    private val mutex = Mutex()
    val devices = mutableMapOf<String, MutableMap<String, RegisteredDevice>>()
    val quotas = mutableMapOf<String, QuotaState>()
    val syncs = mutableMapOf<String, SyncDocument>()

    override suspend fun listDevices(uid: String) = mutex.withLock { devices[uid]?.values?.toList().orEmpty() }
    override suspend fun isDeviceRegistered(uid: String, deviceId: String) = mutex.withLock { devices[uid]?.containsKey(deviceId) == true }

    override suspend fun registerDevice(uid: String, device: RegisteredDevice, max: Int) = mutex.withLock {
        val map = devices.getOrPut(uid) { mutableMapOf() }
        if (device.id !in map && map.size >= max) return@withLock RegisterResult.LimitReached
        map[device.id] = device
        RegisterResult.Registered
    }

    override suspend fun touchDevice(uid: String, device: RegisteredDevice) = mutex.withLock {
        devices[uid]?.get(device.id)?.let { devices[uid]!![device.id] = it.copy(name = device.name, appVersion = device.appVersion, lastSeen = device.lastSeen) }
        Unit
    }

    override suspend fun removeDevice(uid: String, deviceId: String) = mutex.withLock { devices[uid]?.remove(deviceId); Unit }

    override suspend fun loadQuota(uid: String) = mutex.withLock { quotas[uid] }

    override suspend fun updateQuota(uid: String, transform: (QuotaState?) -> QuotaState?) = mutex.withLock {
        transform(quotas[uid])?.also { quotas[uid] = it }
    }

    override suspend fun loadSync(uid: String) = mutex.withLock { syncs[uid] }

    override suspend fun pushSync(uid: String, doc: SyncDocument, transform: (QuotaState?) -> QuotaState?) = mutex.withLock {
        val next = transform(quotas[uid]) ?: return@withLock null
        quotas[uid] = next
        syncs[uid] = doc
        next
    }
}
