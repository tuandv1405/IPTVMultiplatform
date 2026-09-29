package tss.t.tsiptv.ui.screens.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tss.t.tsiptv.core.parser.tsiptv.TsiptvCardStyle
import tss.t.tsiptv.core.parser.tsiptv.TsiptvEffectiveAppearance
import tss.t.tsiptv.ui.themes.TSColors

/**
 * The colours a source's appearance gives its home, See all and detail pages (PRD F3 §4
 * "Appearance"), after the legibility rules. The player, dialogs and settings keep the app theme.
 */
data class SourceColors(
    val accent: Color = TSColors.AccentCyan,
    val accentSecondary: Color = TSColors.AccentGreen,
    val cardDefaults: TsiptvCardStyle? = null,
)

val LocalSourceColors = staticCompositionLocalOf { SourceColors() }

/** `#RRGGBB` → [Color]; [fallback] for anything else. */
fun parseSourceColor(value: String?, fallback: Color): Color {
    val hex = value?.removePrefix("#")?.takeIf { it.length == 6 } ?: return fallback
    val rgb = hex.toLongOrNull(16) ?: return fallback
    return Color(0xFF000000 or rgb)
}

fun TsiptvEffectiveAppearance.toColors() = SourceColors(
    accent = parseSourceColor(accent, TSColors.AccentCyan),
    accentSecondary = parseSourceColor(accentSecondary, TSColors.AccentGreen),
    cardDefaults = card,
)

/** Corner radius of [TsiptvCardStyle] corners (spec §6.1). */
fun cornerDp(dp: Int): Dp = dp.dp

/**
 * The source's background: its gradient or colour (already checked for contrast), and the
 * optional image under a dark scrim of [TsiptvEffectiveAppearance.imageDim].
 */
@Composable
fun SourceBackground(
    appearance: TsiptvEffectiveAppearance,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = appearance.backgroundGradient?.map { parseSourceColor(it, TSColors.BackgroundColor) }
        ?.let { Brush.verticalGradient(it) }
        ?: Brush.verticalGradient(listOf(parseSourceColor(appearance.backgroundColor, TSColors.BackgroundColor), parseSourceColor(appearance.backgroundColor, TSColors.BackgroundColor)))
    val colors = appearance.toColors()
    // Buttons, chips and TV focus rings take the source's accent (already contrast-checked).
    CompositionLocalProvider(
        LocalSourceColors provides colors,
        tss.t.tsiptv.ui.screens.addons.LocalAddonAccent provides colors.accent,
        tss.t.tsiptv.ui.tv.LocalFocusRingColor provides colors.accent,
    ) {
        Box(modifier.fillMaxSize().background(background)) {
            appearance.backgroundImage?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = appearance.imageDim.toFloat())))
            }
            content()
        }
    }
}

/**
 * F3 (PRD §4 "Appearance", AC-T14): detail pages of a source item take the source's background and
 * accent. [content] gets whether the source appearance is applied (then it draws no background).
 */
@Composable
fun SourceAppearanceScope(playlistId: String?, content: @Composable (sourceApplied: Boolean) -> Unit) {
    if (playlistId == null) {
        content(false)
        return
    }
    val database = org.koin.compose.koinInject<tss.t.tsiptv.core.database.IPTVDatabase>()
    val appearance by androidx.compose.runtime.produceState<TsiptvEffectiveAppearance?>(null, playlistId) {
        val json = database.tsiptvStore.getSource(playlistId)?.appearanceJson
        val stored = json?.let {
            runCatching {
                tss.t.tsiptv.core.tsiptv.TsiptvStorageJson.decodeFromString(tss.t.tsiptv.core.parser.tsiptv.TsiptvAppearance.serializer(), it)
            }.getOrNull()
        }
        value = tss.t.tsiptv.core.parser.tsiptv.TsiptvAppearanceResolver.resolve(stored).first
    }
    val resolved = appearance
    if (resolved == null) content(false) else SourceBackground(resolved) { content(true) }
}
