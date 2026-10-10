package tss.t.tsiptv.core.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.billing.Entitlement
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.core.uimode.UiModeRepository

/**
 * Which ads may show right now.
 *
 * @property adMob AdMob may be requested and shown (24 h passed, consent allows, AdMob exists here,
 *   not the TV layout, no ad-free subscription)
 * @property fallback The Shopee affiliate fallback may show (24 h passed, not the TV layout, no
 *   ad-free subscription)
 */
data class AdsState(val adMob: Boolean, val fallback: Boolean) {
    val any: Boolean get() = adMob || fallback

    companion object {
        val NONE = AdsState(adMob = false, fallback = false)
    }
}

/**
 * The pure decision of [AdsGate] (docs/prd-admob.md, docs/prd-subscriptions.md §2.2).
 *
 * @param entitlement null while the subscription is not known yet (the cache is read in a few ms):
 *   nothing shows and the SDK is not started, so a subscriber never sees an ad flash
 */
object AdsDecision {
    fun state(firstDayOver: Boolean?, consent: Boolean, tv: Boolean, adMobSupported: Boolean, entitlement: Entitlement?): AdsState =
        if (firstDayOver != true || tv || entitlement == null || entitlement.noAds) AdsState.NONE
        else AdsState(adMob = adMobSupported && consent, fallback = true)

    /**
     * Rewarded ads the user asks for (extra sends / syncs) follow the same 24 h, consent and TV rules,
     * but not the "No ads" plan: those subscribers keep the opt-in tasks. Unlimited hides the tasks.
     */
    fun rewardedAllowed(firstDayOver: Boolean?, consent: Boolean, tv: Boolean, adMobSupported: Boolean, entitlement: Entitlement?): Boolean =
        firstDayOver == true && !tv && adMobSupported && consent && entitlement?.unlimited != true
}

/**
 * Combines the PRD rules into [state]: the 24 h ad-free period (first-use time kept in
 * [KeyValueStorage]), UMP consent, the platform, the TV layout and the subscription. Starts as
 * [AdsState.NONE] until the stored first-use time and the entitlement are read, so nothing flashes on
 * a fresh start and `MobileAds.initialize` never runs for a subscriber.
 */
class AdsGate(
    private val storage: KeyValueStorage,
    private val platform: AdsPlatform,
    private val uiModes: UiModeRepository,
    private val deviceIsTv: Boolean,
    private val scope: CoroutineScope,
    /** The subscription; null until known. Free everywhere by default (tests, no billing). */
    private val entitlement: StateFlow<Entitlement?> = MutableStateFlow(Entitlement.FREE),
    private val nowMs: () -> Long,
) {
    private val firstDayOver = MutableStateFlow<Boolean?>(null)
    private val _state = MutableStateFlow(AdsState.NONE)
    val state: StateFlow<AdsState> = _state.asStateFlow()

    init {
        scope.launch { trackFirstDay() }
        scope.launch {
            combine(firstDayOver, platform.canRequestAds, uiModes.observeUiMode(), entitlement) { over, consent, mode, ent ->
                val tv = mode.resolveIsTv(deviceIsTv)
                tvLayout = tv
                rewardedAllowedNow = AdsDecision.rewardedAllowed(over, consent, tv, platform.isAdMobSupported, ent)
                AdsDecision.state(over, consent, tv, platform.isAdMobSupported, ent)
            }.collect {
                // The SDK is started only once AdMob may show (PRD R1: nothing during the first 24 h;
                // never for a subscriber), before the state lets any slot request an ad.
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

    /** The TV layout is on (no ads there). Read by the rewarded-ad tasks to explain why they are off. */
    @kotlin.concurrent.Volatile
    var tvLayout: Boolean = deviceIsTv
        private set

    /** A rewarded ad the user asks for may be shown now ([AdsDecision.rewardedAllowed]). */
    @kotlin.concurrent.Volatile
    var rewardedAllowedNow: Boolean = false
        private set

    /** The 24 h ad-free start is over. */
    val adFreePeriodOver: Boolean get() = firstDayOver.value == true

    /** The current decision without waiting (false until the first-use time has been read). */
    val adMobAllowedNow: Boolean get() = _state.value.adMob

    companion object {
        const val KEY_FIRST_USE_MS = "ads_first_use_ms"

        @OptIn(kotlin.time.ExperimentalTime::class)
        fun systemNowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
    }
}
