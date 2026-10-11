package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Pixels per wheel notch, about what Compose scrolls a vertical list by. */
private val WheelStep = 64.dp

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.mouseHorizontalScroll(state: ScrollableState): Modifier = pointerInput(state) {
    val step = WheelStep.toPx()
    val slop = viewConfiguration.touchSlop
    awaitPointerEventScope {
        var tracking = false
        var dragging = false
        var pressX = 0f
        var lastX = 0f
        while (true) {
            // Main pass: a chip or card under the pointer sees the press first and still gets
            // its click; once this drag consumes a move, that click is cancelled.
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            when (event.type) {
                PointerEventType.Scroll -> {
                    val delta = change.scrollDelta
                    // Shift + wheel and touchpads already give x; Compose handles those.
                    if (abs(delta.y) <= abs(delta.x) || change.isConsumed) continue
                    val canMove = if (delta.y > 0) state.canScrollForward else state.canScrollBackward
                    if (canMove) {
                        state.dispatchRawDelta(delta.y * step)
                        change.consume()
                    }
                }

                PointerEventType.Press -> {
                    tracking = change.type == PointerType.Mouse && event.buttons.isPrimaryPressed
                    dragging = false
                    pressX = change.position.x
                    lastX = pressX
                }

                PointerEventType.Move -> if (tracking) {
                    if (!dragging && abs(change.position.x - pressX) > slop) dragging = true
                    if (dragging) {
                        state.dispatchRawDelta(lastX - change.position.x)
                        change.consume()
                    }
                    lastX = change.position.x
                }

                PointerEventType.Release -> {
                    if (dragging) change.consume()
                    tracking = false
                    dragging = false
                }
            }
        }
    }
}
