package tss.t.tsiptv.ui.ads

import androidx.compose.runtime.Composable

// AdMob and the Shopee fallback fill the slots here; no house promo.

@Composable
actual fun rememberHouseAd(slot: Int?): (@Composable () -> Unit)? = null

actual val houseAdsReplaceAds: Boolean = false

@Composable
actual fun rememberSupportPromo(): (@Composable () -> Unit)? = null
