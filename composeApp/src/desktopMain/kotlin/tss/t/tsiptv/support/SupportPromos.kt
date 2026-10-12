package tss.t.tsiptv.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.support_android_body
import tsiptv.composeapp.generated.resources.support_android_button
import tsiptv.composeapp.generated.resources.support_android_title
import tsiptv.composeapp.generated.resources.support_bank_hint
import tsiptv.composeapp.generated.resources.support_bank_title
import tsiptv.composeapp.generated.resources.support_banner_body
import tsiptv.composeapp.generated.resources.support_button
import tsiptv.composeapp.generated.resources.support_dialog_body
import tsiptv.composeapp.generated.resources.support_dialog_title
import tsiptv.composeapp.generated.resources.support_later
import tsiptv.composeapp.generated.resources.support_online_button
import tsiptv.composeapp.generated.resources.support_title
import tss.t.tsiptv.core.ads.AdsGate
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.themes.TSTextStyles
import tss.t.tsiptv.ui.widgets.GradientButton1
import tss.t.tsiptv.utils.getUrlOpener

/** Which house promo a slot shows. */
enum class SupportPromo { DONATE, ANDROID_APP }

/** The support dialog is open (from a promo's button, or as the start screen). */
object SupportDialogState {
    var visible by mutableStateOf(false)
}

/**
 * The start screen takes the place of the mobile app open ad: once per launch, at most every
 * [START_SCREEN_MIN_INTERVAL_MS], and only if it may show within [START_SCREEN_WAIT_MS] of start.
 */
private const val START_SCREEN_MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val START_SCREEN_WAIT_MS = 4_000L
private const val KEY_START_SCREEN_LAST_MS = "support_start_screen_last_ms"

/**
 * Hosts the support dialog over the whole window and opens it as the start screen. The ad rules
 * decide ([AdsGate]): nothing in the first 24 h of use, on the TV layout or for an ad-free subscriber,
 * the same as the ads it replaces.
 */
@Composable
fun SupportHost() {
    val gate = koinInject<AdsGate>()
    val storage = koinInject<KeyValueStorage>()
    LaunchedEffect(Unit) {
        if (!SupportLinks.canDonate) return@LaunchedEffect
        withTimeoutOrNull(START_SCREEN_WAIT_MS) { gate.state.first { it.fallback } } ?: return@LaunchedEffect
        val now = AdsGate.systemNowMs()
        if (now - storage.getLong(KEY_START_SCREEN_LAST_MS, 0L) < START_SCREEN_MIN_INTERVAL_MS) return@LaunchedEffect
        storage.putLong(KEY_START_SCREEN_LAST_MS, now)
        SupportDialogState.visible = true
    }
    if (SupportDialogState.visible) SupportDialog(onDismiss = { SupportDialogState.visible = false })
}

/** A promo card in an ad slot (banner or list), in the app's own style. */
@Composable
fun SupportPromoCard(promo: SupportPromo, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val (icon, title, body, button) = when (promo) {
        SupportPromo.DONATE -> PromoText(
            Icons.Filled.Favorite,
            stringResource(Res.string.support_title),
            stringResource(Res.string.support_banner_body),
            stringResource(Res.string.support_button),
        )
        SupportPromo.ANDROID_APP -> PromoText(
            Icons.Filled.PhoneAndroid,
            stringResource(Res.string.support_android_title),
            stringResource(Res.string.support_android_body),
            stringResource(Res.string.support_android_button),
        )
    }
    val onClick: () -> Unit = when (promo) {
        SupportPromo.DONATE -> ({ SupportDialogState.visible = true })
        SupportPromo.ANDROID_APP -> ({ scope.launch { getUrlOpener().openUrl(SupportLinks.PLAY_STORE_URL) } })
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(TSShapes.roundedShape16)
            .background(TSColors.SecondaryBackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(TSShapes.roundedShape12).background(TSColors.baseGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TSTextStyles.semiBold15.copy(color = TSColors.TextPrimary), maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(body, style = TSTextStyles.normal13.copy(color = TSColors.TextSecondary), maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        // CommonButton fills the width it is given: a fixed width keeps the text column readable.
        GradientButton1(button, modifier = Modifier.width(140.dp), onClick = onClick)
    }
}

private data class PromoText(val icon: ImageVector, val title: String, val body: String, val button: String)

/** The support screen: VietQR for a bank transfer, an online link, and the Android app. */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // The scrim closes the dialog; the card itself swallows clicks.
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .clip(TSShapes.roundedShape24)
                .background(TSColors.SecondaryBackgroundColor)
                .clickable(interactionSource = MutableInteractionSource(), indication = null) { }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The text and QR scroll in a short window; the buttons below always stay visible.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(56.dp).clip(TSShapes.roundedShape16).background(TSColors.baseGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.support_dialog_title),
                    style = TSTextStyles.semiBold19.copy(color = TSColors.TextPrimary),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.support_dialog_body),
                    style = TSTextStyles.normal15.copy(color = TSColors.TextSecondary),
                    textAlign = TextAlign.Center,
                )
                SupportLinks.vietQrImageUrl?.let { qr ->
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(Res.string.support_bank_title),
                        style = TSTextStyles.semiBold15.copy(color = TSColors.TextPrimary),
                    )
                    Spacer(Modifier.height(10.dp))
                    AsyncImage(
                        model = qr,
                        contentDescription = stringResource(Res.string.support_bank_title),
                        modifier = Modifier.size(200.dp).clip(TSShapes.roundedShape12).background(Color.White),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.support_bank_hint),
                        style = TSTextStyles.normal13.copy(color = TSColors.TextSecondary),
                    )
                    Text(
                        listOf(SupportLinks.bankId, SupportLinks.accountNo, SupportLinks.accountName)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = TSTextStyles.semiBold13.copy(color = TSColors.TextSecondaryLight),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SupportLinks.supportUrl?.let { url ->
                GradientButton1(stringResource(Res.string.support_online_button), Modifier.fillMaxWidth()) {
                    scope.launch { getUrlOpener().openUrl(url) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { getUrlOpener().openUrl(SupportLinks.PLAY_STORE_URL) } }) {
                    Text(stringResource(Res.string.support_android_button), color = TSColors.AccentCyan)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.support_later), color = TSColors.TextSecondary)
                }
            }
        }
    }
}
