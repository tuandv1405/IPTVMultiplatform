package tss.t.tsiptv.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Default shadow color - semi-transparent black
val ShadowColor = Color(0x55000000)
//
///**
// * Applies a custom drop shadow to the element with specific position, blur, and color
// *
// * @param elevation The size of the shadow (blur radius)
// * @param offsetX The horizontal offset of the shadow
// * @param offsetY The vertical offset of the shadow
// * @param shape The shape of the shadow
// * @param color The color of the shadow
// */
//fun Modifier.customShadow(
//    elevation: Dp = 10.dp,
//    offsetX: Dp = 0.dp,
//    offsetY: Dp = 0.dp,
//    shape: Shape = RoundedCornerShape(0.dp),
//    color: Color = ShadowColor
//) = this.graphicsLayer {
//    shadowElevation = elevation.toPx()
//    translationX = offsetX.toPx()
//    translationY = offsetY.toPx()
//    this.shape = shape
//    clip = false
//}.shadow(
//    elevation = elevation,
//    shape = shape,
//    ambientColor = color,
//    spotColor = color
//)

/**
 * Simplified shadow function that applies a standard shadow with configurable elevation
 */
fun Modifier.shadow(elevation: Int = 10) = this.shadow(
    elevation = elevation.dp,
    ambientColor = ShadowColor,
    spotColor = ShadowColor
)

/**
 * Applies an inner shadow effect to the element
 * 
 * @param color The color of the inner shadow
 * @param spread How far the shadow spreads inward
 * @param blur How blurry the shadow appears (simulated with gradient)
 * @param offsetX Horizontal offset of the shadow
 * @param offsetY Vertical offset of the shadow
 * @param shape The shape of the element
 */
fun Modifier.innerShadow(
    color: Color = ShadowColor,
    spread: Dp = 0.dp,
    blur: Dp = 8.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    shape: Shape = RoundedCornerShape(0.dp)
) = this
    .clip(shape)
    .border(
        width = blur,
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(
                x = offsetX.value / 2 + 0.5f,
                y = offsetY.value / 2 + 0.5f
            ),
            radius = blur.value * 2
        ),
        shape = shape
    )
    .padding(blur - spread)
