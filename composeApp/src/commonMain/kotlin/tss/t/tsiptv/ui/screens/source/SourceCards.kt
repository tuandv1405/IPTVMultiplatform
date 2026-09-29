package tss.t.tsiptv.ui.screens.source

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardItemKind
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardKind
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardResolver
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardStyle
import tss.t.tsiptv.core.parser.tsiptv.TsiptvResolvedCard
import tss.t.tsiptv.core.tsiptv.SourceHomeBuilder
import tss.t.tsiptv.core.tsiptv.SourceItem
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.ui.themes.TSColors

/**
 * A D-pad friendly surface whose focus ring uses the source's accent (PRD F3 §4: "focused card
 * scales 1.08 with an accent focus ring").
 */
@Composable
fun SourceFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.08f,
    color: Color = TSColors.White.copy(alpha = 0.06f),
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val accent = LocalSourceColors.current.accent
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(color, shape)
            .border(width = if (focused) 3.dp else 0.dp, color = if (focused) accent else Color.Transparent, shape = shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = { content(focused) },
    )
}

/** The item kind for the `auto` card style (spec §6.1). */
fun cardKindOf(item: SourceItem): TsiptvCardItemKind = when (item) {
    is SourceItem.ChannelItem -> if (item.channel.isRadio) TsiptvCardItemKind.RADIO_CHANNEL else TsiptvCardItemKind.TV_CHANNEL
    is SourceItem.Vod -> if (item.item.isSeries) TsiptvCardItemKind.SERIES else TsiptvCardItemKind.MOVIE
    is SourceItem.Episode -> TsiptvCardItemKind.EPISODE
    is SourceItem.Continue -> TsiptvCardItemKind.EPISODE
}

/** Section `card` → `appearance.card` → `auto` (spec §6.1). */
@Composable
fun resolveCard(section: TsiptvCardStyle?, item: SourceItem): TsiptvResolvedCard =
    TsiptvCardResolver.resolve(section, LocalSourceColors.current.cardDefaults, cardKindOf(item))

/** Card width per style: phone and TV. */
@Composable
fun cardWidth(style: TsiptvCardKind): Dp {
    val tv = LocalIsTvMode.current
    return when (style) {
        TsiptvCardKind.POSTER, TsiptvCardKind.AUTO -> if (tv) 140.dp else 110.dp
        TsiptvCardKind.LANDSCAPE -> if (tv) 240.dp else 180.dp
        TsiptvCardKind.SQUARE -> if (tv) 140.dp else 110.dp
        TsiptvCardKind.LOGO -> if (tv) 170.dp else 128.dp
        TsiptvCardKind.LIST -> if (tv) 360.dp else 280.dp
    }
}

private fun aspectOf(style: TsiptvCardKind): Float = when (style) {
    TsiptvCardKind.LANDSCAPE -> 16f / 9f
    TsiptvCardKind.SQUARE -> 1f
    TsiptvCardKind.LOGO -> 16f / 10f
    else -> 2f / 3f
}

private fun imageOf(item: SourceItem, style: TsiptvCardKind): String? = when (item) {
    is SourceItem.ChannelItem -> item.channel.logoUrl
    is SourceItem.Vod -> when (style) {
        TsiptvCardKind.LANDSCAPE -> item.item.backdrop ?: item.item.poster
        TsiptvCardKind.SQUARE, TsiptvCardKind.LOGO, TsiptvCardKind.LIST -> item.item.logo ?: item.item.poster
        else -> item.item.poster ?: item.item.backdrop
    }
    is SourceItem.Episode -> item.series.backdrop ?: item.series.poster
    is SourceItem.Continue -> item.item?.backdrop ?: item.record.posterUrl ?: item.item?.poster
}

/**
 * One card of a section. [item] decides the image; [card] the shape, corner and titles.
 * [programme] is the "now on" line of `list` cards (channels with guide data).
 */
@Composable
fun SourceCard(
    item: SourceItem,
    card: TsiptvResolvedCard,
    uiLanguage: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = cardWidth(card.style),
    programme: String? = null,
) {
    val name = SourceHomeBuilder.displayName(item, uiLanguage)
    val shape = RoundedCornerShape(cornerDp(card.corner.dp))
    val image = imageOf(item, card.style)
    val number = (item as? SourceItem.ChannelItem)?.channel?.number
    val progress = (item as? SourceItem.Continue)?.record?.let { r ->
        if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) else null
    }
    val subtitle = (item as? SourceItem.Continue)?.record?.subtitle

    if (card.style == TsiptvCardKind.LIST) {
        SourceFocusSurface(onClick = onClick, modifier = modifier.width(width), shape = shape, focusedScale = 1.04f) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardImage(image, name, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(number?.toString(), name).joinToString("  "), color = TSColors.TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    (programme ?: subtitle)?.let {
                        Text(it, color = TSColors.TextSecondaryLight, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        return
    }

    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SourceFocusSurface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(aspectOf(card.style)),
            shape = shape,
        ) {
            val fit = if (card.style == TsiptvCardKind.LOGO || item is SourceItem.ChannelItem) ContentScale.Fit else ContentScale.Crop
            CardImage(
                image, name,
                Modifier.fillMaxSize().then(if (fit == ContentScale.Fit) Modifier.padding(12.dp) else Modifier),
                fit,
            )
            if (number != null && card.style == TsiptvCardKind.LOGO) {
                Text(
                    number.toString(),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = TSColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            progress?.let { p ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(4.dp).background(LocalSourceColors.current.accent))
            }
        }
        if (card.showTitles) {
            // The name opens the item too (touch), without being a second D-pad stop.
            Text(
                name, color = TSColors.TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
                ).focusProperties { canFocus = false },
            )
            subtitle?.let { Text(it, color = TSColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Image with a placeholder tile showing the name's initials (PRD F3 §4 "Cards"). */
@Composable
fun CardImage(url: String?, name: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val placeholder = @Composable {
        Box(modifier.background(TSColors.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text(initials(name), color = TSColors.TextSecondaryLight, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
    if (url.isNullOrBlank()) placeholder()
    else SubcomposeAsyncImage(
        model = url,
        contentDescription = name,
        modifier = modifier,
        contentScale = contentScale,
        error = { placeholder() },
        loading = { placeholder() },
    )
}

/**
 * Placeholder initials (QC #22): the first letters of the first two words that start with a letter
 * ("Movie 10" → "M", "His Girl Friday" → "HG"); "•" when there is none.
 */
fun initials(name: String): String =
    name.split(' ', '-', '_').filter { it.isNotBlank() && it.first().isLetter() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "•" }
