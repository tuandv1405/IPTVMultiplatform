package tss.t.tsiptv.core.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.uimode.UiMode
import tss.t.tsiptv.core.uimode.UiModeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** docs/prd-admob.md: the 24 h gate, native slot positions and the app open rules. */
class AdsPolicyTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    // --- R1: first 24 h -------------------------------------------------------------------------

    @Test
    fun firstUseTimePrefersStoredThenInstallTimeThenNow() {
        val now = 100 * day
        assertEquals(5 * day, AdsPolicy.firstUseTime(storedMs = 5 * day, nowMs = now, installTimeMs = 1 * day))
        // Upgrade: Android firstInstallTime long ago counts, so no new ad-free day.
        assertEquals(1 * day, AdsPolicy.firstUseTime(storedMs = null, nowMs = now, installTimeMs = 1 * day))
        // Unknown install time (iOS, desktop): first launch of this version.
        assertEquals(now, AdsPolicy.firstUseTime(storedMs = null, nowMs = now, installTimeMs = null))
        // Bogus install times are ignored.
        assertEquals(now, AdsPolicy.firstUseTime(storedMs = null, nowMs = now, installTimeMs = now + 1))
        assertEquals(now, AdsPolicy.firstUseTime(storedMs = 0, nowMs = now, installTimeMs = 0))
    }

    @Test
    fun noAdsDuringTheFirstDay() {
        val first = 10 * day
        assertTrue(AdsPolicy.isInAdFreePeriod(first, first))
        assertTrue(AdsPolicy.isInAdFreePeriod(first, first + day - 1))
        assertFalse(AdsPolicy.isInAdFreePeriod(first, first + day))
        // A clock set back before the first use still counts as the first day.
        assertTrue(AdsPolicy.isInAdFreePeriod(first, first - hour))
        // Debug override.
        assertFalse(AdsPolicy.isInAdFreePeriod(first, first, debugSkip = true))
        assertEquals(hour, AdsPolicy.remainingAdFreeMs(first, first + 23 * hour))
        assertEquals(0L, AdsPolicy.remainingAdFreeMs(first, first + day))
    }

    // --- R5: native slots ---------------------------------------------------------------------

    @Test
    fun nativeSlotsFollowTheFrequency() {
        assertEquals(emptyList(), AdsPolicy.nativeSlots(0))
        assertEquals(emptyList(), AdsPolicy.nativeSlots(9)) // short list: no ad (never last)
        assertEquals(listOf(9), AdsPolicy.nativeSlots(10))
        assertEquals(listOf(9, 20), AdsPolicy.nativeSlots(25))
        assertEquals(listOf(9, 20), AdsPolicy.nativeSlots(30)) // 2 ads per 25–30 items
        assertEquals(listOf(9, 20, 31), AdsPolicy.nativeSlots(32))
        val long = AdsPolicy.nativeSlots(1000)
        assertEquals(8, long.size) // maximum per list
        assertEquals(listOf(9, 20, 31, 42, 53, 64, 75, 86), long)
    }

    @Test
    fun interleavedListNeverStartsEndsOrDoublesWithAnAd() {
        for (count in 0..200) {
            val rows = AdsPolicy.interleave(List(count) { it }, withAds = true)
            assertEquals(count, rows.count { it is AdsPolicy.Row.Item })
            if (rows.isEmpty()) continue
            assertTrue(rows.first() is AdsPolicy.Row.Item, "index 0 at count=$count")
            assertTrue(rows.last() is AdsPolicy.Row.Item, "last row at count=$count")
            rows.zipWithNext().forEach { (a, b) ->
                assertFalse(a is AdsPolicy.Row.Ad && b is AdsPolicy.Row.Ad, "adjacent ads at count=$count")
            }
            // Items stay in order with their indexes.
            assertEquals((0 until count).toList(), rows.filterIsInstance<AdsPolicy.Row.Item<Int>>().map { it.item })
            // Ad slots are numbered 0, 1, …
            val ads = rows.filterIsInstance<AdsPolicy.Row.Ad>()
            assertEquals(ads.indices.toList(), ads.map { it.slot })
        }
        assertEquals(30, AdsPolicy.interleave(List(30) { it }, withAds = false).size)
    }

    // --- R2: app open -------------------------------------------------------------------------

    @Test
    fun appOpenFreshnessIsUnderFourHours() {
        assertTrue(AdsPolicy.isAppOpenFresh(loadedAtMs = 0, nowMs = 4 * hour - 1))
        assertFalse(AdsPolicy.isAppOpenFresh(loadedAtMs = 0, nowMs = 4 * hour))
        assertFalse(AdsPolicy.isAppOpenFresh(loadedAtMs = 10, nowMs = 5)) // clock went back
    }

    @Test
    fun appOpenOnlyOnColdStartWithinTheTimeoutAndNeverOverPlayback() {
        fun can(
            handled: Boolean = false,
            since: Long = 1_500,
            foreground: Boolean = true,
            playing: Boolean = false,
            allowed: Boolean = true,
            loadedAt: Long? = 1_000,
        ) = AdsPolicy.canShowAppOpen(handled, since, foreground, playing, allowed, loadedAt, nowMs = 2_000)

        assertTrue(can())
        assertFalse(can(handled = true)) // back from background / rotation: already handled
        assertFalse(can(since = AdsPolicy.APP_OPEN_TIMEOUT_MS + 1)) // too late: skipped this launch
        assertFalse(can(foreground = false))
        assertFalse(can(playing = true))
        assertFalse(can(allowed = false)) // first 24 h, consent, TV
        assertFalse(can(loadedAt = null))
    }

    // --- AdsGate --------------------------------------------------------------------------------

    private class FakePlatform(
        consent: Boolean,
        private val install: Long?,
        private val skip: Boolean = false,
        override val isAdMobSupported: Boolean = true,
    ) : AdsPlatform {
        val consentFlow = MutableStateFlow(consent)
        override val canRequestAds: StateFlow<Boolean> = consentFlow
        override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
        override val networkEpoch: StateFlow<Int> = MutableStateFlow(0)
        override fun installTimeMs(): Long? = install
        override fun debugSkipFirstDay(): Boolean = skip
        override fun showPrivacyOptions() = Unit
        var started = 0
        override fun startAdMob() {
            started++
        }
    }

    private fun gateState(
        platform: AdsPlatform,
        storage: InMemoryKeyValueStorage = InMemoryKeyValueStorage(),
        now: Long = 100 * day,
        deviceIsTv: Boolean = false,
        uiMode: UiMode? = null,
        expect: (AdsState) -> Boolean,
    ): AdsState = runBlocking {
        val repo = UiModeRepository(storage)
        uiMode?.let { repo.setUiMode(it) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = AdsGate(storage, platform, repo, deviceIsTv, scope) { now }
            withTimeout(5_000) { gate.state.first(expect) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun gateAllowsAdMobAfterTheFirstDayWithConsent() {
        val platform = FakePlatform(consent = true, install = 1 * day)
        val state = gateState(platform) { it.adMob }
        assertEquals(AdsState(adMob = true, fallback = true), state)
        // The SDK is started before any slot may request an ad.
        assertTrue(platform.started > 0)
    }

    @Test
    fun gateWithoutConsentLeavesOnlyTheFallback() {
        val state = gateState(FakePlatform(consent = false, install = 1 * day)) { it.fallback }
        assertEquals(AdsState(adMob = false, fallback = true), state)
    }

    @Test
    fun gateOnTvShowsNothing() {
        val storage = InMemoryKeyValueStorage()
        // The 24 h clock is read (and stored) even on TV; the state simply stays NONE.
        val state = gateState(FakePlatform(consent = true, install = 1 * day), storage, deviceIsTv = true) {
            runBlocking { storage.getLong(AdsGate.KEY_FIRST_USE_MS, 0L) } > 0
        }
        assertEquals(AdsState.NONE, state)
        val forced = gateState(FakePlatform(consent = true, install = 1 * day), uiMode = UiMode.TV) {
            true
        }
        assertEquals(AdsState.NONE, forced)
    }

    @Test
    fun gateOnAFreshInstallShowsNothingAndStoresTheFirstUse() {
        val storage = InMemoryKeyValueStorage()
        val now = 100 * day
        val fresh = FakePlatform(consent = true, install = now - hour)
        val state = gateState(fresh, storage, now = now) {
            runBlocking { storage.getLong(AdsGate.KEY_FIRST_USE_MS, 0L) } > 0
        }
        assertEquals(AdsState.NONE, state)
        // PRD R1: the ad SDK is not even started during the first 24 h.
        assertEquals(0, fresh.started)
        assertEquals(now - hour, runBlocking { storage.getLong(AdsGate.KEY_FIRST_USE_MS, 0L) })
        // Debug override: ads at once.
        val debug = gateState(FakePlatform(consent = true, install = now - hour, skip = true), now = now) { it.adMob }
        assertTrue(debug.adMob)
    }

    // --- Subscriptions (docs/prd-subscriptions.md §2.2, AC-SUB4, AC-SUB5) ---------------------------

    private fun <T> withGate(
        platform: FakePlatform,
        entitlement: MutableStateFlow<tss.t.tsiptv.core.billing.Entitlement?>,
        block: suspend (AdsGate) -> T,
    ): T = runBlocking {
        val storage = InMemoryKeyValueStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = AdsGate(storage, platform, UiModeRepository(storage), false, scope, entitlement) { 100 * day }
            withTimeout(5_000) { block(gate) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun subscriberFromAColdStartNeverStartsTheSdk() {
        val platform = FakePlatform(consent = true, install = 1 * day)
        val noAds = tss.t.tsiptv.core.billing.Entitlement(plan = tss.t.tsiptv.core.billing.Plan.NO_ADS, source = tss.t.tsiptv.core.billing.EntitlementSource.CACHE)
        withGate(platform, MutableStateFlow(noAds)) { gate ->
            kotlinx.coroutines.delay(300)
            assertEquals(AdsState.NONE, gate.state.value)
        }
        assertEquals(0, platform.started)
    }

    @Test
    fun gateWaitsForTheEntitlementBeforeStartingTheSdk() {
        val platform = FakePlatform(consent = true, install = 1 * day)
        val entitlement = MutableStateFlow<tss.t.tsiptv.core.billing.Entitlement?>(null)
        withGate(platform, entitlement) { gate ->
            kotlinx.coroutines.delay(300)
            assertEquals(AdsState.NONE, gate.state.value)
            assertEquals(0, platform.started)
            entitlement.value = tss.t.tsiptv.core.billing.Entitlement.FREE
            assertEquals(AdsState(adMob = true, fallback = true), gate.state.first { it.adMob })
        }
        assertTrue(platform.started > 0)
    }

    @Test
    fun subscribingMidSessionRemovesEveryAdAndExpiryBringsThemBack() {
        val platform = FakePlatform(consent = false, install = 1 * day)
        val entitlement = MutableStateFlow<tss.t.tsiptv.core.billing.Entitlement?>(tss.t.tsiptv.core.billing.Entitlement.FREE)
        withGate(platform, entitlement) { gate ->
            gate.state.first { it.fallback } // Shopee fallback (no consent)
            val unlimited = tss.t.tsiptv.core.billing.Entitlement(plan = tss.t.tsiptv.core.billing.Plan.UNLIMITED, source = tss.t.tsiptv.core.billing.EntitlementSource.SERVER)
            entitlement.value = unlimited
            gate.state.first { it == AdsState.NONE } // the fallback goes too
            assertFalse(gate.rewardedAllowedNow) // Unlimited: no rewarded tasks
            entitlement.value = tss.t.tsiptv.core.billing.Entitlement.FREE
            gate.state.first { it.fallback }
        }
    }

    @Test
    fun noAdsSubscribersKeepTheOptInRewardedTasks() {
        val noAds = tss.t.tsiptv.core.billing.Entitlement(plan = tss.t.tsiptv.core.billing.Plan.NO_ADS)
        val unlimited = tss.t.tsiptv.core.billing.Entitlement(plan = tss.t.tsiptv.core.billing.Plan.UNLIMITED)
        val free = tss.t.tsiptv.core.billing.Entitlement.FREE
        assertTrue(AdsDecision.rewardedAllowed(true, consent = true, tv = false, adMobSupported = true, entitlement = noAds))
        assertTrue(AdsDecision.rewardedAllowed(true, consent = true, tv = false, adMobSupported = true, entitlement = free))
        assertFalse(AdsDecision.rewardedAllowed(true, consent = true, tv = false, adMobSupported = true, entitlement = unlimited))
        assertFalse(AdsDecision.rewardedAllowed(false, consent = true, tv = false, adMobSupported = true, entitlement = noAds))
        assertFalse(AdsDecision.rewardedAllowed(true, consent = false, tv = false, adMobSupported = true, entitlement = noAds))
        assertFalse(AdsDecision.rewardedAllowed(true, consent = true, tv = true, adMobSupported = true, entitlement = noAds))
        // Banners, native, app open and the fallback: none for any paid plan, nothing while unknown.
        assertEquals(AdsState.NONE, AdsDecision.state(true, true, false, true, noAds))
        assertEquals(AdsState.NONE, AdsDecision.state(true, true, false, true, unlimited))
        assertEquals(AdsState.NONE, AdsDecision.state(true, true, false, true, null))
        assertEquals(AdsState(adMob = true, fallback = true), AdsDecision.state(true, true, false, true, free))
    }

    @Test
    fun desktopAndIosNeverRequestAdMob() {
        val state = gateState(FakePlatform(consent = true, install = null, isAdMobSupported = false), storage = InMemoryKeyValueStorage().also {
            runBlocking { it.putLong(AdsGate.KEY_FIRST_USE_MS, 1 * day) }
        }) { it.fallback }
        assertEquals(AdsState(adMob = false, fallback = true), state)
        assertFalse(NoAdMobPlatform.isAdMobSupported)
    }
}
