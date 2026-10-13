package tss.t.tsiptv.ui.ads

import androidx.compose.runtime.Composable
import tss.t.tsiptv.support.SupportLinks
import tss.t.tsiptv.support.SupportPromo
import tss.t.tsiptv.support.SupportPromoCard

/**
 * Desktop has no ad SDK (docs/desktop-build.md): every slot shows a support promo. Banners ask for
 * support; list slots alternate with the Android app, so a long list does not repeat one ask. Without
 * a configured way to give, only the Android app promo shows.
 */
@Composable
actual fun rememberHouseAd(slot: Int?): (@Composable () -> Unit)? {
    val promo = when {
        !SupportLinks.canDonate -> SupportPromo.ANDROID_APP
        slot == null || slot % 2 == 0 -> SupportPromo.DONATE
        else -> SupportPromo.ANDROID_APP
    }
    return { SupportPromoCard(promo) }
}

actual val houseAdsReplaceAds: Boolean = true

/** "Support TS IPTV"; the Android app promo when no way to give is configured. */
@Composable
actual fun rememberSupportPromo(): (@Composable () -> Unit)? {
    val promo = if (SupportLinks.canDonate) SupportPromo.DONATE else SupportPromo.ANDROID_APP
    return { SupportPromoCard(promo) }
}
