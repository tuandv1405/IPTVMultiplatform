package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A [LazyRow] that a mouse can scroll too (see [mouseHorizontalScroll]). Use it for every
 * horizontal list; a plain LazyRow cannot be scrolled on desktop with a wheel-only mouse.
 */
@Composable
fun TSLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = modifier.mouseHorizontalScroll(state),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/**
 * Desktop: the vertical mouse wheel scrolls this horizontal list while it can still move that
 * way (then the page scrolls as before), and the list can be dragged with the mouse. Compose only
 * scrolls horizontal lists with Shift + wheel or a touchpad, which most people never find.
 * Touch platforms: no change.
 */
expect fun Modifier.mouseHorizontalScroll(state: ScrollableState): Modifier
