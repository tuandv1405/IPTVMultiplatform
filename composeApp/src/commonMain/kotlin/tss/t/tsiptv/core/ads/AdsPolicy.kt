package tss.t.tsiptv.core.ads

/**
 * The ad rules of `docs/prd-admob.md`, as pure functions (unit-tested in `commonTest`).
 * Platform code and the UI ask these; nothing here talks to an ad SDK.
 */
object AdsPolicy {
    /** R1: no ads of any kind during the first 24 hours of use. */
    const val AD_FREE_PERIOD_MS: Long = 24L * 60 * 60 * 1000

    /** R2: the app open ad is shown only if it is ready this long after the first screen appears. */
    const val APP_OPEN_TIMEOUT_MS: Long = 4_000

    /** R2: AdMob app open ads expire after 4 hours. */
    const val APP_OPEN_MAX_AGE_MS: Long = 4L * 60 * 60 * 1000

    /** Native ads kept for reuse are dropped after this long. */
    const val NATIVE_AD_MAX_AGE_MS: Long = 60L * 60 * 1000

    /**
     * R5 frequency: the first ad after [firstAfter] items, then one every [interval] items, at
     * most [maxPerList] per list (about 2 ads per 25–30 items).
     */
    data class NativeFrequency(
        val firstAfter: Int = 9,
        val interval: Int = 11,
        val maxPerList: Int = 8,
    )

    val DEFAULT_NATIVE_FREQUENCY = NativeFrequency()

    /**
     * When the 24 h clock started. A stored value wins. Otherwise (first run of this version) the
     * earlier of now and the platform's install time ([installTimeMs], Android
     * `PackageManager.firstInstallTime`; null where unknown), so users who installed long ago are not
     * held back again. An install time in the future or ≤ 0 is ignored.
     */
    fun firstUseTime(storedMs: Long?, nowMs: Long, installTimeMs: Long?): Long {
        if (storedMs != null && storedMs > 0) return storedMs
        val install = installTimeMs?.takeIf { it in 1..nowMs }
        return install ?: nowMs
    }

    /**
     * True during the first 24 h after [firstUseMs]. A clock set back before the first use counts
     * as inside the period. [debugSkip] (debug builds only) ends it at once.
     */
    fun isInAdFreePeriod(firstUseMs: Long, nowMs: Long, debugSkip: Boolean = false): Boolean {
        if (debugSkip) return false
        return nowMs - firstUseMs < AD_FREE_PERIOD_MS
    }

    /** Milliseconds until the ad-free period ends (0 when it has). */
    fun remainingAdFreeMs(firstUseMs: Long, nowMs: Long, debugSkip: Boolean = false): Long =
        if (!isInAdFreePeriod(firstUseMs, nowMs, debugSkip)) 0L
        else (firstUseMs + AD_FREE_PERIOD_MS - nowMs).coerceIn(1L, AD_FREE_PERIOD_MS)

    /**
     * R5: where native ads go in a list of [itemCount] items. Each value `k` means "an ad between
     * item `k - 1` and item `k`". Never at index 0, never adjacent to another ad, never after the
     * last item, at most [NativeFrequency.maxPerList].
     */
    fun nativeSlots(itemCount: Int, frequency: NativeFrequency = DEFAULT_NATIVE_FREQUENCY): List<Int> {
        if (itemCount <= 0) return emptyList()
        val first = frequency.firstAfter.coerceAtLeast(1)
        val step = frequency.interval.coerceAtLeast(1)
        val out = ArrayList<Int>()
        var k = first
        while (k < itemCount && out.size < frequency.maxPerList) {
            out += k
            k += step
        }
        return out
    }

    /** One row of a list with native ads: a content item or an ad slot. */
    sealed interface Row<out T> {
        data class Item<T>(val index: Int, val item: T) : Row<T>
        /** [slot] counts the ads in this list from 0 (used to pick a fallback offer and as the cache key). */
        data class Ad(val slot: Int, val beforeIndex: Int) : Row<Nothing>
    }

    /** [items] with ads at [nativeSlots] positions; without ads when [withAds] is false. */
    fun <T> interleave(
        items: List<T>,
        withAds: Boolean,
        frequency: NativeFrequency = DEFAULT_NATIVE_FREQUENCY,
    ): List<Row<T>> {
        if (!withAds) return items.mapIndexed { i, item -> Row.Item(i, item) }
        val slots = nativeSlots(items.size, frequency).toSet()
        val out = ArrayList<Row<T>>(items.size + slots.size)
        var slot = 0
        items.forEachIndexed { i, item ->
            if (i in slots) out += Row.Ad(slot++, i)
            out += Row.Item(i, item)
        }
        return out
    }

    /** R2: an app open ad loaded at [loadedAtMs] may still be shown at [nowMs] (< 4 h old). */
    fun isAppOpenFresh(loadedAtMs: Long, nowMs: Long): Boolean {
        val age = nowMs - loadedAtMs
        return age in 0 until APP_OPEN_MAX_AGE_MS
    }

    /**
     * R2: whether the app open ad may be shown now.
     *
     * @param alreadyHandled It was already shown, skipped or timed out in this process (once per
     *   cold start: never again after returning from background or a configuration change)
     * @param sinceProcessStartMs Time since the first screen became visible (Android: first
     *   activity resume of this process)
     * @param appInForeground The splash or first screen is resumed
     * @param isPlaying The player is playing (never cover playback)
     * @param adsAllowed [AdsState.adMob] (24 h gate, consent, not TV)
     * @param timeoutMs [APP_OPEN_TIMEOUT_MS]; debug builds may raise it for slow emulators
     */
    fun canShowAppOpen(
        alreadyHandled: Boolean,
        sinceProcessStartMs: Long,
        appInForeground: Boolean,
        isPlaying: Boolean,
        adsAllowed: Boolean,
        loadedAtMs: Long?,
        nowMs: Long,
        timeoutMs: Long = APP_OPEN_TIMEOUT_MS,
    ): Boolean =
        !alreadyHandled &&
            adsAllowed &&
            appInForeground &&
            !isPlaying &&
            sinceProcessStartMs in 0..timeoutMs &&
            loadedAtMs != null && isAppOpenFresh(loadedAtMs, nowMs)
}
