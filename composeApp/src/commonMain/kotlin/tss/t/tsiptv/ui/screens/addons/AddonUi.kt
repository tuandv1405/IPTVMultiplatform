package tss.t.tsiptv.ui.screens.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import tss.t.tsiptv.core.stremio.StremioMeta
import tss.t.tsiptv.core.stremio.StremioType
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvFocusableSurface
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.type_channel
import tsiptv.composeapp.generated.resources.type_movie
import tsiptv.composeapp.generated.resources.type_series
import tsiptv.composeapp.generated.resources.type_tv

/** Localized label for the known types; other raw strings as is (PRD §1). */
@Composable
fun addonTypeLabel(type: String): String = when (type) {
    StremioType.MOVIE -> stringResource(Res.string.type_movie)
    StremioType.SERIES -> stringResource(Res.string.type_series)
    StremioType.TV -> stringResource(Res.string.type_tv)
    StremioType.CHANNEL -> stringResource(Res.string.type_channel)
    else -> type
}

/** A dark dialog panel (phone and TV). Back / outside tap calls [onDismissRequest]. */
@Composable
fun AddonDialog(
    title: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier
                .widthIn(min = 300.dp, max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TSColors.SecondaryBackgroundColor)
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Text(title, color = TSColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** F3: the accent of buttons and chips; a TS IPTV Source's screens use its accent (PRD §4). */
val LocalAddonAccent = androidx.compose.runtime.compositionLocalOf { TSColors.AccentCyan }

/** A focusable pill button with the TV focus ring (also fine for touch). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    TvFocusableSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(22.dp),
        focusedScale = 1.04f,
        color = when {
            !enabled -> TSColors.White.copy(alpha = 0.06f)
            primary -> LocalAddonAccent.current.copy(alpha = 0.22f)
            else -> TSColors.White.copy(alpha = 0.08f)
        },
        focusedColor = if (primary) LocalAddonAccent.current.copy(alpha = 0.38f) else TSColors.White.copy(alpha = 0.18f),
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = TSColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
            Text(
                text,
                color = if (enabled) TSColors.TextPrimary else TSColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A chip (genre, season, type tab). */
@Composable
fun AddonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.05f,
        color = if (selected) LocalAddonAccent.current.copy(alpha = 0.3f) else TSColors.White.copy(alpha = 0.08f),
        focusedColor = if (selected) LocalAddonAccent.current.copy(alpha = 0.45f) else TSColors.White.copy(alpha = 0.18f),
        selected = false,
    ) {
        Text(
            text,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) TSColors.TextPrimary else TSColors.TextSecondaryLight,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Aspect ratio for `posterShape` (PRD §5): poster 2:3, square 1:1, landscape 16:9. */
fun posterAspect(shape: String): Float = when (shape) {
    "square" -> 1f
    "landscape" -> 16f / 9f
    else -> 2f / 3f
}

/**
 * A catalogue card: poster (placeholder with the name when missing) and the title below.
 * [modifier] goes on the focusable poster (focus requesters, key handlers), not on the column.
 */
@Composable
fun PosterCard(
    meta: StremioMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    focusedScale: Float = 1.08f,
    subtitle: String? = null,
    progress: Float? = null,
) {
    val aspect = posterAspect(meta.resolvedPosterShape)
    val cardWidth = if (aspect > 1.2f) width * 1.6f else if (aspect == 1f) width * 1.2f else width
    Column(Modifier.width(cardWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TvFocusableSurface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(aspect),
            shape = RoundedCornerShape(10.dp),
            focusedScale = focusedScale,
        ) {
            PosterImage(meta.poster, meta.displayName, Modifier.fillMaxSize())
            if (progress != null && progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(6.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).padding(vertical = 2.dp)
                            .background(TSColors.AccentCyan)
                    )
                }
            }
        }
        Text(
            meta.displayName,
            color = TSColors.TextSecondaryLight,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = TSColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Poster/thumbnail with a text placeholder (missing or broken image). */
@Composable
fun PosterImage(url: String?, name: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val placeholder = @Composable {
        Box(modifier.background(TSColors.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Text(
                name,
                modifier = Modifier.padding(8.dp),
                color = TSColors.TextSecondaryLight,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (url.isNullOrBlank()) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = name,
            contentScale = contentScale,
            modifier = modifier,
            error = { placeholder() },
            loading = { Box(modifier.background(TSColors.White.copy(alpha = 0.04f))) },
        )
    }
}

/** Addon logo (placeholder icon when missing). */
@Composable
fun AddonLogo(url: String?, modifier: Modifier = Modifier.size(44.dp)) {
    val fallback = @Composable {
        Box(modifier.clip(RoundedCornerShape(10.dp)).background(TSColors.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Extension, contentDescription = null, tint = TSColors.AccentCyan)
        }
    }
    if (url.isNullOrBlank()) fallback()
    else SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        contentScale = ContentScale.Fit,
        error = { fallback() },
    )
}

@Composable
fun AddonSectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = TSColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}
