package tss.t.tsiptv.core.parser.tsiptv

import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_URL
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_CONTRAST
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_FIELD
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §6 appearance, legibility rules (AC-T16), card resolution (§6.1) and the default layout (§7.5). */
class TsiptvAppearanceTest {

    private fun appearance(json: String): Pair<TsiptvAppearance?, TsiptvValidationReport> {
        val (doc, report) = success(docWithChannel(""""appearance":$json"""))
        return doc.appearance to report
    }

    @Test
    fun contrastRatio() {
        assertTrue(abs(TsiptvAppearanceResolver.contrastRatio("#FFFFFF", "#000000") - 21.0) < 1e-9)
        assertEquals(1.0, TsiptvAppearanceResolver.contrastRatio("#123456", "#123456"))
        val ratio = TsiptvAppearanceResolver.contrastRatio("#777777", "#FFFFFF")
        assertTrue(ratio in 4.47..4.49, "WCAG reference: #777 on white is 4.48:1, got $ratio")
    }

    // AC-T16
    @Test
    fun aLightBackgroundIsReplacedByTheAppBackground() {
        val (a, report) = appearance("""{"background":{"color":"#DDDDDD"},"accent":"#FFB300"}""")
        assertEquals(listOf(W_CONTRAST to "appearance.background.color"), report.codes())
        val (effective, _) = TsiptvAppearanceResolver.resolve(a)
        assertFalse(effective.creatorBackgroundUsed)
        assertEquals(TsiptvAppearanceResolver.APP_BACKGROUND, effective.backgroundColor)
        // The accent is then checked against the app background, where it is legible.
        assertEquals("#FFB300", effective.accent)
    }

    @Test
    fun oneLightGradientStopRejectsTheGradient() {
        val (a, report) = appearance("""{"background":{"color":"#101014","gradient":["#101014","#F0F0F0"]}}""")
        assertEquals(listOf(W_CONTRAST to "appearance.background.gradient"), report.codes())
        val (effective, _) = TsiptvAppearanceResolver.resolve(a)
        assertNull(effective.backgroundGradient)
        assertEquals(TsiptvAppearanceResolver.APP_BACKGROUND, effective.backgroundColor)
    }

    @Test
    fun aDarkAccentOnADarkBackgroundIsReplacedWithTheSecondaryAccent() {
        val (a, report) = appearance("""{"background":{"color":"#101014"},"accent":"#202020","accentSecondary":"#FF7043"}""")
        assertEquals(listOf(W_CONTRAST to "appearance.accent"), report.codes())
        val (effective, _) = TsiptvAppearanceResolver.resolve(a)
        assertTrue(effective.creatorBackgroundUsed)
        assertEquals("#101014", effective.backgroundColor)
        assertEquals(TsiptvAppearanceResolver.APP_ACCENT, effective.accent)
        assertEquals(TsiptvAppearanceResolver.APP_ACCENT_SECONDARY, effective.accentSecondary)
    }

    @Test
    fun legibleValuesAreKept() {
        val (a, report) = appearance(
            """{"accent":"#4FC3F7","accentSecondary":"#FF7043","background":{"gradient":["#06121C","#101820","#1C1C24"],"image":"https://img.example.com/bg.jpg","imageDim":0.8},"card":{"style":"landscape"}}"""
        )
        assertTrue(report.issues.isEmpty())
        val (effective, warnings) = TsiptvAppearanceResolver.resolve(a)
        assertTrue(warnings.isEmpty())
        assertEquals("#4FC3F7", effective.accent)
        assertEquals("#FF7043", effective.accentSecondary)
        assertEquals(listOf("#06121C", "#101820", "#1C1C24"), effective.backgroundGradient)
        assertEquals("https://img.example.com/bg.jpg", effective.backgroundImage)
        assertEquals(0.8, effective.imageDim)
    }

    @Test
    fun defaultsWithoutAppearance() {
        val (effective, warnings) = TsiptvAppearanceResolver.resolve(null)
        assertTrue(warnings.isEmpty())
        assertEquals(TsiptvAppearanceResolver.APP_ACCENT, effective.accent)
        assertEquals(TsiptvAppearanceResolver.APP_ACCENT_SECONDARY, effective.accentSecondary)
        assertEquals(TsiptvAppearanceResolver.APP_BACKGROUND, effective.backgroundColor)
        assertEquals(TsiptvBackground.DEFAULT_IMAGE_DIM, effective.imageDim)
    }

    @Test
    fun invalidAppearanceFields() {
        val (a, report) = appearance(
            """{"accent":"orange","accentSecondary":"#FFF","background":{"color":"#10101","gradient":["#101014"],"image":"file:///bg.jpg","imageDim":1.5},"card":"poster"}"""
        )
        assertEquals(
            listOf(
                W_FIELD to "appearance.background.gradient",
                W_FIELD to "appearance.background.imageDim",
                W_FIELD to "appearance.background.color",
                E_URL to "appearance.background.image",
                W_FIELD to "appearance.accent",
                W_FIELD to "appearance.accentSecondary",
                W_FIELD to "appearance.card",
            ),
            report.codes()
        )
        assertEquals(TsiptvAppearance(background = TsiptvBackground()), a)
        assertEquals(listOf(W_FIELD to "appearance"), success(docWithChannel(""""appearance":"dark"""")).second.codes())
    }

    @Test
    fun imageDimBelowTheMinimumIsRaised() {
        val (a, report) = appearance("""{"background":{"image":"https://img.example.com/bg.jpg","imageDim":0.1}}""")
        assertTrue(report.issues.isEmpty())
        assertEquals(0.3, a?.background?.imageDim)
    }

    @Test
    fun cardResolution() {
        val appearanceCard = TsiptvCardStyle(style = TsiptvCardKind.POSTER, corner = TsiptvCorner.LARGE, showTitles = false)
        val section = TsiptvCardStyle(style = TsiptvCardKind.LANDSCAPE)
        assertEquals(
            TsiptvResolvedCard(TsiptvCardKind.LANDSCAPE, TsiptvCorner.LARGE, false),
            TsiptvCardResolver.resolve(section, appearanceCard, TsiptvCardItemKind.MOVIE)
        )
        // Channel and list cards always show names.
        assertTrue(TsiptvCardResolver.resolve(section, appearanceCard, TsiptvCardItemKind.TV_CHANNEL).showTitles)
        assertTrue(TsiptvCardResolver.resolve(TsiptvCardStyle(TsiptvCardKind.LIST), appearanceCard, TsiptvCardItemKind.MOVIE).showTitles)

        fun auto(kind: TsiptvCardItemKind, shape: String? = null) =
            TsiptvCardResolver.resolve(null, null, kind, shape).style
        assertEquals(TsiptvCardKind.POSTER, auto(TsiptvCardItemKind.MOVIE))
        assertEquals(TsiptvCardKind.POSTER, auto(TsiptvCardItemKind.SERIES))
        assertEquals(TsiptvCardKind.LOGO, auto(TsiptvCardItemKind.TV_CHANNEL))
        assertEquals(TsiptvCardKind.SQUARE, auto(TsiptvCardItemKind.RADIO_CHANNEL))
        assertEquals(TsiptvCardKind.LANDSCAPE, auto(TsiptvCardItemKind.EPISODE))
        assertEquals(TsiptvCardKind.LANDSCAPE, auto(TsiptvCardItemKind.STREMIO_ITEM, "landscape"))
        assertEquals(TsiptvCardKind.SQUARE, auto(TsiptvCardItemKind.STREMIO_ITEM, "square"))
        assertEquals(TsiptvCardKind.POSTER, auto(TsiptvCardItemKind.STREMIO_ITEM, null))
        val defaults = TsiptvCardResolver.resolve(null, null, TsiptvCardItemKind.MOVIE)
        assertEquals(TsiptvCorner.MEDIUM, defaults.corner)
        assertTrue(defaults.showTitles)
        assertEquals(listOf(0, 4, 12, 20), TsiptvCorner.entries.map { it.dp })
    }

    @Test
    fun defaultLayout() {
        val full = TsiptvDefaultLayout.build(
            TsiptvPoolSummary(hasMovies = true, hasMovieWithBackdrop = true, hasSeries = true, hasRadio = true, hasChannels = true)
        ).home
        assertEquals(
            listOf(
                TsiptvSectionType.ROW to TsiptvQuerySource.CONTINUE_WATCHING,
                TsiptvSectionType.HERO to TsiptvQuerySource.MOVIES,
                TsiptvSectionType.ROW to TsiptvQuerySource.MOVIES,
                TsiptvSectionType.ROW to TsiptvQuerySource.SERIES,
                TsiptvSectionType.ROW to TsiptvQuerySource.RADIO,
                TsiptvSectionType.GRID to TsiptvQuerySource.CHANNELS,
            ),
            full.map { it.type to it.query?.from }
        )
        assertEquals(5, full[1].effectiveLimit)
        val grid = full.last()
        assertEquals(TsiptvQuerySort.NUMBER, grid.query?.sort)
        assertTrue(grid.groupChips)
        assertEquals(TsiptvBuiltInTitle.CHANNELS, grid.builtInTitle)
        assertTrue(full.all { it.title == null })

        val channelsOnly = TsiptvDefaultLayout.build(
            TsiptvPoolSummary(hasMovies = true, hasMovieWithBackdrop = false, hasSeries = false, hasRadio = false, hasChannels = true)
        ).home
        assertEquals(
            listOf(TsiptvBuiltInTitle.CONTINUE_WATCHING, TsiptvBuiltInTitle.MOVIES, TsiptvBuiltInTitle.CHANNELS),
            channelsOnly.map { it.builtInTitle }
        )
    }
}
