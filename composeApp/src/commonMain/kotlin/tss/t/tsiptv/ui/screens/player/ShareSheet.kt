package tss.t.tsiptv.ui.screens.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.player_share
import tsiptv.composeapp.generated.resources.share_app_icon
import tsiptv.composeapp.generated.resources.share_card_get_app
import tsiptv.composeapp.generated.resources.share_card_watching
import tsiptv.composeapp.generated.resources.share_copy_image
import tsiptv.composeapp.generated.resources.share_failed
import tsiptv.composeapp.generated.resources.share_image_copied
import tsiptv.composeapp.generated.resources.share_more
import tsiptv.composeapp.generated.resources.share_no_frame
import tsiptv.composeapp.generated.resources.share_save_image
import tsiptv.composeapp.generated.resources.share_saved
import tsiptv.composeapp.generated.resources.share_text
import tss.t.tsiptv.core.firebase.analystics.AnalyticsConstants
import tss.t.tsiptv.core.tracking.AppAnalytics
import tss.t.tsiptv.feature.share.ShareLinks
import tss.t.tsiptv.feature.share.ShareResult
import tss.t.tsiptv.feature.share.ShareTarget
import tss.t.tsiptv.feature.share.getImageSharer
import tss.t.tsiptv.player.VideoSnapshot
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.ui.screens.addons.AddonDialog
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.themes.TSColors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Player › Share: a card with the picture on screen (or the channel logo when it cannot be read:
 * DRM, radio, iOS), the title and the Play Store link, sent to the apps this device has. The card
 * shown is the picture sent: it is recorded into a graphics layer at [SHARE_CARD_WIDTH_PX].
 *
 * @param capture reads the picture once, when the sheet opens ([PlayerViewModel.captureShareFrame])
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareSheet(
    mediaItem: MediaItem,
    capture: suspend () -> VideoSnapshot?,
    onDismiss: () -> Unit,
) {
    val sharer = remember { getImageSharer() }
    val targets = remember(sharer) { sharer.targets() }
    // null while capturing; then the snapshot, or null inside when there is no picture.
    val frame by produceState<Result<VideoSnapshot?>?>(null) { value = Result.success(capture()) }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val title = mediaItem.title.ifBlank { APP_NAME }
    val caption = stringResource(Res.string.share_text, title, ShareLinks.PLAY_STORE)

    AddonDialog(title = stringResource(Res.string.player_share), onDismissRequest = onDismiss) {
        val ready = frame
        if (ready == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TSColors.AccentCyan)
            }
        } else {
            val snapshot = ready.getOrNull()
            ShareCard(
                snapshot = snapshot,
                title = title,
                logoUrl = mediaItem.artworkUri,
                // Rounded on screen only: the clip is outside what is recorded.
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).recordInto(layer, SHARE_CARD_WIDTH_PX),
            )
            if (snapshot == null) {
                Text(stringResource(Res.string.share_no_frame), color = TSColors.TextSecondary, fontSize = 13.sp)
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            targets.forEach { target ->
                PillButton(
                    text = targetLabel(target),
                    primary = target != ShareTarget.SAVE && target != ShareTarget.COPY,
                    enabled = ready != null && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            val result = try {
                                sharer.share(target, layer.toImageBitmap(), caption)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                ShareResult.Failed
                            }
                            AppAnalytics.logEvent(
                                AnalyticsConstants.EVENT_PLAYER_SHARE,
                                mapOf(
                                    AnalyticsConstants.PARAMS_SHARE_TARGET to target.name.lowercase(),
                                    AnalyticsConstants.PARAMS_SHARE_RESULT to result.analyticsName(),
                                    AnalyticsConstants.PARAMS_SHARE_HAS_FRAME to (ready?.getOrNull() != null).toString(),
                                )
                            )
                            busy = false
                            when (result) {
                                // The other app or the system sheet is in front now.
                                ShareResult.Opened, ShareResult.OpenedCaptionCopied -> onDismiss()
                                ShareResult.OpenedImageCopied, ShareResult.ImageCopied ->
                                    message = getString(Res.string.share_image_copied)
                                is ShareResult.Saved -> message = getString(Res.string.share_saved, result.where)
                                ShareResult.Failed -> message = getString(Res.string.share_failed)
                            }
                        }
                    },
                )
            }
        }
        message?.let { Text(it, color = TSColors.TextSecondaryLight, fontSize = 14.sp) }
    }
}

@Composable
private fun targetLabel(target: ShareTarget): String = when (target) {
    ShareTarget.FACEBOOK -> "Facebook"
    ShareTarget.INSTAGRAM -> "Instagram"
    ShareTarget.ZALO -> "Zalo"
    ShareTarget.TELEGRAM -> "Telegram"
    ShareTarget.MORE -> stringResource(Res.string.share_more)
    ShareTarget.COPY -> stringResource(Res.string.share_copy_image)
    ShareTarget.SAVE -> stringResource(Res.string.share_save_image)
}

private fun ShareResult.analyticsName(): String = when (this) {
    ShareResult.Opened, ShareResult.OpenedCaptionCopied, ShareResult.OpenedImageCopied -> "opened"
    ShareResult.ImageCopied -> "copied"
    is ShareResult.Saved -> "saved"
    ShareResult.Failed -> "failed"
}

/**
 * The card: the picture (its display aspect, kept between 4:3 and 21:9) or the logo on the app's
 * gradient, then the app icon, the title and the store line. Square corners: a JPEG has no
 * transparency, so rounded ones would be sent with white corners (the sheet rounds it on screen).
 */
@Composable
internal fun ShareCard(
    snapshot: VideoSnapshot?,
    title: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TSColors.DeepBlue),
    ) {
        val aspect = snapshot?.aspect?.takeIf { it.isFinite() && it > 0f }?.coerceIn(4f / 3f, 21f / 9f) ?: (16f / 9f)
        Box(Modifier.fillMaxWidth().aspectRatio(aspect), contentAlignment = Alignment.Center) {
            if (snapshot != null) {
                Image(
                    bitmap = snapshot.image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(TSColors.SecondaryBackgroundColor, TSColors.DeepBlue))
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logoUrl.isNullOrBlank()) {
                        Image(painterResource(Res.drawable.share_app_icon), contentDescription = null, modifier = Modifier.size(96.dp))
                    } else {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(0.5f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(TSColors.SecondaryBackgroundColor, TSColors.DeepBlue)))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.share_app_icon),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = title,
                    color = TSColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(Res.string.share_card_watching), color = TSColors.TextSecondary, fontSize = 12.sp)
            }
            Text(
                text = stringResource(Res.string.share_card_get_app),
                color = Color(0xFF03041D),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(TSColors.AccentCyan)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Draws the content as usual and records it into [layer] at least [widthPx] wide, so the picture
 * sent is sharp on a low-density screen too (text and vectors are redrawn at that size).
 */
internal fun Modifier.recordInto(layer: GraphicsLayer, widthPx: Int): Modifier = drawWithContent {
    val k = max(1f, widthPx / size.width)
    layer.record(size = IntSize((size.width * k).roundToInt(), (size.height * k).roundToInt())) {
        scale(k, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
    }
    scale(1f / k, pivot = Offset.Zero) { drawLayer(layer) }
}

internal const val SHARE_CARD_WIDTH_PX = 1280
private const val APP_NAME = "TS IPTV"
