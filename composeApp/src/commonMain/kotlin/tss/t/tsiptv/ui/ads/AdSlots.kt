package tss.t.tsiptv.ui.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.ad_label
import tsiptv.composeapp.generated.resources.remove_ads_link
import tss.t.tsiptv.ui.screens.plans.LocalOpenPlans
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import tss.t.tsiptv.core.ads.AdLoadResult
import tss.t.tsiptv.core.ads.AdPlacement
import tss.t.tsiptv.core.ads.AdsGate
import tss.t.tsiptv.core.ads.AdsPlatform
import tss.t.tsiptv.core.ads.AdsState
import tss.t.tsiptv.ui.themes.TSColors

/**
 * How a native ad row looks, so it matches the list it sits in (PRD R5). The ad still carries the
 * "Ad" badge and AdChoices.
 */
data class NativeAdStyle(
    val background: Color = TSColors.SecondaryBackgroundColor,
    val title: Color = TSColors.TextPrimary,
    val body: Color = TSColors.TextSecondaryLight,
    val ctaBackground: Color = TSColors.AccentCyan,
    val ctaText: Color = Color.Black,
    val badgeBackground: Color = Color(0xFFFFC107),
    val badgeText: Color = Color.Black,
    val cornerRadius: Dp = 12.dp,
    val iconSize: Dp = 48.dp,
    val iconCorner: Dp = 8.dp,
    val horizontalPadding: Dp = 16.dp,
    val verticalPadding: Dp = 12.dp,
)

/** Current [AdsState] (24 h rule, consent, platform, TV layout). */
@Composable
fun rememberAdsState(): AdsState {
    val gate = koinInject<AdsGate>()
    val state by gate.state.collectAsState()
    return state
}

/**
 * A banner slot (PRD R3/R4/R6): AdMob banner, a same-height skeleton with the "Ad" label while it
 * loads, then [fallback] (the Shopee item) if AdMob fails, else nothing. Never both. A failed slot
 * retries when the screen resumes or the network comes back ([AdRetryEffect]).
 */
@Composable
fun BannerAdSlot(
    placement: AdPlacement,
    modifier: Modifier = Modifier,
    fallback: (@Composable () -> Unit)? = null,
    /** A small "Remove ads" link below the slot while it shows something (docs/prd-subscriptions.md §3.2). */
    removeAdsLink: Boolean = false,
) {
    val ads = rememberAdsState()
    if (!ads.any) return
    if (!ads.adMob) {
        if (fallback != null) {
            fallback()
            if (removeAdsLink) RemoveAdsLink()
        }
        return
    }
    var result by remember(placement) { mutableStateOf<AdLoadResult?>(null) }
    var attempt by remember(placement) { mutableStateOf(0) }
    AdRetryEffect(failed = result == AdLoadResult.FAILED) {
        result = null
        attempt++
    }
    if (result == AdLoadResult.FAILED) {
        if (fallback != null) {
            fallback()
            if (removeAdsLink) RemoveAdsLink()
        }
        return
    }
    val height = rememberBannerHeight()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height)) {
            key(attempt) {
                PlatformBannerAd(placement, Modifier.fillMaxWidth().height(height)) { result = it }
            }
            if (result == null) AdSkeleton(Modifier.fillMaxWidth().height(height))
        }
        if (removeAdsLink && result == AdLoadResult.LOADED) RemoveAdsLink()
    }
}

/**
 * "Remove ads": a small text link in the app's own style, right-aligned **below** an ad slot, never
 * over the ad or inside the ad view (AdMob policy), opening the plans screen.
 */
@Composable
fun RemoveAdsLink(modifier: Modifier = Modifier) {
    val open = LocalOpenPlans.current ?: return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = stringResource(Res.string.remove_ads_link),
            color = TSColors.AccentCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = open)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * A native ad slot in a list (PRD R5/R6): the AdMob native row styled like the list, a skeleton row
 * while it loads, then [fallback] if AdMob fails, else nothing. Never both. Retries like
 * [BannerAdSlot].
 *
 * @param slot The ad's number in this list (0, 1, …): cache key and fallback offer pick
 * @param divider Drawn under the slot only when the slot shows something (no double divider when
 *   it collapses)
 */
@Composable
fun NativeAdSlot(
    placement: AdPlacement,
    slot: Int,
    modifier: Modifier = Modifier,
    style: NativeAdStyle = NativeAdStyle(),
    fallback: (@Composable () -> Unit)? = null,
    divider: (@Composable () -> Unit)? = null,
) {
    val ads = rememberAdsState()
    if (!ads.any) return
    if (!ads.adMob) {
        if (fallback != null) {
            fallback()
            divider?.invoke()
        }
        return
    }
    var result by remember(placement, slot) { mutableStateOf<AdLoadResult?>(null) }
    var attempt by remember(placement, slot) { mutableStateOf(0) }
    AdRetryEffect(failed = result == AdLoadResult.FAILED) {
        result = null
        attempt++
    }
    if (result == AdLoadResult.FAILED) {
        if (fallback != null) {
            fallback()
            divider?.invoke()
        }
        return
    }
    val label = stringResource(Res.string.ad_label)
    Column(modifier.fillMaxWidth()) {
        if (result == null) {
            AdSkeleton(
                Modifier.fillMaxWidth().height(style.iconSize + style.verticalPadding * 2),
                corner = style.cornerRadius,
                color = style.background,
            )
        }
        key(attempt) {
            PlatformNativeAd(placement, slot, style, label, Modifier.fillMaxWidth()) { result = it }
        }
        divider?.invoke()
    }
}

/** A failed slot tries again at most this often when the screen resumes. */
private const val AD_RETRY_MIN_INTERVAL_MS = 60_000L

/**
 * While [failed]: calls [onRetry] when the network comes back, or when the screen resumes at least
 * [AD_RETRY_MIN_INTERVAL_MS] after the failure (no request storm on no-fill).
 */
@Composable
private fun AdRetryEffect(failed: Boolean, onRetry: () -> Unit) {
    if (!failed) return
    val platform = koinInject<AdsPlatform>()
    val retry by rememberUpdatedState(onRetry)
    val failedAt = remember { AdsGate.systemNowMs() }
    LaunchedEffect(platform) {
        platform.networkEpoch.drop(1).first()
        retry()
    }
    LifecycleResumeEffect(Unit) {
        if (AdsGate.systemNowMs() - failedAt >= AD_RETRY_MIN_INTERVAL_MS) retry()
        onPauseOrDispose { }
    }
}

/** Placeholder of a loading ad: the slot's size, with the "Ad" label top-left (PRD R3). */
@Composable
fun AdSkeleton(
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    color: Color = TSColors.SecondaryBackgroundColor,
) {
    Box(modifier.background(color.copy(alpha = 0.6f), RoundedCornerShape(corner))) {
        AdBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

/** The "Ad" / "Quảng cáo" attribution badge. */
@Composable
fun AdBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.ad_label),
        modifier = modifier
            .background(Color(0xFFFFC107), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.Black,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/**
 * The platform banner (Android: AdMob anchored adaptive banner). Reports load success or failure,
 * and null when it starts loading again (for example a new banner after a rotation).
 */
@Composable
expect fun PlatformBannerAd(placement: AdPlacement, modifier: Modifier, onResult: (AdLoadResult?) -> Unit)

/** Height of the banner for the current screen width (Android: anchored adaptive size). */
@Composable
expect fun rememberBannerHeight(): Dp

/**
 * The platform native ad row (Android: `NativeAdView` with the assets registered). Renders nothing
 * until an ad is ready; reports success or failure.
 */
@Composable
expect fun PlatformNativeAd(
    placement: AdPlacement,
    slot: Int,
    style: NativeAdStyle,
    adLabel: String,
    modifier: Modifier,
    onResult: (AdLoadResult) -> Unit,
)
