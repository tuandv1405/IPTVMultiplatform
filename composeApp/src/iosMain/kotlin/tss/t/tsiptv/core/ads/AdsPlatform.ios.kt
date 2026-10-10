package tss.t.tsiptv.core.ads

/** iOS: no AdMob (PRD §3). The Shopee fallback still follows the 24 h rule. */
actual fun platformAdsPlatform(): AdsPlatform = NoAdMobPlatform
