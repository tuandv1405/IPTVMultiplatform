package tss.t.tsiptv.feature.account

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.tsiptv.AppBuildInfo
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.utils.PlatformUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Platform values of `users/{uid}/devices/{id}.platform`. */
object DevicePlatform {
    const val ANDROID_PHONE = "android_phone"
    const val ANDROID_TV = "android_tv"
    const val IOS = "ios"
    const val DESKTOP = "desktop"

    fun current(): String {
        val p = PlatformUtils.platform
        return when {
            p.isTv -> ANDROID_TV
            p.isAndroid -> ANDROID_PHONE
            p.isIOS -> IOS
            else -> DESKTOP
        }
    }
}

/** A human name for this device ("Pixel 8", "Living room TV"). Platform modules override it. */
fun interface DeviceNameProvider {
    fun deviceName(): String

    companion object {
        val Default = DeviceNameProvider {
            when (DevicePlatform.current()) {
                DevicePlatform.ANDROID_TV -> "Android TV"
                DevicePlatform.ANDROID_PHONE -> "Android"
                DevicePlatform.IOS -> "iPhone"
                else -> "Desktop"
            }
        }
    }
}

/**
 * This installation: a random id (UUID v4) kept in local storage and regenerated on reinstall.
 * Never a hardware identifier (PRD §4.1).
 */
class LocalDevice(
    private val storage: KeyValueStorage,
    private val names: DeviceNameProvider = DeviceNameProvider.Default,
    val platform: String = DevicePlatform.current(),
    val appVersion: String = AppBuildInfo.VERSION_NAME,
) {
    private val mutex = Mutex()
    private var cached: String? = null

    @OptIn(ExperimentalUuidApi::class)
    suspend fun installationId(): String = mutex.withLock {
        cached ?: storage.getString(KEY_ID).ifEmpty {
            Uuid.random().toString().also { storage.putString(KEY_ID, it) }
        }.also { cached = it }
    }

    fun name(): String = names.deviceName().take(60).ifBlank { "TS IPTV" }

    companion object {
        const val KEY_ID = "installation_id"
    }
}
