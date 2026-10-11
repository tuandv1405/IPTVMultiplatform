package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A horizontal list under a vertical page, driven with a mouse: the wheel scrolls the row until
 * its end and then the page, a drag scrolls the row without clicking, a click still clicks.
 */
@OptIn(ExperimentalComposeUiApi::class)
class TSLazyRowMouseTest {

    private val rowState = LazyListState()
    private val pageState = LazyListState()
    private val clicks = mutableListOf<Int>()

    // 400 x 300 px; the row is the first 100 px of the page, 20 items of 100 px.
    private val scene = ImageComposeScene(width = 400, height = 300, density = Density(1f)) {
        LazyColumn(state = pageState) {
            item {
                TSLazyRow(modifier = Modifier.height(100.dp), state = rowState) {
                    items(20) { index ->
                        Box(Modifier.width(100.dp).height(100.dp).clickable { clicks += index })
                    }
                }
            }
            items(20) { Box(Modifier.height(100.dp).width(400.dp)) }
        }
    }

    private val onRow = Offset(150f, 50f)

    @AfterTest
    fun close() = scene.close()

    private fun frame() = scene.render()

    private fun wheel(notches: Float, at: Offset = onRow) {
        scene.sendPointerEvent(PointerEventType.Scroll, at, scrollDelta = Offset(0f, notches), type = PointerType.Mouse)
        frame()
    }

    private fun mouse(type: PointerEventType, at: Offset, pressed: Boolean) {
        scene.sendPointerEvent(
            type, at, type = PointerType.Mouse,
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
        )
        frame()
    }

    @Test
    fun wheelScrollsTheRowThenThePage() {
        frame()
        wheel(3f)
        assertTrue(rowState.firstVisibleItemIndex > 0, "row moved")
        assertEquals(0, pageState.firstVisibleItemScrollOffset, "page did not move")

        wheel(-30f)
        assertEquals(0, rowState.firstVisibleItemIndex)
        assertEquals(0, rowState.firstVisibleItemScrollOffset)

        // At the row's end, the wheel goes on to the page as before.
        wheel(100f)
        val rowEnd = rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset
        assertTrue(!rowState.canScrollForward, "row at its end")
        val pageBefore = pageState.firstVisibleItemIndex to pageState.firstVisibleItemScrollOffset
        wheel(1f)
        assertEquals(rowEnd, rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset)
        assertTrue(
            (pageState.firstVisibleItemIndex to pageState.firstVisibleItemScrollOffset) != pageBefore,
            "page scrolled"
        )
    }

    @Test
    fun dragScrollsWithoutClickingAndClickStillClicks() {
        frame()
        mouse(PointerEventType.Press, Offset(350f, 50f), pressed = true)
        for (x in 330 downTo 50 step 20) mouse(PointerEventType.Move, Offset(x.toFloat(), 50f), pressed = true)
        mouse(PointerEventType.Release, Offset(50f, 50f), pressed = false)
        assertEquals(300, rowState.firstVisibleItemIndex * 100 + rowState.firstVisibleItemScrollOffset)
        assertEquals(emptyList(), clicks)

        mouse(PointerEventType.Press, Offset(150f, 50f), pressed = true)
        mouse(PointerEventType.Release, Offset(150f, 50f), pressed = false)
        assertEquals(listOf(4), clicks)
    }
}
