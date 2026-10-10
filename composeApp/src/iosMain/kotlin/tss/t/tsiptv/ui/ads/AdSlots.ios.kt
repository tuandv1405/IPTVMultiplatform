package tss.t.tsiptv.ui.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.tsiptv.core.ads.AdLoadResult
import tss.t.tsiptv.core.ads.AdPlacement

// iOS has no AdMob (PRD §3): every AdMob slot reports a failure, so the Shopee fallback (or
// nothing) takes it. AdsGate never enables AdMob here, so these are not normally reached.

@Composable
actual fun PlatformBannerAd(placement: AdPlacement, modifier: Modifier, onResult: (AdLoadResult?) -> Unit) {
    LaunchedEffect(placement) { onResult(AdLoadResult.FAILED) }
}

@Composable
actual fun rememberBannerHeight(): Dp = 50.dp

@Composable
actual fun PlatformNativeAd(
    placement: AdPlacement,
    slot: Int,
    style: NativeAdStyle,
    adLabel: String,
    modifier: Modifier,
    onResult: (AdLoadResult) -> Unit,
) {
    LaunchedEffect(placement, slot) { onResult(AdLoadResult.FAILED) }
}
