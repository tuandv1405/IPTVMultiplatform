package tss.t.tsiptv.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.Network
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tss.t.tsiptv.R
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android ads: Google UMP consent and the Mobile Ads SDK (PRD §5). The SDK is initialised only when
 * [AdsGate] says AdMob may show ([startAdMob]: 24 h passed, UMP `canRequestAds()`, not TV). Nothing
 * here logs URLs or ad content.
 */
object AndroidAdsPlatform : AdsPlatform {
    @Volatile
    private lateinit var app: Application
    private var consent: ConsentInformation? = null
    private var activityRef: WeakReference<Activity>? = null
    private val sdkStarted = AtomicBoolean(false)
    private val gatheringConsent = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val _networkEpoch = MutableStateFlow(0)
    override val networkEpoch: StateFlow<Int> = _networkEpoch.asStateFlow()

    override val isAdMobSupported: Boolean = true

    val isDebuggable: Boolean
        get() = ::app.isInitialized && (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** `Application.onCreate`: reads consent kept from the previous session (no network, no UI). */
    fun onApplicationCreate(application: Application) {
        app = application
        val info = UserMessagingPlatform.getConsentInformation(application)
        consent = info
        if (isDebuggable && app.resources.getBoolean(R.bool.debug_ump_reset)) {
            // QA (debug only): forget stored consent so the form shows again.
            info.reset()
        }
        AdsLog.i { "Stored consent: canRequestAds=${info.canRequestAds()} at +${AppOpenAdController.sinceProcessStart()} ms" }
        watchNetwork(application)
        // Consent from an earlier session: AdsGate starts the SDK at once if the 24 h period is
        // over, so the app open ad can load during start-up (PRD R2).
        refreshConsentState()
        if (!info.canRequestAds()) pollStoredConsent(info)
    }

    /**
     * UMP reads its stored state on a background thread right after `getConsentInformation`, so
     * `canRequestAds()` can be false for the first moments of a cold start even when consent from
     * the previous session allows ads. Re-check briefly so the app open ad does not wait for the
     * network consent update (PRD R2). The update in [gatherConsent] stays the source of truth.
     */
    private fun pollStoredConsent(info: ConsentInformation) {
        // Off the main thread, which is busy with the first frames at this point.
        Thread {
            val startedAt = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - startedAt < STORED_CONSENT_POLL_MS) {
                if (info.canRequestAds()) {
                    AdsLog.i { "Stored consent ready at +${AppOpenAdController.sinceProcessStart()} ms" }
                    refreshConsentState()
                    return@Thread
                }
                SystemClock.sleep(25)
            }
        }.apply { name = "ads-consent" }.start()
    }

    private const val STORED_CONSENT_POLL_MS = 3_000L

    /**
     * `MainActivity.onCreate`: asks UMP for the current consent requirements and shows the
     * consent form where required (EEA, UK, Switzerland). Then starts the SDK if allowed.
     */
    fun gatherConsent(activity: Activity) {
        activityRef = WeakReference(activity)
        val info = consent ?: UserMessagingPlatform.getConsentInformation(activity).also { consent = it }
        if (!gatheringConsent.compareAndSet(false, true)) return
        val params = ConsentRequestParameters.Builder()
            .apply { debugConsentSettings(activity)?.let { setConsentDebugSettings(it) } }
            .build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) AdsLog.w { "Consent form: ${error.errorCode}" }
                    gatheringConsent.set(false)
                    refreshConsentState()
                }
            },
            { error ->
                // Offline or misconfigured: keep whatever consent the device already has.
                AdsLog.w { "Consent update failed: ${error.errorCode}" }
                gatheringConsent.set(false)
                refreshConsentState()
            },
        )
    }

    /** Counts network returns ([networkEpoch]) so slots that failed offline retry. */
    private fun watchNetwork(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        // Started offline: no onLost comes, so the first onAvailable must count as a return.
        var hadNetwork = cm.activeNetwork != null
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!hadNetwork) _networkEpoch.value += 1
                    hadNetwork = true
                }

                override fun onLost(network: Network) {
                    hadNetwork = false
                }
            })
        }.onFailure { AdsLog.w { "Network callback not registered" } }
    }

    fun onActivityResumed(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun currentActivity(): Activity? = activityRef?.get()

    private fun refreshConsentState() {
        val info = consent ?: return
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        val can = info.canRequestAds()
        if (_canRequestAds.value != can) AdsLog.i { "Consent: canRequestAds=$can" }
        _canRequestAds.value = can
    }

    /**
     * Debug builds only: UMP debug geography (`-Ptsiptv.debugUmpGeography=EEA`) and the test device
     * hash (`-Ptsiptv.debugUmpTestDevice=…`, UMP logs it on first use). Null in release.
     */
    private fun debugConsentSettings(context: Context): ConsentDebugSettings? {
        if (!isDebuggable) return null
        val geography = when (context.getString(R.string.debug_ump_geography).trim().uppercase()) {
            "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            "REGULATED_US_STATE", "US" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
            "OTHER", "NOT_EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
            else -> return null
        }
        val builder = ConsentDebugSettings.Builder(context).setDebugGeography(geography)
        context.getString(R.string.debug_ump_test_device).trim().takeIf { it.isNotEmpty() }
            ?.let { builder.addTestDeviceHashedId(it) }
        AdsLog.i { "UMP debug geography: $geography" }
        return builder.build()
    }

    /** [AdsGate]: AdMob may show now. Starts the SDK once (only with consent). */
    override fun startAdMob() {
        if (!::app.isInitialized || consent?.canRequestAds() != true) return
        startSdk()
    }

    /** `MobileAds.initialize` once per process, on a background thread (Google's recommendation). */
    private fun startSdk() {
        if (!sdkStarted.compareAndSet(false, true)) return
        AdsLog.i { "SDK start at +${AppOpenAdController.sinceProcessStart()} ms" }
        Thread {
            MobileAds.initialize(app) {}
        }.apply { name = "ads-init" }.start()
        // Ads may be requested right away; the SDK queues them until it is ready.
        AppOpenAdController.onSdkReady(app)
    }

    override fun installTimeMs(): Long? = try {
        app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime.takeIf { it > 0 }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: UninitializedPropertyAccessException) {
        null
    }

    override fun debugSkipFirstDay(): Boolean =
        ::app.isInitialized && isDebuggable && app.resources.getBoolean(R.bool.debug_ads_skip_first_day)

    override fun showPrivacyOptions() {
        val activity = currentActivity() ?: return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) AdsLog.w { "Privacy options form: ${error.errorCode}" }
            refreshConsentState()
        }
    }

    /** Ad unit IDs: Google test IDs in debug; real IDs injected at build time in release. */
    fun unitId(context: Context, placement: AdPlacement): String = context.getString(
        when (placement) {
            AdPlacement.APP_OPEN -> R.string.admob_app_open_unit
            AdPlacement.HOME_BANNER, AdPlacement.PLAYER_BANNER -> R.string.admob_banner_unit
            AdPlacement.HOME_NATIVE, AdPlacement.PLAYER_NATIVE, AdPlacement.PLAYER_DETAILS_NATIVE,
            AdPlacement.PROGRAMS_NATIVE -> R.string.admob_native_unit
        }
    )
}

actual fun platformAdsPlatform(): AdsPlatform = AndroidAdsPlatform
