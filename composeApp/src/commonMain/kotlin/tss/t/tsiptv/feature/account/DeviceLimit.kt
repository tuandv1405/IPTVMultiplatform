package tss.t.tsiptv.feature.account

import kotlinx.serialization.Serializable

/** `users/{uid}/devices/{id}` (PRD §4.1). */
@Serializable
data class RegisteredDevice(
    val id: String = "",
    val platform: String = "",
    val name: String = "",
    val appVersion: String = "",
    val createdAt: Long = 0,
    val lastSeen: Long = 0,
)

/** What to do with this device at sign-in or at a start while signed in. */
sealed interface DeviceCheck {
    /** Registered: refresh `lastSeen` when it is older than [DeviceLimitPolicy.TOUCH_INTERVAL_MS]. */
    data class Registered(val touch: Boolean) : DeviceCheck

    /** Not registered and a slot is free: register it. */
    data object Register : DeviceCheck

    /** Not registered and the account already has [devices] (≥ the limit): the user picks one to sign out. */
    data class LimitReached(val devices: List<RegisteredDevice>) : DeviceCheck

    /** It was registered on this account before and someone removed it: sign out here. */
    data object RemovedElsewhere : DeviceCheck
}

/** Pure device-limit decision (PRD §4.2). */
object DeviceLimitPolicy {
    const val MAX_DEVICES = 4
    const val TOUCH_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /**
     * @param registered the account's devices as stored now
     * @param wasRegistered this installation registered itself on this account earlier (local flag)
     */
    fun check(
        registered: List<RegisteredDevice>,
        myId: String,
        wasRegistered: Boolean,
        now: Long,
        max: Int = MAX_DEVICES,
    ): DeviceCheck {
        val mine = registered.firstOrNull { it.id == myId }
        return when {
            mine != null -> DeviceCheck.Registered(touch = now - mine.lastSeen >= TOUCH_INTERVAL_MS)
            wasRegistered -> DeviceCheck.RemovedElsewhere
            registered.size < max -> DeviceCheck.Register
            else -> DeviceCheck.LimitReached(registered.sortedByDescending { it.lastSeen })
        }
    }
}
