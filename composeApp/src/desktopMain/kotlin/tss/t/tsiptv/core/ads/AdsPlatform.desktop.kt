package tss.t.tsiptv.core.ads

/** Desktop: no AdMob (PRD §3). The Shopee fallback still follows the 24 h rule. */
actual fun platformAdsPlatform(): AdsPlatform = NoAdMobPlatform
