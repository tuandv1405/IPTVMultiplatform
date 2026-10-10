package tss.t.tsiptv.core.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.core.uimode.UiModeRepository

/**
 * Which ads may show right now.
 *
 * @property adMob AdMob may be requested and shown (24 h passed, consent allows, AdMob exists here,
 *   not the TV layout)
 * @property fallback The Shopee affiliate fallback may show (24 h passed, not the TV layout)
 */
data class AdsState(val adMob: Boolean, val fallback: Boolean) {
    val any: Boolean get() = adMob || fallback

    companion object {
        val NONE = AdsState(adMob = false, fallback = false)
    }
}

/**
 * Combines the PRD rules into [state]: the 24 h ad-free period (first-use time kept in
 * [KeyValueStorage]), UMP consent, the platform and the TV layout. Starts as [AdsState.NONE] until
 * the stored first-use time is read, so nothing flashes on a fresh start.
 */
class AdsGate(
    private val storage: KeyValueStorage,
    private val platform: AdsPlatform,
    private val uiModes: UiModeRepository,
    private val deviceIsTv: Boolean,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val firstDayOver = MutableStateFlow<Boolean?>(null)
    private val _state = MutableStateFlow(AdsState.NONE)
    val state: StateFlow<AdsState> = _state.asStateFlow()

    init {
        scope.launch { trackFirstDay() }
        scope.launch {
            combine(firstDayOver, platform.canRequestAds, uiModes.observeUiMode()) { over, consent, mode ->
                val tv = mode.resolveIsTv(deviceIsTv)
                if (over != true || tv) AdsState.NONE
                else AdsState(adMob = platform.isAdMobSupported && consent, fallback = true)
            }.collect {
                // The SDK is started only once AdMob may show (PRD R1: nothing during the first 24 h),
                // before the state lets any slot request an ad.
                if (it.adMob) platform.startAdMob()
                _state.value = it
            }
        }
    }

    /** Reads (or records) the first-use time, then flips at the end of the 24 h period. */
    private suspend fun trackFirstDay() {
        val stored = storage.getLong(KEY_FIRST_USE_MS, 0L).takeIf { it > 0 }
        val firstUse = AdsPolicy.firstUseTime(stored, nowMs(), platform.installTimeMs())
        if (stored == null) storage.putLong(KEY_FIRST_USE_MS, firstUse)
        val skip = platform.debugSkipFirstDay()
        val remaining = AdsPolicy.remainingAdFreeMs(firstUse, nowMs(), skip)
        if (remaining > 0) {
            firstDayOver.value = false
            delay(remaining)
        }
        firstDayOver.value = true
    }

    /** The current decision without waiting (false until the first-use time has been read). */
    val adMobAllowedNow: Boolean get() = _state.value.adMob

    companion object {
        const val KEY_FIRST_USE_MS = "ads_first_use_ms"

        @OptIn(kotlin.time.ExperimentalTime::class)
        fun systemNowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
    }
}
