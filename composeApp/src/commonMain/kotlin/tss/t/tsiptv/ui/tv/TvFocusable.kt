package tss.t.tsiptv.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.ui.themes.TSColors

object TvDefaults {
    val cardShape: Shape = RoundedCornerShape(12.dp)
    val focusBorderColor = TSColors.AccentCyan
    val surfaceColor = TSColors.SecondaryBackgroundColor
    val focusedSurfaceColor = Color(0xFF232A4D)

    /** 5% overscan-safe margin recommended for TV layouts (48dp horizontal, 27dp vertical). */
    val overscanHorizontal = 48.dp
    val overscanVertical = 27.dp
}

/**
 * A D-pad friendly surface: focusable, clickable with OK / Enter, and visibly
 * highlighted (scale + accent border) while it holds focus, since a TV has no
 * touch feedback to tell the user where they are.
 */
/** F3: the focus ring colour; a TS IPTV Source's screens use its accent. */
val LocalFocusRingColor = androidx.compose.runtime.compositionLocalOf { TvDefaults.focusBorderColor }

@Composable
fun TvFocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TvDefaults.cardShape,
    focusedScale: Float = 1.05f,
    color: Color = TvDefaults.surfaceColor,
    focusedColor: Color = TvDefaults.focusedSurfaceColor,
    selected: Boolean = false,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f)
    val background by animateColorAsState(
        when {
            focused -> focusedColor
            selected -> focusedColor.copy(alpha = 0.6f)
            else -> color
        }
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background, shape)
            .border(
                width = 2.dp,
                color = if (focused) LocalFocusRingColor.current else Color.Transparent,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        content = { content(focused) }
    )
}

/**
 * Request focus once the target has been laid out. Initial-focus effects run
 * right after composition, before a lazy list has composed its rows, so a bare
 * requestFocus() there silently does nothing. Returns whether focus was taken.
 */
suspend fun FocusRequester.requestFocusAfterLayout(): Boolean {
    withFrameNanos { }
    return runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)
}

/**
 * Accent border while this element (or a child) holds focus, in TV mode only.
 * For shared phone widgets that must show D-pad focus on a TV without changing
 * how they look on a touch screen. Place it before the focusable modifier.
 */
@Composable
fun Modifier.tvFocusBorder(shape: Shape): Modifier {
    if (!LocalIsTvMode.current) return this
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .border(
            width = 2.dp,
            color = if (focused) TvDefaults.focusBorderColor else Color.Transparent,
            shape = shape
        )
}

/** A full-width row for menus and side rails. */
@Composable
fun TvMenuItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    selected: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
        color = Color.Transparent,
        selected = selected,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (focused || selected) TSColors.AccentCyan else TSColors.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (focused || selected) TSColors.TextPrimary else TSColors.TextSecondaryLight,
                    fontSize = 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (description != null) {
                    Text(
                        text = description,
                        color = TSColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing()
        }
    }
}
