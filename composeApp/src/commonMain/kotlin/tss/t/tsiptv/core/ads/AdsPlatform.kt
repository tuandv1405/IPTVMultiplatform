package tss.t.tsiptv.core.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where an ad is shown (PRD §4). */
enum class AdPlacement {
    APP_OPEN,
    HOME_BANNER,
    HOME_NATIVE,
    PLAYER_BANNER,
    PLAYER_NATIVE,
    /** The player's schedule/details overlay (its own slots: one native ad is never bound twice). */
    PLAYER_DETAILS_NATIVE,
    PROGRAMS_NATIVE,
}

/** Result of one platform ad load, reported to the common slot (which then shows the fallback). */
enum class AdLoadResult { LOADED, FAILED }

/**
 * What the platform's ad SDK can do. Android: AdMob + UMP (`AndroidAdsPlatform`). Desktop and iOS:
 * [NoAdMobPlatform] (iOS AdMob is follow-up work, PRD §9).
 */
interface AdsPlatform {
    /** The AdMob SDK exists on this platform (Android phone/tablet build). */
    val isAdMobSupported: Boolean

    /**
     * UMP `canRequestAds()`: consent (or no consent needed) allows ad requests. Independent of
     * whether the SDK has been started (see [startAdMob]).
     */
    val canRequestAds: StateFlow<Boolean>

    /** UMP privacy options are required: Profile shows "Privacy options". */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Increments each time the network comes back: failed ad slots retry then. */
    val networkEpoch: StateFlow<Int>

    /** When the app was first installed (Android `firstInstallTime`), or null where unknown. */
    fun installTimeMs(): Long?

    /** Debug builds only: `-Ptsiptv.debugAdsNoFirstDay=true` skips the 24 h ad-free period. */
    fun debugSkipFirstDay(): Boolean

    /** Opens the UMP privacy options form (no-op where not required or unsupported). */
    fun showPrivacyOptions()

    /**
     * Starts the ad SDK (Android: `MobileAds.initialize`, then the app open ad). [AdsGate] calls it
     * only when AdMob may show: after the 24 h period, with consent, not on TV. Idempotent.
     */
    fun startAdMob()

    /**
     * Starts the SDK for a rewarded ad the user asked for, **without** the app open ad
     * (docs/prd-subscriptions.md §2.2: a "No ads" subscriber may still watch rewarded ads for extra
     * sends / syncs, but their SDK was never started at launch). Idempotent.
     */
    fun startAdMobForRewarded() = startAdMob()
}

/** Desktop and iOS: no AdMob; the Shopee fallback still follows the 24 h rule. */
object NoAdMobPlatform : AdsPlatform {
    override val isAdMobSupported: Boolean = false
    private val no = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = no.asStateFlow()
    override val privacyOptionsRequired: StateFlow<Boolean> = no.asStateFlow()
    override val networkEpoch: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
    override fun installTimeMs(): Long? = null
    override fun debugSkipFirstDay(): Boolean = false
    override fun showPrivacyOptions() = Unit
    override fun startAdMob() = Unit
}

/** The platform's [AdsPlatform] (Android: AdMob + UMP; desktop and iOS: [NoAdMobPlatform]). */
expect fun platformAdsPlatform(): AdsPlatform
