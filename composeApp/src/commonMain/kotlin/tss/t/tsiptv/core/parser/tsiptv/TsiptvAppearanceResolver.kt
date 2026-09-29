package tss.t.tsiptv.core.parser.tsiptv

import kotlin.math.pow

/**
 * The appearance a reader actually draws (§6): creator values with app defaults filled in and
 * the legibility rules applied.
 *
 * @property backgroundGradient Non-null only when the creator's gradient passed the contrast rule
 * @property creatorBackgroundUsed false when the creator's colour/gradient was ignored (`W_CONTRAST`)
 * @property creatorAccentUsed false when the accents were ignored (`W_CONTRAST`) or not set
 */
data class TsiptvEffectiveAppearance(
    val accent: String,
    val accentSecondary: String,
    val backgroundColor: String,
    val backgroundGradient: List<String>?,
    val backgroundImage: String?,
    val imageDim: Double,
    val card: TsiptvCardStyle?,
    val creatorBackgroundUsed: Boolean,
    val creatorAccentUsed: Boolean,
)

object TsiptvAppearanceResolver {
    /** The app's own text colour, which a source never changes (§6). */
    const val APP_TEXT = "#F3F4F6"
    const val APP_ACCENT = "#00F5A0"
    const val APP_ACCENT_SECONDARY = "#00D9E9"
    const val APP_BACKGROUND = "#03041D"

    const val MIN_TEXT_CONTRAST = 4.5
    const val MIN_ACCENT_CONTRAST = 3.0

    /**
     * Applies §6: a background (colour, or every gradient stop) below 4.5:1 against [APP_TEXT] is
     * replaced by the app background; an accent below 3:1 against the effective background (every
     * stop) is replaced, together with the secondary accent, by the app accents. The background
     * image and its scrim are kept either way: the scrim is what keeps text legible over it.
     *
     * @return the effective appearance and the `W_CONTRAST` warnings
     */
    fun resolve(appearance: TsiptvAppearance?): Pair<TsiptvEffectiveAppearance, List<TsiptvIssue>> {
        val issues = mutableListOf<TsiptvIssue>()
        val background = appearance?.background

        val creatorStops = background?.gradient ?: background?.color?.let { listOf(it) }
        val backgroundOk = creatorStops != null &&
                creatorStops.all { contrastRatio(APP_TEXT, it) >= MIN_TEXT_CONTRAST }
        if (creatorStops != null && !backgroundOk) {
            val field = if (background?.gradient != null) "gradient" else "color"
            issues += TsiptvIssue(
                TsiptvIssueCode.W_CONTRAST,
                "appearance.background.$field",
                "Background contrast with the text is below 4.5:1; the app background is used.",
            )
        }
        val effectiveStops = if (backgroundOk) creatorStops!! else listOf(APP_BACKGROUND)

        val accent = appearance?.accent
        val accentOk = accent != null &&
                effectiveStops.all { contrastRatio(accent, it) >= MIN_ACCENT_CONTRAST }
        if (accent != null && !accentOk) {
            issues += TsiptvIssue(
                TsiptvIssueCode.W_CONTRAST,
                "appearance.accent",
                "Accent contrast with the background is below 3:1; the app accents are used.",
            )
        }

        val creatorColor = background?.color
        val effective = TsiptvEffectiveAppearance(
            accent = if (accentOk) accent!! else APP_ACCENT,
            accentSecondary = if (accentOk || accent == null) {
                appearance?.accentSecondary ?: APP_ACCENT_SECONDARY
            } else {
                APP_ACCENT_SECONDARY
            },
            // With a gradient only the stops were checked: the creator colour is used only if it
            // passes on its own, else the first (checked) stop.
            backgroundColor = when {
                !backgroundOk -> APP_BACKGROUND
                creatorColor != null && contrastRatio(APP_TEXT, creatorColor) >= MIN_TEXT_CONTRAST -> creatorColor
                else -> effectiveStops.first()
            },
            backgroundGradient = if (backgroundOk) background?.gradient else null,
            backgroundImage = background?.image,
            imageDim = background?.imageDim ?: TsiptvBackground.DEFAULT_IMAGE_DIM,
            card = appearance?.card,
            creatorBackgroundUsed = backgroundOk,
            creatorAccentUsed = accentOk,
        )
        return effective to issues
    }

    /** WCAG 2.1 contrast ratio of two `#RRGGBB` colours, 1.0 … 21.0. */
    fun contrastRatio(a: String, b: String): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun relativeLuminance(color: String): Double {
        val hex = color.removePrefix("#")
        require(hex.length == 6) { "not #RRGGBB" }
        fun channel(i: Int): Double {
            val c = hex.substring(i, i + 2).toInt(16) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    }
}

/** What kind of item a card shows, for the `auto` rule of §6.1. */
enum class TsiptvCardItemKind { MOVIE, SERIES, TV_CHANNEL, RADIO_CHANNEL, EPISODE, STREMIO_ITEM }

/** A card style with every field decided. [style] is never [TsiptvCardKind.AUTO]. */
data class TsiptvResolvedCard(
    val style: TsiptvCardKind,
    val corner: TsiptvCorner,
    val showTitles: Boolean,
)

object TsiptvCardResolver {
    /**
     * Section `card` → `appearance.card` → defaults, field by field; then `auto` resolved per item
     * kind: movies and series → poster, TV channels → logo, radio → square, episodes → landscape,
     * Stremio items → their `posterShape` ([stremioPosterShape]: `landscape`, `square`, anything else poster).
     * Channel and `list` cards always show titles.
     */
    fun resolve(
        section: TsiptvCardStyle?,
        appearance: TsiptvCardStyle?,
        itemKind: TsiptvCardItemKind,
        stremioPosterShape: String? = null,
    ): TsiptvResolvedCard {
        val declared = section?.style ?: appearance?.style ?: TsiptvCardKind.AUTO
        val style = if (declared != TsiptvCardKind.AUTO) declared else when (itemKind) {
            TsiptvCardItemKind.MOVIE, TsiptvCardItemKind.SERIES -> TsiptvCardKind.POSTER
            TsiptvCardItemKind.TV_CHANNEL -> TsiptvCardKind.LOGO
            TsiptvCardItemKind.RADIO_CHANNEL -> TsiptvCardKind.SQUARE
            TsiptvCardItemKind.EPISODE -> TsiptvCardKind.LANDSCAPE
            TsiptvCardItemKind.STREMIO_ITEM -> when (stremioPosterShape?.lowercase()) {
                "landscape" -> TsiptvCardKind.LANDSCAPE
                "square" -> TsiptvCardKind.SQUARE
                else -> TsiptvCardKind.POSTER
            }
        }
        val corner = section?.corner ?: appearance?.corner ?: TsiptvCorner.MEDIUM
        val isChannel = itemKind == TsiptvCardItemKind.TV_CHANNEL || itemKind == TsiptvCardItemKind.RADIO_CHANNEL
        val showTitles = isChannel || style == TsiptvCardKind.LIST ||
                (section?.showTitles ?: appearance?.showTitles ?: true)
        return TsiptvResolvedCard(style, corner, showTitles)
    }
}

/** What the pools contain after includes were merged, for the default layout (§7.5). */
data class TsiptvPoolSummary(
    val hasMovies: Boolean,
    val hasMovieWithBackdrop: Boolean,
    val hasSeries: Boolean,
    val hasRadio: Boolean,
    val hasChannels: Boolean,
)

object TsiptvDefaultLayout {
    /**
     * §7.5, used when the document has no layout (or every section was skipped).
     * Titles are the reader's strings, given as [TsiptvSection.builtInTitle].
     */
    fun build(pools: TsiptvPoolSummary): TsiptvLayout {
        val sections = buildList {
            add(
                TsiptvSection(
                    type = TsiptvSectionType.ROW,
                    query = TsiptvQuery(TsiptvQuerySource.CONTINUE_WATCHING, sort = TsiptvQuerySort.RECENT),
                    seeAll = true,
                    builtInTitle = TsiptvBuiltInTitle.CONTINUE_WATCHING,
                )
            )
            if (pools.hasMovieWithBackdrop) {
                add(TsiptvSection(type = TsiptvSectionType.HERO, query = TsiptvQuery(TsiptvQuerySource.MOVIES, limit = 5)))
            }
            if (pools.hasMovies) {
                add(row(TsiptvQuerySource.MOVIES, TsiptvBuiltInTitle.MOVIES))
            }
            if (pools.hasSeries) {
                add(row(TsiptvQuerySource.SERIES, TsiptvBuiltInTitle.SERIES))
            }
            if (pools.hasRadio) {
                add(row(TsiptvQuerySource.RADIO, TsiptvBuiltInTitle.RADIO))
            }
            if (pools.hasChannels) {
                add(
                    TsiptvSection(
                        type = TsiptvSectionType.GRID,
                        query = TsiptvQuery(TsiptvQuerySource.CHANNELS, sort = TsiptvQuerySort.NUMBER),
                        groupChips = true,
                        builtInTitle = TsiptvBuiltInTitle.CHANNELS,
                    )
                )
            }
        }
        return TsiptvLayout(sections)
    }

    private fun row(from: TsiptvQuerySource, title: TsiptvBuiltInTitle) =
        TsiptvSection(type = TsiptvSectionType.ROW, query = TsiptvQuery(from), seeAll = true, builtInTitle = title)
}
