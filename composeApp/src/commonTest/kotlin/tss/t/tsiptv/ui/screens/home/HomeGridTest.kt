package tss.t.tsiptv.ui.screens.home

import androidx.compose.ui.unit.dp
import tss.t.tsiptv.core.ads.AdsPolicy
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.HomeGridRow
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.homeGridColumns
import tss.t.tsiptv.ui.screens.home.homeiptvlist.widgets.homeGridRows
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeGridTest {
    private fun item(i: Int) = AdsPolicy.Row.Item(i, Channel(id = "c$i", name = "C$i", url = "u$i", playlistId = "p"))

    private fun shape(lines: List<HomeGridRow>) = lines.map {
        when (it) {
            is HomeGridRow.Channels -> it.items.joinToString(",") { row -> row.index.toString() }
            is HomeGridRow.Ad -> "ad${it.ad.slot}"
        }
    }

    @Test
    fun channelsFillLinesOfTheColumnCount() {
        val rows = (0 until 7).map(::item)
        assertEquals(listOf("0,1,2", "3,4,5", "6"), shape(homeGridRows(rows, 3)))
        assertEquals(listOf("0,1", "2,3", "4,5", "6"), shape(homeGridRows(rows, 2)))
    }

    @Test
    fun anAdKeepsItsOwnLine() {
        val rows = listOf(item(0), item(1), item(2), AdsPolicy.Row.Ad(slot = 0, beforeIndex = 3), item(3), item(4))
        assertEquals(listOf("0,1", "2", "ad0", "3,4"), shape(homeGridRows(rows, 2)))
    }

    @Test
    fun lineKeysKeepTheSingleColumnPrefixes() {
        val rows = listOf(item(0), item(1), AdsPolicy.Row.Ad(slot = 0, beforeIndex = 2))
        val keys = homeGridRows(rows, 3).map { it.key }
        assertEquals(listOf("ch:0:c0", "ad:0"), keys)
    }

    @Test
    fun oneColumnOnPhonesAndTwoToThreeOnDesktop() {
        assertEquals(1, homeGridColumns(1400.dp, isDesktop = false))
        assertEquals(1, homeGridColumns(500.dp, isDesktop = true))
        assertEquals(2, homeGridColumns(800.dp, isDesktop = true))
        assertEquals(3, homeGridColumns(1280.dp, isDesktop = true))
    }
}
