package tss.t.tsiptv.ui.ads

import androidx.compose.runtime.Composable

/**
 * The app's own promo for an ad slot on a platform without an ad SDK (desktop: "support the
 * developer", docs/desktop-build.md), or null to keep the Shopee fallback (Android, iOS). It follows
 * the same rules as any fallback: not in the first 24 h, not on TV, not for an ad-free subscriber.
 *
 * @param slot null for a banner slot; otherwise the native slot's number, so neighbours differ
 */
@Composable
expect fun rememberHouseAd(slot: Int?): (@Composable () -> Unit)?

/** House promos replace ads on this platform, so there is nothing to "remove": no Remove ads link. */
expect val houseAdsReplaceAds: Boolean

/**
 * The "support the developer" card for a slot that shows no ad (desktop, docs/desktop-build.md), or
 * null where there is none (Android, iOS). Unlike [rememberHouseAd] it is not an ad: it may show in
 * the first 24 h.
 */
@Composable
expect fun rememberSupportPromo(): (@Composable () -> Unit)?
