package tss.t.tsiptv.ui.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import tss.t.tsiptv.core.ads.AdLoadResult
import tss.t.tsiptv.core.ads.AdPlacement
import tss.t.tsiptv.core.ads.AdsLog
import tss.t.tsiptv.core.ads.AndroidAdsPlatform
import tss.t.tsiptv.core.ads.NativeAdCache

/** Anchored adaptive banner size for the screen width (Google's recommended banner). */
private fun bannerSize(context: Context, widthDp: Int): AdSize =
    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp.coerceAtLeast(320))

@Composable
actual fun rememberBannerHeight(): Dp {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { bannerSize(context, widthDp).height.dp }
}

/**
 * AdMob anchored adaptive banner (PRD R3/R4). One `AdView` per screen width: a rotation or a new
 * window size creates a new `AdView` in a new `AndroidView` (the old one is detached and destroyed).
 * The request is made once the view is committed to composition (no request for a banner that is
 * never shown). Paused and resumed with the screen, destroyed when the slot leaves (no leak).
 */
@Composable
actual fun PlatformBannerAd(placement: AdPlacement, modifier: Modifier, onResult: (AdLoadResult?) -> Unit) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val report by rememberUpdatedState(onResult)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    key(placement, widthDp) {
        val adView = remember {
            AdView(context).apply {
                setAdSize(bannerSize(context, widthDp))
                adUnitId = AndroidAdsPlatform.unitId(context, placement)
            }
        }
        DisposableEffect(adView, lifecycle) {
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() = report(AdLoadResult.LOADED)
                override fun onAdFailedToLoad(error: LoadAdError) = report(AdLoadResult.FAILED)
            }
            report(null)
            // Request only once the banner is really on screen: attached, then one frame later.
            // A banner composed for a single frame (e.g. a rotation that switches the player to
            // fullscreen) is disposed before that and never requests an ad.
            var disposed = false
            val request = Runnable {
                if (!disposed) {
                    AdsLog.d { "Banner ${placement.name}: new AdView (width $widthDp dp), request" }
                    adView.loadAd(AdRequest.Builder().build())
                }
            }
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.post(request)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            if (adView.isAttachedToWindow) adView.post(request) else adView.addOnAttachStateChangeListener(attachListener)
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                disposed = true
                adView.removeOnAttachStateChangeListener(attachListener)
                adView.removeCallbacks(request)
                lifecycle.removeObserver(observer)
                adView.adListener = object : AdListener() {}
                (adView.parent as? ViewGroup)?.removeView(adView)
                adView.destroy()
            }
        }
        AndroidView(factory = { adView }, modifier = modifier)
    }
}

/**
 * AdMob native ad as a list row (PRD R5): icon, "Ad" badge + headline, body, call to action, and
 * the AdChoices icon (top-right). Assets are registered on the `NativeAdView`, which alone handles
 * clicks. Loaded lazily through [NativeAdCache] when the slot is composed; the `NativeAdView` is
 * destroyed when the row leaves composition, the ad itself when the cache lets it go.
 */
@Composable
actual fun PlatformNativeAd(
    placement: AdPlacement,
    slot: Int,
    style: NativeAdStyle,
    adLabel: String,
    modifier: Modifier,
    onResult: (AdLoadResult) -> Unit,
) {
    val context = LocalContext.current
    val key = remember(placement, slot) { NativeAdCache.key(placement, slot) }
    var nativeAd by remember(key) { mutableStateOf<NativeAd?>(null) }
    val report by rememberUpdatedState(onResult)
    DisposableEffect(key) {
        var acquired: NativeAd? = null
        var active = true
        val callback: (NativeAd?) -> Unit = { ad ->
            if (!active) {
                ad?.let { NativeAdCache.release(it) }
            } else if (ad == null) {
                report(AdLoadResult.FAILED)
            } else {
                acquired = ad
                nativeAd = ad
                report(AdLoadResult.LOADED)
            }
        }
        NativeAdCache.acquire(context, placement, key, callback)
        onDispose {
            active = false
            val ad = acquired
            if (ad != null) NativeAdCache.release(ad) else NativeAdCache.cancel(key, callback)
        }
    }
    val ad = nativeAd ?: return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx -> NativeRowViews(ctx, style, adLabel) },
        update = { views -> views.bind(ad) },
        onRelease = { views -> views.destroy() },
    )
}

/** The views of one native ad row, built in code (no XML) and styled from [NativeAdStyle]. */
private class NativeRowViews(context: Context, style: NativeAdStyle, adLabel: String) : LinearLayout(context) {
    val adView = NativeAdView(context)
    private val icon = ImageView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val cta = TextView(context)
    private var bound: NativeAd? = null

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(style.background.toArgb())
                cornerRadius = dp(style.cornerRadius.value).toFloat()
            }
            setPadding(
                dp(style.horizontalPadding.value), dp(style.verticalPadding.value),
                dp(style.horizontalPadding.value), dp(style.verticalPadding.value),
            )
        }

        icon.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(style.iconCorner.value).toFloat() }
        }
        val iconSize = dp(style.iconSize.value)
        row.addView(icon, LayoutParams(iconSize, iconSize).apply { marginEnd = dp(16f) })

        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // "Ad" / "Quảng cáo" attribution badge (AdMob policy), before the headline.
        val badge = TextView(context).apply {
            text = adLabel
            setTextColor(style.badgeText.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(style.badgeBackground.toArgb())
                cornerRadius = dp(4f).toFloat()
            }
            setPadding(dp(5f), dp(1f), dp(5f), dp(1f))
        }
        titleRow.addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6f) })
        headline.apply {
            setTextColor(style.title.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titleRow.addView(headline, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        texts.addView(titleRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.apply {
            setTextColor(style.body.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
        row.addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        cta.apply {
            setTextColor(style.ctaText.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(style.ctaBackground.toArgb())
                cornerRadius = dp(16f).toFloat()
            }
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        }
        // Room for the AdChoices icon the SDK draws in the top-right corner.
        row.addView(cta, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(12f)
            topMargin = dp(10f)
        })

        // AdChoices (AdMob policy): drawn by the SDK in the top-right corner of the NativeAdView
        // (NativeAdOptions.ADCHOICES_TOP_RIGHT); the CTA's top margin keeps that corner free.
        adView.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        adView.headlineView = headline
        adView.bodyView = body
        adView.iconView = icon
        adView.callToActionView = cta
        addView(adView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(ad: NativeAd) {
        if (bound === ad) return
        bound = ad
        headline.text = ad.headline
        val secondary = ad.body ?: ad.advertiser ?: ad.store
        body.text = secondary
        body.visibility = if (secondary.isNullOrBlank()) View.GONE else View.VISIBLE
        val iconDrawable = ad.icon?.drawable
        icon.setImageDrawable(iconDrawable)
        icon.visibility = if (iconDrawable == null) View.GONE else View.VISIBLE
        cta.text = ad.callToAction
        cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        // Must come last: the SDK reads the registered asset views. Registered once the row is
        // attached to the window, so the SDK can lay out its AdChoices overlay.
        if (adView.isAttachedToWindow) {
            adView.setNativeAd(ad)
        } else {
            adView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    if (bound === ad) adView.setNativeAd(ad)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }

    fun destroy() {
        bound = null
        adView.destroy()
    }
}
