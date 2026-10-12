package tss.t.tsiptv.core.ads

/**
 * Desktop: no AdMob (PRD §3). Support promos take the slots and follow the 24 h rule
 * (docs/desktop-build.md). For testing, environment `TSIPTV_DEBUG_ADS_NO_FIRST_DAY=true` skips the
 * 24 h ad-free start; it only makes the promos show sooner.
 */
actual fun platformAdsPlatform(): AdsPlatform = DesktopAdsPlatform

private object DesktopAdsPlatform : AdsPlatform by NoAdMobPlatform {
    override fun debugSkipFirstDay(): Boolean = System.getenv("TSIPTV_DEBUG_ADS_NO_FIRST_DAY") == "true"
}
